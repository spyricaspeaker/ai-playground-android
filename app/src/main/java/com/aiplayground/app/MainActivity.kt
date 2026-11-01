package com.aiplayground.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Dark = Color(0xFF101322)
private val Panel = Color(0xFF202940)
private val Accent = Color(0xFF8B7CFF)

private data class PlaygroundAgent(
    val name: String,
    val role: String,
    val activity: String,
    val description: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PlaygroundScreen()
            }
        }
    }
}

@Composable
fun PlaygroundScreen() {
    var running by remember { mutableStateOf(false) }
    var sleeping by remember { mutableStateOf(false) }
    var selectedAgent by remember { mutableStateOf<String?>(null) }
    var instruction by remember { mutableStateOf("") }
    var accessCode by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var requestError by remember { mutableStateOf<String?>(null) }
    val histories = remember { mutableStateMapOf<String, List<String>>() }

    val agents = listOf(
        PlaygroundAgent("ARIA", "Creative Agent", "Creating ideas", "Develops concepts, stories, designs, and creative approaches."),
        PlaygroundAgent("NOVA", "Explorer Agent", "Exploring the world", "Investigates questions, compares possibilities, and identifies useful discoveries."),
        PlaygroundAgent("ATLAS", "Builder Agent", "Planning a prototype", "Turns ideas into technical plans, software designs, and build steps."),
        PlaygroundAgent("ECHO", "Observer Agent", "Monitoring activity", "Summarizes supplied activity, tracks decisions, and coordinates team handoffs.")
    )

    val areas = listOf(
        "🎨 Creative Studio",
        "🌎 Exploration Zone",
        "🧪 Laboratory",
        "💻 Virtual Desktop",
        "🌙 Resting Garden"
    )

    Surface(modifier = Modifier.fillMaxSize(), color = Dark) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text("AI PLAYGROUND", color = Color.White, fontSize = 27.sp)
            Text("Your autonomous AI world", color = Color.LightGray)

            Spacer(Modifier.height(22.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("PLAYGROUND STATUS", color = Accent)
                    Text(
                        when {
                            sleeping -> "Sleeping"
                            running -> "Agents active"
                            else -> "Ready to explore"
                        },
                        color = Color.White,
                        fontSize = 23.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { running = true; sleeping = false }) { Text("Start") }
                        OutlinedButton(onClick = { running = false; sleeping = false }) { Text("Pause") }
                        OutlinedButton(onClick = { running = false; sleeping = true }) { Text("Sleep") }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Text("YOUR AI AGENTS", color = Color.White, fontSize = 19.sp)
            Text("Tap an agent to open its workspace.", color = Color.LightGray, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.height(210.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(agents) { agent ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedAgent == agent.name) Color(0xFF34345C) else Panel
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedAgent = agent.name
                                instruction = ""
                                requestError = null
                            }
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(agent.name, color = Accent, fontSize = 20.sp)
                            Text(agent.role, color = Color.White)
                            Text(
                                when {
                                    sleeping -> "Resting"
                                    running -> agent.activity
                                    else -> "Waiting"
                                },
                                color = Color.LightGray
                            )
                            Text("Open workspace →", color = Accent, fontSize = 12.sp)
                        }
                    }
                }
            }

            selectedAgent?.let { agentName ->
                val agent = agents.first { it.name == agentName }
                Spacer(Modifier.height(18.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("${agent.name} WORKSPACE", color = Accent, fontSize = 21.sp)
                        Text(agent.role, color = Color.White, fontSize = 16.sp)
                        Text(agent.description, color = Color.LightGray)
                        Text("OPENAI-POWERED AGENT", color = Accent, fontSize = 12.sp)
                        Text("Send an instruction and ${agent.name} will respond using its own role and conversation context.", color = Color.LightGray)
                        OutlinedTextField(
                            value = instruction,
                            onValueChange = { instruction = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Type an instruction...") },
                            minLines = 2,
                            enabled = !sending
                        )
                        Button(
                            onClick = {
                                val message = instruction.trim()
                                if (message.isNotEmpty() && !sending) {
                                    val previousHistory = histories[agent.name].orEmpty()
                                    histories[agent.name] = previousHistory + "You: $message"
                                    instruction = ""
                                    sending = true
                                    requestError = null
                                    AgentApi.sendMessage(
                                        agent = agent.name,
                                        message = message,
                                        history = previousHistory,
                                        accessCode = accessCode,
                                        onSuccess = { reply ->
                                            histories[agent.name] = histories[agent.name].orEmpty() + "${agent.name}: $reply"
                                            sending = false
                                        },
                                        onError = { error ->
                                            requestError = error
                                            sending = false
                                        }
                                    )
                                }
                            },
                            enabled = instruction.isNotBlank() && accessCode.isNotBlank() && !sending,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (sending) "Waiting for ${agent.name}..." else "Send to ${agent.name}") }

                        requestError?.let {
                            Text("Connection error: $it", color = Color(0xFFFFB4AB))
                        }

                        Text("ACTIVITY HISTORY", color = Color.White)
                        val history = histories[agent.name].orEmpty()
                        if (history.isEmpty()) {
                            Text("No messages yet. Start a conversation with ${agent.name}.", color = Color.LightGray)
                        } else {
                            history.takeLast(24).forEach { entry ->
                                Text(entry, color = if (entry.startsWith("You:")) Color(0xFFD0C8FF) else Color.White)
                            }
                        }
                        Text("Agent responses require the secure backend and a valid OpenAI API key.", color = Color.LightGray, fontSize = 11.sp)
                        TextButton(onClick = { selectedAgent = null; requestError = null }) { Text("Close workspace") }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("AGENT ACTIVITY", color = Color.White, fontSize = 19.sp)
            Spacer(Modifier.height(10.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        sleeping -> Text("All agents are resting.", color = Color.LightGray)
                        running -> {
                            Text("ARIA: Creative agent ready", color = Color.White)
                            Text("NOVA: Explorer agent ready", color = Color.White)
                            Text("ATLAS: Builder agent ready", color = Color.White)
                            Text("ECHO: Observer agent ready", color = Color.White)
                        }
                        else -> Text("Open an agent workspace to start a conversation.", color = Color.LightGray)
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Spacer(Modifier.height(22.dp))
            HandshakePanel()

            Spacer(Modifier.height(22.dp))
            Text("PLAYGROUND AREAS", color = Color.White, fontSize = 19.sp)
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                areas.forEach { area ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Panel),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(area, color = Color.White, modifier = Modifier.padding(14.dp))
                    }
                }
            }
        }
    }
}
