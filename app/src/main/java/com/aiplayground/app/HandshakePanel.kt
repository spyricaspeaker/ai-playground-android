package com.aiplayground.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aiplayground.app.network.HandshakeEngine
import java.security.SecureRandom

private val HandshakePanelColor = Color(0xFF202940)
private val HandshakeAccent = Color(0xFF8B7CFF)

/** Runs a local self-test; it does not connect to another device or agent. */
@Composable
fun HandshakePanel() {
    val engine = remember { HandshakeEngine() }
    var status by remember { mutableStateOf("Not tested yet") }
    var handshakeJson by remember {
        mutableStateOf("Press Run local test to generate a handshake.")
    }
    var replayStatus by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = HandshakePanelColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "SPYRICA HANDSHAKE ENGINE",
                color = HandshakeAccent,
                style = MaterialTheme.typography.titleMedium
            )
            Text("AI-P2P-Mesh/1.0 · Local prototype", color = Color.LightGray)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val secret = ByteArray(32).also { SecureRandom().nextBytes(it) }
                    try {
                        val message = engine.createHandshake(
                            nodeId = "spyrica-core-01",
                            architecture = "autonomous-llm",
                            capabilities = listOf(
                                "text-generation",
                                "code-execution",
                                "mcp-routing"
                            ),
                            secret = secret
                        )
                        val first = engine.validateHandshake(message, secret)
                        val replay = engine.validateHandshake(message, secret)

                        status = if (first.accepted) {
                            "PASS · ${first.message} · ${first.nodeId}"
                        } else {
                            "FAIL · ${first.message}"
                        }
                        replayStatus = if (!replay.accepted &&
                            replay.message == "Replay detected"
                        ) {
                            "PASS · Repeated nonce rejected"
                        } else {
                            "CHECK · ${replay.message}"
                        }
                        handshakeJson = message.toString(2)
                    } catch (error: Exception) {
                        status = "ERROR · ${error.message ?: "Handshake test failed"}"
                        replayStatus = ""
                        handshakeJson = "No handshake generated."
                    }
                }) {
                    Text("Run local test")
                }
            }

            Text("Authentication: $status", color = Color.White)
            if (replayStatus.isNotBlank()) {
                Text("Replay protection: $replayStatus", color = Color.White)
            }

            Text("GENERATED HANDSHAKE JSON", color = HandshakeAccent)
            Text(
                handshakeJson,
                color = Color(0xFFE5E5E5),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .verticalScroll(rememberScrollState())
            )
            Text(
                "This test validates a message locally using a temporary key. " +
                    "It does not establish a network connection. Do not use this " +
                    "temporary-key demo as production peer authentication.",
                color = Color.LightGray,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
