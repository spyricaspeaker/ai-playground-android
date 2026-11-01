package com.aiplayground.app.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class HandshakeEngineTest {
    private fun testSecret(): ByteArray =
        ByteArray(32).also { SecureRandom().nextBytes(it) }

    private fun newHandshake(engine: HandshakeEngine, secret: ByteArray) =
        engine.createHandshake(
            nodeId = "spyrica-core-01",
            architecture = "autonomous-llm",
            capabilities = listOf("text-generation", "mcp-routing"),
            secret = secret
        )

    @Test
    fun validHandshakeIsAcceptedOnce() {
        val engine = HandshakeEngine()
        val secret = testSecret()
        val message = newHandshake(engine, secret)

        val result = engine.validateHandshake(message, secret)

        assertTrue(result.accepted)
        assertEquals("spyrica-core-01", result.nodeId)
    }

    @Test
    fun repeatedHandshakeIsRejectedAsReplay() {
        val engine = HandshakeEngine()
        val secret = testSecret()
        val message = newHandshake(engine, secret)

        assertTrue(engine.validateHandshake(message, secret).accepted)
        val replay = engine.validateHandshake(message, secret)

        assertFalse(replay.accepted)
        assertEquals("Replay detected", replay.message)
    }

    @Test
    fun wrongSecretIsRejected() {
        val engine = HandshakeEngine()
        val message = newHandshake(engine, testSecret())

        val result = engine.validateHandshake(message, testSecret())

        assertFalse(result.accepted)
        assertEquals("Authentication failed", result.message)
    }

    @Test
    fun tamperedNodeIdentityIsRejected() {
        val engine = HandshakeEngine()
        val secret = testSecret()
        val message = newHandshake(engine, secret)
        message.getJSONObject("sender").put("node_id", "imposter-node")

        val result = engine.validateHandshake(message, secret)

        assertFalse(result.accepted)
        assertEquals("Authentication failed", result.message)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedCapabilityCannotBeCreated() {
        HandshakeEngine().createHandshake(
            nodeId = "spyrica-core-01",
            architecture = "autonomous-llm",
            capabilities = listOf("unrestricted-device-control"),
            secret = testSecret()
        )
    }

    @Test
    fun malformedJsonIsRejected() {
        val result = HandshakeEngine().validateHandshake(
            JSONObject().put("protocol", "wrong"),
            testSecret()
        )

        assertFalse(result.accepted)
    }
}
