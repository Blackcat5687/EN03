package com.example.geminivoicechat

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch

private enum class ConnectionState { IDLE, CONNECTING, CONNECTED, ERROR }

@Composable
fun ChatScreen(
    settingsStore: SettingsStore,
    onOpenSettings: () -> Unit,
    onOpenApiKey: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var connectionState by remember { mutableStateOf(ConnectionState.IDLE) }
    var isMicActive by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Tap the mic to start talking") }
    var transcript by remember { mutableStateOf(listOf<String>()) }
    var hasApiKey by remember { mutableStateOf(true) }

    var client by remember { mutableStateOf<GeminiLiveClient?>(null) }
    var audioEngine by remember { mutableStateOf<AudioEngine?>(null) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch {
                startSession(
                    context = context,
                    settingsStore = settingsStore,
                    onClientReady = { client = it },
                    onAudioEngineReady = { audioEngine = it },
                    onStateChange = { connectionState = it },
                    onStatus = { statusMessage = it },
                    onTranscript = { line -> transcript = transcript + line },
                    onMicActiveChange = { isMicActive = it },
                    onMissingKey = { hasApiKey = false }
                )
            }
        } else {
            statusMessage = "Microphone permission is required to talk to the assistant."
        }
    }

    // Clean up the connection and audio hardware when leaving this screen.
    DisposableEffect(Unit) {
        onDispose {
            audioEngine?.release()
            client?.close()
        }
    }

    LaunchedEffect(Unit) {
        hasApiKey = settingsStore.getApiKey().isNotBlank()
        if (!hasApiKey) {
            onOpenApiKey()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Gemini Voice Chat", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings")
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            transcript.forEach { line ->
                Text(
                    text = line,
                    modifier = Modifier.padding(vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = statusMessage, style = MaterialTheme.typography.bodySmall)

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier.size(88.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = {
                        if (!hasApiKey) {
                            onOpenApiKey()
                            return@Button
                        }

                        if (isMicActive) {
                            // Stop current session
                            audioEngine?.release()
                            client?.close()
                            audioEngine = null
                            client = null
                            isMicActive = false
                            connectionState = ConnectionState.IDLE
                            statusMessage = "Tap the mic to start talking"
                        } else {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasPermission) {
                                scope.launch {
                                    startSession(
                                        context = context,
                                        settingsStore = settingsStore,
                                        onClientReady = { client = it },
                                        onAudioEngineReady = { audioEngine = it },
                                        onStateChange = { connectionState = it },
                                        onStatus = { statusMessage = it },
                                        onTranscript = { line -> transcript = transcript + line },
                                        onMicActiveChange = { isMicActive = it },
                                        onMissingKey = { hasApiKey = false }
                                    )
                                }
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    modifier = Modifier.size(72.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = if (isMicActive) Icons.Filled.MicOff else Icons.Filled.Mic,
                        contentDescription = if (isMicActive) "Stop" else "Start talking",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

/**
 * Wires together the SettingsStore, GeminiLiveClient, and AudioEngine to start
 * one live voice session: connect the socket, then start streaming mic audio
 * once the connection is open.
 */
private suspend fun startSession(
    context: android.content.Context,
    settingsStore: SettingsStore,
    onClientReady: (GeminiLiveClient) -> Unit,
    onAudioEngineReady: (AudioEngine) -> Unit,
    onStateChange: (ConnectionState) -> Unit,
    onStatus: (String) -> Unit,
    onTranscript: (String) -> Unit,
    onMicActiveChange: (Boolean) -> Unit,
    onMissingKey: () -> Unit
) {
    val apiKey = settingsStore.getApiKey()
    if (apiKey.isBlank()) {
        onMissingKey()
        return
    }

    val voice = settingsStore.getVoiceName()
    val language = settingsStore.getLanguageCode()
    val systemPrompt = settingsStore.getSystemPrompt()

    onStateChange(ConnectionState.CONNECTING)
    onStatus("Connecting...")

    lateinit var audioEngine: AudioEngine

    val client = GeminiLiveClient(
        apiKey = apiKey,
        voiceName = voice,
        languageCode = language,
        systemPrompt = systemPrompt,
        listener = object : GeminiLiveClient.Listener {
            override fun onOpen() {
                onStateChange(ConnectionState.CONNECTED)
                onStatus("Listening... speak naturally")
                onMicActiveChange(true)
                audioEngine.preparePlayback()
                audioEngine.startRecording()
            }

            override fun onAudioChunk(base64Pcm: String) {
                audioEngine.playChunk(base64Pcm)
            }

            override fun onTextChunk(text: String) {
                onTranscript("Gemini: $text")
            }

            override fun onInterrupted() {
                audioEngine.clearPlaybackQueue()
            }

            override fun onTurnComplete() {
                // No-op for now; could be used to show a "your turn" indicator.
            }

            override fun onError(message: String) {
                onStateChange(ConnectionState.ERROR)
                onStatus("Error: $message")
                onMicActiveChange(false)
            }

            override fun onClosed() {
                onStateChange(ConnectionState.IDLE)
                onStatus("Session ended. Tap the mic to start again.")
                onMicActiveChange(false)
            }
        }
    )

    audioEngine = AudioEngine(onAudioChunk = { chunk -> client.sendAudioChunk(chunk) })

    onClientReady(client)
    onAudioEngineReady(audioEngine)

    client.connect()
}
