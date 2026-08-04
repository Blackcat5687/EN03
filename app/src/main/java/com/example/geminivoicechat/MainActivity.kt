package com.example.geminivoicechat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settingsStore = SettingsStore(applicationContext)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "chat") {
                        composable("apiKey") {
                            ApiKeyScreen(
                                settingsStore = settingsStore,
                                onSaved = { navController.navigate("chat") { popUpTo("apiKey") { inclusive = true } } }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                settingsStore = settingsStore,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("chat") {
                            ChatScreen(
                                settingsStore = settingsStore,
                                onOpenSettings = { navController.navigate("settings") },
                                onOpenApiKey = { navController.navigate("apiKey") }
                            )
                        }
                    }
                }
            }
        }
    }
}
