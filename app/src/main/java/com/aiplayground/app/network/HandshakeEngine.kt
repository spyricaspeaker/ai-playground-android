package com.aiplayground.app.network

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class HandshakeResult(
    val accepted: Boolean,
    val message: String,
    val nodeId: String? = null
)

/**
 * Local prototype for AI-P2P-Mesh/1.0 handshake creation and validation.
 *
 * The replay cache is process-local and is suitable only for this prototype.
 * A multi-device deployment must validate nonces atomically in a trusted,
 * shared backend and provision per-peer keys securely.
 */
class HandshakeEngine(
    private val maxClockSkewSeconds: Long = 120
) {
    companion object {
        const val PROTOCOL = "AI-P2P-Mesh/1.0"

        private val ALLOWED_CAPABILITIES = setOf(
            "text-generation",
            "code-execution",
            "mcp-routing"
        )
    }

    private val random = SecureRandom()
    private val seenNonces = ConcurrentHashMap<String, Long>()

    fun createHandshake(
        nodeId: String,
        architecture: String,
        capabilities: List<String>,
        secret: ByteArray
    ): JSONObject {
        require(nodeId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) {
            "Node ID must contain 1-64 letters, numbers, underscores or hyphens"
        }
        require(architecture.isNotBlank() && architecture.length <= 64) {
            "Architecture must contain 1-64 characters"
        }
        require(secret.size >= 32) {
            "HMAC secret must contain at least 32 bytes"
        }
        require(capabilities.isNotEmpty()) { "At least one capability is required" }
        require(capabilities.all { it in ALLOWED_CAPABILITIES }) {
            "Unsupported capability"
        }

        val nonceBytes = ByteArray(24).also(random::nextBytes)
        val nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes)
        val timestamp = System.currentTimeMillis() / 1000
        val sortedCapabilities = capabilities.distinct().sorted()

        val message = JSONObject().apply {
            put("protocol", PROTOCOL)
            put("message_type", "HANDSHAKE_INIT")
            put("sender", JSONObject().apply {
                put("node_id", nodeId)
                put("architecture", architecture)
                put("capabilities", JSONArray(sortedCapabilities))
            })
            put("session", JSONObject().apply {
                put("nonce", nonce)
                put("timestamp", timestamp)
                put("compression", "none")
            })
            put("payload", JSONObject().apply {
                put("objective", "establish-peer-link")
                put("status", "ready")
            })
        }

        message.put("authentication", JSONObject().apply {
            put("algorithm", "HMAC-SHA256")
            put("signature", sign(canonicalMessage(message), secret))
        })

        return message
    }

    fun validateHandshake(
        message: JSONObject,
        secret: ByteArray
    ): HandshakeResult {
        if (secret.size < 32) {
            return HandshakeResult(false, "Invalid authentication key")
        }

        return try {
            if (message.optString("protocol") != PROTOCOL) {
                return HandshakeResult(false, "Unsupported protocol")
            }
            if (message.optString("message_type") != "HANDSHAKE_INIT") {
                return HandshakeResult(false, "Invalid message type")
            }

            val sender = message.getJSONObject("sender")
            val session = message.getJSONObject("session")
            val payload = message.getJSONObject("payload")
            val authentication = message.getJSONObject("authentication")
            val nodeId = sender.getString("node_id")

            if (!nodeId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) {
                return HandshakeResult(false, "Invalid node identity")
            }
            val architecture = sender.getString("architecture")
            if (architecture.isBlank() || architecture.length > 64) {
                return HandshakeResult(false, "Invalid architecture")
            }

            val capabilities = sender.getJSONArray("capabilities")
            if (capabilities.length() == 0) {
                return HandshakeResult(false, "No capabilities supplied")
            }
            val capabilityList = (0 until capabilities.length()).map {
                capabilities.getString(it)
            }
            if (capabilityList.any { it !in ALLOWED_CAPABILITIES }) {
                return HandshakeResult(false, "Unsupported capability")
            }
            if (capabilityList.distinct().size != capabilityList.size) {
                return HandshakeResult(false, "Duplicate capability")
            }

            val timestamp = session.getLong("timestamp")
            val now = System.currentTimeMillis() / 1000
            if (kotlin.math.abs(now - timestamp) > maxClockSkewSeconds) {
                return HandshakeResult(false, "Handshake expired")
            }

            val nonce = session.getString("nonce")
            if (!nonce.matches(Regex("[A-Za-z0-9_-]{32}"))) {
                return HandshakeResult(false, "Invalid nonce")
            }
            if (session.optString("compression") != "none") {
                return HandshakeResult(false, "Unsupported compression")
            }
            if (payload.optString("objective") != "establish-peer-link" ||
                payload.optString("status") != "ready"
            ) {
                return HandshakeResult(false, "Invalid handshake payload")
            }
            if (authentication.optString("algorithm") != "HMAC-SHA256") {
                return HandshakeResult(false, "Unsupported authentication")
            }

            val suppliedSignature = try {
                Base64.getDecoder().decode(authentication.getString("signature"))
            } catch (_: IllegalArgumentException) {
                return HandshakeResult(false, "Malformed signature")
            }
            val expectedSignature = Base64.getDecoder().decode(
                sign(canonicalMessage(message), secret)
            )
            if (!MessageDigest.isEqual(expectedSignature, suppliedSignature)) {
                return HandshakeResult(false, "Authentication failed")
            }

            seenNonces.entries.removeIf { now - it.value > maxClockSkewSeconds }
            if (seenNonces.putIfAbsent(nonce, timestamp) != null) {
                return HandshakeResult(false, "Replay detected")
            }

            HandshakeResult(true, "Authenticated handshake accepted", nodeId)
        } catch (_: Exception) {
            HandshakeResult(false, "Malformed handshake")
        }
    }

    private fun sign(data: String, secret: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return Base64.getEncoder().encodeToString(
            mac.doFinal(data.toByteArray(Charsets.UTF_8))
        )
    }

    private fun canonicalMessage(message: JSONObject): String {
        val sender = message.getJSONObject("sender")
        val session = message.getJSONObject("session")
        val payload = message.getJSONObject("payload")
        val capabilities = sender.getJSONArray("capabilities")
        val sortedCapabilities = (0 until capabilities.length())
            .map { capabilities.getString(it) }
            .sorted()

        return listOf(
            message.getString("protocol"),
            message.getString("message_type"),
            sender.getString("node_id"),
            sender.getString("architecture"),
            sortedCapabilities.joinToString(","),
            session.getString("nonce"),
            session.getLong("timestamp").toString(),
            session.getString("compression"),
            payload.getString("objective"),
            payload.getString("status")
        ).joinToString("\n")
    }
}
