package com.example.geminivoicechat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// Voices offered by Gemini Live API's prebuilt voice config.
private val AVAILABLE_VOICES = listOf("Puck", "Charon", "Kore", "Fenrir", "Aoede")

// A short list of common language codes; free text is also accepted.
private val AVAILABLE_LANGUAGES = listOf(
    "en-US" to "English (US)",
    "en-GB" to "English (UK)",
    "ar-XA" to "Arabic",
    "fr-FR" to "French",
    "es-US" to "Spanish"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsStore: SettingsStore,
    onBack: () -> Unit
) {
    var voice by remember { mutableStateOf(SettingsStore.DEFAULT_VOICE) }
    var language by remember { mutableStateOf(SettingsStore.DEFAULT_LANGUAGE) }
    var systemPrompt by remember { mutableStateOf("") }
    var voiceMenuExpanded by remember { mutableStateOf(false) }
    var languageMenuExpanded by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        voice = settingsStore.getVoiceName()
        language = settingsStore.getLanguageCode()
        systemPrompt = settingsStore.getSystemPrompt()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineSmall)

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 24.dp))

        Text(text = "Voice", style = MaterialTheme.typography.labelLarge)
        ExposedDropdownMenuBox(
            expanded = voiceMenuExpanded,
            onExpandedChange = { voiceMenuExpanded = it }
        ) {
            OutlinedTextField(
                value = voice,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceMenuExpanded) }
            )
            DropdownMenu(
                expanded = voiceMenuExpanded,
                onDismissRequest = { voiceMenuExpanded = false }
            ) {
                AVAILABLE_VOICES.forEach { v ->
                    DropdownMenuItem(
                        text = { Text(v) },
                        onClick = {
                            voice = v
                            voiceMenuExpanded = false
                        }
                    )
                }
            }
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 16.dp))

        Text(text = "Response language", style = MaterialTheme.typography.labelLarge)
        ExposedDropdownMenuBox(
            expanded = languageMenuExpanded,
            onExpandedChange = { languageMenuExpanded = it }
        ) {
            val currentLabel = AVAILABLE_LANGUAGES.firstOrNull { it.first == language }?.second ?: language
            OutlinedTextField(
                value = currentLabel,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageMenuExpanded) }
            )
            DropdownMenu(
                expanded = languageMenuExpanded,
                onDismissRequest = { languageMenuExpanded = false }
            ) {
                AVAILABLE_LANGUAGES.forEach { (code, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            language = code
                            languageMenuExpanded = false
                        }
                    )
                }
            }
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 16.dp))

        Text(text = "System prompt (optional)", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = systemPrompt,
            onValueChange = { systemPrompt = it },
            placeholder = { Text("e.g. Keep answers short and casual.") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            minLines = 3
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) {
                Text("Cancel")
            }
            Button(onClick = {
                scope.launch {
                    settingsStore.saveVoiceName(voice)
                    settingsStore.saveLanguageCode(language)
                    settingsStore.saveSystemPrompt(systemPrompt)
                    onBack()
                }
            }) {
                Text("Save")
            }
        }
    }
}
