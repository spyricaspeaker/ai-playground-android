package com.aiplayground.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class BrainSignal(val label: String, val symbol: String, val meaning: String) {
    YES("YES", "✓", "Confirm / continue"),
    NO("NO", "✕", "Reject / stop"),
    HELP("HELP", "?", "Ask for assistance"),
    REPEAT("REPEAT", "↻", "Repeat the last response"),
    FOCUS("FOCUS", "◎", "Focus on the selected agent")
}

data class AgentProfile(val name: String, val role: String, val description: String)

object AgentDirectory {
    val agents = listOf(
        AgentProfile("ARIA", "Companion", "Conversation, creative thinking and emotional presence."),
        AgentProfile("NOVA", "Researcher", "Exploration, explanations and information synthesis."),
        AgentProfile("ATLAS", "Builder", "Technical planning, systems and implementation."),
        AgentProfile("ECHO", "Communicator", "Clear communication, summaries and accessible interaction.")
    )
}

data class InterpretedIntent(val title: String, val detail: String)

object NeuralInterpreter {
    fun interpret(signal: BrainSignal, agent: AgentProfile, endpoint: String): InterpretedIntent {
        if (endpoint.isNotBlank()) {
            try {
                val connection = (URL(endpoint.trim()).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 5000
                    readTimeout = 8000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                val body = JSONObject().put("signal", signal.name)
                    .put("meaning", signal.meaning).put("agent", agent.name)
                    .put("agentRole", agent.role).toString()
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                val json = JSONObject(response)
                return InterpretedIntent(json.optString("title", "Intent interpreted"),
                    json.optString("response", json.optString("detail", "The AI service received the signal.")))
            } catch (_: Exception) {
                // Local fallback keeps the prototype usable when no model service is reachable.
            }
        }
        return when (signal) {
            BrainSignal.YES -> InterpretedIntent("Confirmation", "The selected intent is to confirm and continue.")
            BrainSignal.NO -> InterpretedIntent("Rejection / stop", "The selected intent is to decline or stop the current action.")
            BrainSignal.HELP -> InterpretedIntent("Request for assistance", "The selected intent is to ask ${agent.name} for help.")
            BrainSignal.REPEAT -> InterpretedIntent("Repeat request", "The selected intent is to repeat or clarify the previous response.")
            BrainSignal.FOCUS -> InterpretedIntent("Attention selection", "The selected intent is to focus on ${agent.name}, your ${agent.role.lowercase()}.")
        }
    }
}

object AgentEngine {
    fun respond(agent: AgentProfile, signal: BrainSignal, intent: InterpretedIntent): String {
        val opening = when (agent.name) {
            "ARIA" -> "I'm here with you."
            "NOVA" -> "Research view:"
            "ATLAS" -> "Systems view:"
            else -> "Communication view:"
        }
        val response = when (signal) {
            BrainSignal.YES -> "I understand that as a yes. I'll continue only with the current, clearly authorized step."
            BrainSignal.NO -> "Understood as no. I will not proceed with the proposed action."
            BrainSignal.HELP -> when (agent.name) {
                "NOVA" -> "I can break the subject into facts, uncertainties and useful sources."
                "ATLAS" -> "I can help plan the next technical step and explain what it changes."
                "ECHO" -> "I can restate, simplify or prepare a message for you."
                else -> "Tell me what you need, one small step at a time. You can use the signal controls again."
            }
            BrainSignal.REPEAT -> "I'll restate the last point more clearly. This prototype recognizes trained demo signals, not unrestricted thoughts."
            BrainSignal.FOCUS -> "Focus is now assigned to me. Choose HELP, YES, NO or REPEAT to communicate an intention."
        }
        return "$opening $response\n\nIntent: ${intent.title}. ${intent.detail}"
    }
}
