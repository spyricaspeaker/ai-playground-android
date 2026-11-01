package com.aiplayground.app

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AgentApi {
    fun sendMessage(
        baseUrl: String,
        agent: String,
        message: String,
        history: List<String>,
        accessCode: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        Thread {
            var connection: HttpURLConnection? = null
            try {
                val root = baseUrl.trim().trimEnd('/')
                require(root.startsWith("https://")) { "Enter the backend HTTPS URL." }
                require(accessCode.isNotBlank()) { "Enter the backend app access code." }
                val route = if (agent == "TEAM") "team" else "chat"
                connection = (URL(root + "/api/agent/" + route).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 120000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer " + accessCode)
                }
                val payload = JSONObject()
                    .put("agent", agent)
                    .put("message", message)
                    .put("history", JSONArray(history.takeLast(12)))
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val json = JSONObject(body.ifBlank { "{}" })
                if (status !in 200..299) error(json.optString("error", "AI service returned HTTP " + status))
                val reply = json.optString("reply")
                if (reply.isBlank()) error("The AI service returned an empty reply.")
                Handler(Looper.getMainLooper()).post { onSuccess(reply) }
            } catch (e: Exception) {
                val messageText = e.message ?: "Unable to contact the AI service."
                Handler(Looper.getMainLooper()).post { onError(messageText) }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }
}
