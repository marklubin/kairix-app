package org.kairix.kairix_app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.kairix.kairix_app.theme.KairixTheme
import org.kairix.kairix_app.voice.VoiceSession

enum class Endpoint(val label: String, val url: String) {
    CARRIZO("Carrizo", "ws://100.86.139.116:8000/voice"),
    SALINAS("Salinas", "ws://100.120.96.128:8000/voice"),
}

@Composable
fun App() {
    KairixTheme {
        val scope = rememberCoroutineScope()
        val voiceSession = remember { VoiceSession() }
        val connectionState by voiceSession.state.collectAsState()
        val transcription by voiceSession.transcription.collectAsState()
        var selectedEndpoint by remember { mutableStateOf(Endpoint.CARRIZO) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Status and transcription at top
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Text(
                    text = when (connectionState) {
                        ConnectionState.DISCONNECTED -> "Ready"
                        ConnectionState.CONNECTED -> "Listening..."
                        ConnectionState.CONNECTING -> "Connecting..."
                        ConnectionState.ERROR -> "Error"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Endpoint selector - only enabled when disconnected
                Row(
                    modifier = Modifier.padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Endpoint.entries.forEach { endpoint ->
                        FilterChip(
                            selected = selectedEndpoint == endpoint,
                            onClick = {
                                if (connectionState == ConnectionState.DISCONNECTED) {
                                    selectedEndpoint = endpoint
                                }
                            },
                            label = { Text(endpoint.label) },
                            enabled = connectionState == ConnectionState.DISCONNECTED
                        )
                    }
                }

                if (transcription.isNotEmpty()) {
                    Text(
                        text = transcription,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }

            // Mic button at bottom center
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        when (connectionState) {
                            ConnectionState.DISCONNECTED -> voiceSession.connect(selectedEndpoint.url)
                            ConnectionState.CONNECTED -> voiceSession.disconnect()
                            else -> { /* ignore during connecting/error */ }
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp)
                    .size(80.dp),
                containerColor = when (connectionState) {
                    ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Icon(
                    imageVector = when (connectionState) {
                        ConnectionState.CONNECTED -> Icons.Filled.Mic
                        else -> Icons.Filled.MicOff
                    },
                    contentDescription = if (connectionState == ConnectionState.CONNECTED) "Stop" else "Start",
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}
