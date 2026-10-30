package com.aiplayground.app

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AgentApi {
    // Replace this with the HTTPS URL Render gives you after deploying render.yaml.
    const val BASE_URL = "https://YOUR-RENDER-SERVICE.onrender.com"

    fun sendMessage(
        agent: String,
        message: String,
        history: List<String>,\n        accessCode: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        Thread {
            var connection: HttpURLConnection? = null
            try {
                if (BASE_URL.contains("YOUR-RENDER-SERVICE")) {
                    throw IllegalStateException("The secure AI backend has not been deployed yet.")
                }
                connection = (URL("$BASE_URL/api/agent/chat").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 30000
                    readTimeout = 120000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")\n                    setRequestProperty("Authorization", "Bearer $accessCode")
                }
                val body = JSONObject()
                    .put("agent", agent)
                    .put("message", message)
                    .put("history", JSONArray(history.takeLast(12)))
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val json = JSONObject(responseText.ifBlank { "{}" })
                if (status !in 200..299) {
                    throw IllegalStateException(json.optString("error", "The AI service returned HTTP $status."))
                }
                val reply = json.optString("reply")
                if (reply.isBlank()) throw IllegalStateException("The AI service returned an empty reply.")
                Handler(Looper.getMainLooper()).post { onSuccess(reply) }
            } catch (error: Exception) {
                val messageText = error.message ?: "Unable to contact the AI service."
                Handler(Looper.getMainLooper()).post { onError(messageText) }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }
}
