package com.example.geminivoicechat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * First screen: lets the user paste their Gemini API key and stores it locally
 * via DataStore. Nothing is sent anywhere except directly to Google's API
 * when a chat session starts.
 */
@Composable
fun ApiKeyScreen(
    settingsStore: SettingsStore,
    onSaved: () -> Unit
) {
    var apiKey by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        apiKey = settingsStore.getApiKey()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Enter your Gemini API Key",
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))

        Text(
            text = "Get a free key at aistudio.google.com. The key is stored only on this device and used to connect directly to Google's servers.",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 24.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 16.dp))

        Button(
            onClick = {
                scope.launch {
                    settingsStore.saveApiKey(apiKey)
                    onSaved()
                }
            },
            enabled = apiKey.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save and continue")
        }
    }
}
