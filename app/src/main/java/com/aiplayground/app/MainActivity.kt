package com.aiplayground.app

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.sin

private val Night = Color(0xFF0B1020)
private val Panel = Color(0xFF171F35)
private val Violet = Color(0xFF9B8CFF)
private val Cyan = Color(0xFF54D7E8)
private val Soft = Color(0xFFB8C2D9)

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { status -> if (status == TextToSpeech.SUCCESS) tts?.language = Locale.US }
        setContent { NeuraApp(onSpeak = { speak(it) }) }
    }
    private fun speak(text: String) { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "neura-response") }
    override fun onDestroy() { tts?.stop(); tts?.shutdown(); super.onDestroy() }
}

data class ChatLine(val speaker: String, val message: String, val isUser: Boolean = false)

@Composable
fun NeuraApp(onSpeak: (String) -> Unit) {
    var selectedAgent by remember { mutableStateOf(AgentDirectory.agents.first()) }
    var selectedSignal by remember { mutableStateOf<BrainSignal?>(null) }
    var lastResponse by remember { mutableStateOf("Choose a simulated signal to begin. These are demo inputs, not decoded thoughts.") }
    var endpoint by remember { mutableStateOf("") }
    var remoteMode by remember { mutableStateOf(false) }
    var consentGranted by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("SIMULATOR READY") }
    val history = remember { mutableStateListOf(ChatLine("NEURA", "NEURA BAI 0.1 online. Select an agent, then send a simulated signal.")) }

    MaterialTheme(colorScheme = darkColorScheme(primary = Violet, secondary = Cyan, background = Night, surface = Panel)) {
        Surface(Modifier.fillMaxSize(), color = Night) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 22.dp, bottom = 28.dp)
            ) {
                item {
                    Text("NEURA", color = Color.White, fontSize = 30.sp)
                    Text("BRAIN–ARTIFICIAL INTELLIGENCE INTERFACE  •  0.1", color = Cyan, fontSize = 11.sp)
                    Text("Simulated neural communication lab", color = Soft, fontSize = 14.sp)
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("SIGNAL MONITOR", color = Violet, fontSize = 12.sp)
                                    Text(status, color = Color.White, fontSize = 18.sp)
                                }
                                Text("● LIVE DEMO", color = Cyan, fontSize = 11.sp)
                            }
                            Spacer(Modifier.height(10.dp))
                            SignalWave()
                            Text(selectedSignal?.let { "Last signal: ${it.name}  •  ${it.meaning}" } ?: "Waiting for a simulated signal", color = Soft, fontSize = 12.sp)
                        }
                    }
                }
                item {
                    SectionTitle("1  •  SELECT AN AI AGENT")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AgentDirectory.agents.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { agent ->
                                    val active = selectedAgent.name == agent.name
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        onClick = { selectedAgent = agent; status = "${agent.name} SELECTED" },
                                        colors = CardDefaults.cardColors(containerColor = if (active) Color(0xFF302B58) else Panel),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text(agent.name, color = if (active) Cyan else Violet, fontSize = 20.sp)
                                            Text(agent.role, color = Color.White, fontSize = 13.sp)
                                            Text(agent.description, color = Soft, fontSize = 10.sp, lineHeight = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    SectionTitle("2  •  SIMULATE A BRAIN SIGNAL")
                    Text("Tap a signal to send an intentional demo event into the interpreter.", color = Soft, fontSize = 12.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BrainSignal.entries.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { signal ->
                                    Button(
                                        onClick = {
                                            selectedSignal = signal
                                            status = "SIGNAL RECEIVED: ${signal.name}"
                                            val intent = NeuralInterpreter.interpret(signal, selectedAgent, if (remoteMode) endpoint else "")
                                            val reply = AgentEngine.respond(selectedAgent, signal, intent)
                                            lastResponse = reply
                                            history.add(ChatLine("YOU • ${signal.name}", signal.meaning, true))
                                            history.add(ChatLine(selectedAgent.name, reply))
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = if (selectedSignal == signal) Color(0xFF51458D) else Panel),
                                        contentPadding = PaddingValues(vertical = 12.dp)
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(signal.symbol, fontSize = 21.sp)
                                            Text(signal.label, fontSize = 10.sp)
                                        }
                                    }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
                item {
                    SectionTitle("3  •  AI INTERPRETER")
                    Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                        Column(Modifier.padding(14.dp)) {
                            Text(selectedSignal?.let { "INTERPRETED: ${it.meaning}" } ?: "Awaiting input", color = Cyan)
                            Spacer(Modifier.height(6.dp))
                            Text(lastResponse, color = Color.White, fontSize = 13.sp, lineHeight = 19.sp)
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onSpeak(lastResponse) }) { Text("▶ Speak") }
                                OutlinedButton(onClick = {
                                    history.clear()
                                    history.add(ChatLine("NEURA", "Conversation cleared. Signal simulator is ready."))
                                    lastResponse = "Conversation cleared."
                                }) { Text("Clear") }
                            }
                        }
                    }
                }
                item {
                    SectionTitle("4  •  AI SERVICE CONNECTION")
                    Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Optional model gateway URL", color = Color.White)
                            OutlinedTextField(value = endpoint, onValueChange = { endpoint = it }, modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("https://your-server.example/v1/neura/interpret") }, singleLine = true)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = remoteMode, onCheckedChange = { remoteMode = it })
                                Text("Use remote interpreter when configured", color = Soft, fontSize = 12.sp)
                            }
                            Text(
                                "The app sends signal labels and selected-agent metadata to this endpoint. Use your own HTTPS backend; never put an OpenAI API key inside the APK. Expected JSON response: { \"title\": \"...\", \"response\": \"...\" }. If unavailable, local demo interpretation is used.",
                                color = Soft, fontSize = 11.sp, lineHeight = 15.sp
                            )
                        }
                    }
                }
                item {
                    SectionTitle("5  •  PERMISSION GATEWAY")
                    Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Human approval required for external actions", color = Color.White)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = consentGranted, onCheckedChange = { consentGranted = it })
                                Text("Allow future connected actions after explicit review", color = Soft, fontSize = 11.sp)
                            }
                            Text(
                                if (consentGranted) "Demo permission enabled. No external device or account actions are currently implemented."
                                else "Locked. Agents can converse, but cannot control devices or accounts.",
                                color = if (consentGranted) Cyan else Soft, fontSize = 11.sp
                            )
                        }
                    }
                }
                item { SectionTitle("6  •  CONVERSATION") }
                items(history) { line ->
                    Card(colors = CardDefaults.cardColors(containerColor = if (line.isUser) Color(0xFF242B42) else Panel)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(line.speaker, color = if (line.isUser) Cyan else Violet, fontSize = 11.sp)
                            Text(line.message, color = Color.White, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                }
                item {
                    Text("Prototype note: simulated signals are button-generated events. This app does not read EEG, diagnose conditions, or decode unrestricted thoughts.", color = Soft, fontSize = 10.sp, lineHeight = 14.sp)
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) { Text(text, color = Violet, fontSize = 14.sp) }

@Composable
fun SignalWave() {
    Canvas(Modifier.fillMaxWidth().height(58.dp).background(Color(0xFF0D1426), RoundedCornerShape(10.dp)).padding(5.dp)) {
        val mid = size.height / 2f
        val step = size.width / 180f
        var previous = Offset(0f, mid)
        for (i in 1..180) {
            val x = i * step
            val y = mid + sin(i * 0.18f) * 9f + sin(i * 0.047f) * 5f
            val next = Offset(x, y)
            drawLine(Cyan, previous, next, strokeWidth = 2f)
            previous = next
        }
    }
}
