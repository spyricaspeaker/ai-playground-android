package com.aiplayground.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private val Dark = Color(0xFF101322)
private val Panel = Color(0xFF202940)
private val Accent = Color(0xFF8B7CFF)

private data class PlaygroundAgent(val name: String, val role: String, val description: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PlaygroundScreen(
                    listen = { onResult, onError -> startVoiceRecognition(onResult, onError) }
                )
            }
        }
    }

    private fun startVoiceRecognition(onResult: (String) -> Unit, onError: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            onError("Speech recognition is unavailable on this device.")
            return
        }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onResults(results: Bundle?) {
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim().orEmpty()
                recognizer.destroy()
                if (spoken.isBlank()) onError("I didn't catch that. Tap Speak and try again.")
                else onResult(spoken)
            }
            override fun onError(error: Int) {
                recognizer.destroy()
                val reason = when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No clear speech was detected."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition needs a network connection."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognition is busy. Try again in a moment."
                    else -> "Speech recognition failed (code " + error + ")."
                }
                onError(reason)
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer.startListening(intent)
    }
}

@Composable
private fun PlaygroundScreen(
    listen: ((String) -> Unit, (String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var running by remember { mutableStateOf(true) }
    var selectedAgent by remember { mutableStateOf("TEAM") }
    var instruction by remember { mutableStateOf("") }
    var backendUrl by remember { mutableStateOf("") }
    var accessCode by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Tap Speak when you want to talk.") }
    var error by remember { mutableStateOf<String?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    val histories = remember { mutableStateMapOf<String, List<String>>() }
    val tts = remember(context) {
        TextToSpeech(context) { result -> ttsReady = result == TextToSpeech.SUCCESS }
    }
    DisposableEffect(tts) {
        onDispose { tts.stop(); tts.shutdown() }
    }

    val agents = listOf(
        PlaygroundAgent("ARIA", "Creative agent", "Ideas, writing, design, and creative problem-solving."),
        PlaygroundAgent("NOVA", "Explorer agent", "Research, explanations, comparisons, and discovery."),
        PlaygroundAgent("ATLAS", "Builder agent", "Technical plans, coding, architecture, and implementation."),
        PlaygroundAgent("ECHO", "Observer agent", "Summaries, decision tracking, and coordination.")
    )

    val send: (String) -> Unit = { raw ->
        val message = raw.trim()
        if (message.isNotEmpty() && !sending) {
            when {
                !running -> error = "Resume the agent network before sending a message."
                backendUrl.isBlank() -> error = "Enter your deployed backend HTTPS URL."
                accessCode.isBlank() -> error = "Enter the app access code configured on the backend."
                else -> {
                    val agentForRequest = selectedAgent
                    val oldHistory = histories[agentForRequest].orEmpty()
                    histories[agentForRequest] = oldHistory + "You: " + message
                    instruction = ""
                    sending = true
                    error = null
                    AgentApi.sendMessage(
                        backendUrl, agentForRequest, message, oldHistory, accessCode,
                        onSuccess = { reply ->
                            histories[agentForRequest] = histories[agentForRequest].orEmpty() + "Agent: " + reply
                            sending = false
                            if (ttsReady) tts.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "agent-reply")
                        },
                        onError = { problem -> error = problem; sending = false }
                    )
                }
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            listening = true
            status = "Listening..."
            listen(
                { words -> listening = false; status = "Heard: " + words; instruction = words; send(words) },
                { problem -> listening = false; status = problem }
            )
        } else status = "Microphone permission was not granted."
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Dark) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("AI PLAYGROUND", color = Color.White, fontSize = 27.sp)
            Text("Talk to your network of AI agents", color = Color.LightGray)

            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("NETWORK STATUS", color = Accent)
                    Text(if (running) "Ready for your message" else "Paused", color = Color.White, fontSize = 20.sp)
                    Text("Microphone activates only when you tap Speak.", color = Color.LightGray, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { running = true }) { Text("Resume") }
                        OutlinedButton(onClick = { running = false; listening = false }) { Text("Pause") }
                    }
                }
            }

            Text("SELECT AGENT", color = Color.White, fontSize = 18.sp)
            Card(
                colors = CardDefaults.cardColors(containerColor = if (selectedAgent == "TEAM") Color(0xFF34345C) else Panel),
                modifier = Modifier.fillMaxWidth().clickable { selectedAgent = "TEAM"; error = null }
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("ARIA + NOVA + ATLAS + ECHO", color = Accent, fontSize = 17.sp)
                    Text("All four agents contribute, then ECHO synthesizes their perspectives.", color = Color.White)
                }
            }
            agents.forEach { agent ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = if (selectedAgent == agent.name) Color(0xFF34345C) else Panel),
                    modifier = Modifier.fillMaxWidth().clickable { selectedAgent = agent.name; error = null }
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(agent.name + " • " + agent.role, color = Accent, fontSize = 16.sp)
                        Text(agent.description, color = Color.White, fontSize = 13.sp)
                    }
                }
            }

            Text("SERVER CONNECTION", color = Color.White, fontSize = 18.sp)
            OutlinedTextField(
                value = backendUrl, onValueChange = { backendUrl = it },
                modifier = Modifier.fillMaxWidth(), label = { Text("Backend HTTPS URL") },
                placeholder = { Text("https://your-service.onrender.com") }, singleLine = true
            )
            OutlinedTextField(
                value = accessCode, onValueChange = { accessCode = it },
                modifier = Modifier.fillMaxWidth(), label = { Text("App access code") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true
            )

            Text("YOUR MESSAGE", color = Color.White, fontSize = 18.sp)
            OutlinedTextField(
                value = instruction, onValueChange = { instruction = it },
                modifier = Modifier.fillMaxWidth(), placeholder = { Text("Speak or type a message...") },
                minLines = 2, enabled = !sending
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (!running) error = "Resume the network first."
                        else if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            listening = true
                            status = "Listening..."
                            listen(
                                { words -> listening = false; status = "Heard: " + words; instruction = words; send(words) },
                                { problem -> listening = false; status = problem }
                            )
                        } else permission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    enabled = running && !sending && !listening
                ) { Text(if (listening) "Listening..." else "🎙 Speak") }
                Button(onClick = { send(instruction) }, enabled = running && !sending && instruction.isNotBlank()) {
                    Text(if (sending) "Thinking..." else "Send")
                }
            }
            Text(status, color = Color.LightGray, fontSize = 12.sp)
            error?.let { Text(it, color = Color(0xFFFFB4AB)) }

            Text("CONVERSATION", color = Color.White, fontSize = 18.sp)
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val history = histories[selectedAgent].orEmpty()
                    if (history.isEmpty()) Text("Your conversation with " + selectedAgent + " appears here.", color = Color.LightGray)
                    else history.takeLast(30).forEach { entry ->
                        Text(entry, color = if (entry.startsWith("You:")) Color(0xFFD0C8FF) else Color.White)
                    }
                }
            }
            TextButton(onClick = {
                val lastReply = histories[selectedAgent].orEmpty().lastOrNull { it.startsWith("Agent: ") }
                if (lastReply != null && ttsReady) tts.speak(lastReply.removePrefix("Agent: "), TextToSpeech.QUEUE_FLUSH, null, "replay")
                else error = "No agent reply is available to replay yet."
            }) { Text("🔊 Replay latest reply") }
        }
    }
}
