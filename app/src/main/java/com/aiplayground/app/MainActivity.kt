package com.aiplayground.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

    val agents = listOf(
        Triple("ARIA", "Creative Agent", "Creating ideas"),
        Triple("NOVA", "Explorer Agent", "Exploring the world"),
        Triple("ATLAS", "Builder Agent", "Planning a prototype"),
        Triple("ECHO", "Observer Agent", "Monitoring activity")
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

            Card(
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(18.dp)
            ) {
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
                        Button(onClick = {
                            running = true
                            sleeping = false
                        }) { Text("Start") }

                        OutlinedButton(onClick = {
                            running = false
                            sleeping = false
                        }) { Text("Pause") }

                        OutlinedButton(onClick = {
                            running = false
                            sleeping = true
                        }) { Text("Sleep") }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Text("YOUR AI AGENTS", color = Color.White, fontSize = 19.sp)
            Spacer(Modifier.height(10.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.height(190.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(agents) { agent ->
                    Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(agent.first, color = Accent, fontSize = 20.sp)
                            Text(agent.second, color = Color.White)
                            Text(
                                when {
                                    sleeping -> "Resting"
                                    running -> agent.third
                                    else -> "Waiting"
                                },
                                color = Color.LightGray
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("AGENT ACTIVITY", color = Color.White, fontSize = 19.sp)
            Spacer(Modifier.height(10.dp))

            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        running -> {
                            Text("ARIA: Creating new ideas", color = Color.White)
                            Text("NOVA: Exploring available spaces", color = Color.White)
                            Text("ATLAS: Planning a prototype", color = Color.White)
                            Text("ECHO: Monitoring the playground", color = Color.White)
                        }
                        sleeping -> Text("All agents are resting.", color = Color.LightGray)
                        else -> Text("Press Start to begin simulated agent activities.", color = Color.LightGray)
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
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
