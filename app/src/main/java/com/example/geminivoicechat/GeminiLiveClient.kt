package com.example.geminivoicechat

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.Response
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Thin client around the Gemini Live API's WebSocket endpoint.
 *
 * This talks directly to Google's servers using the user's own API key —
 * appropriate for personal/local use, not for an app you'd distribute publicly
 * (the key would be extractable from the APK).
 *
 * Protocol reference: https://ai.google.dev/gemini-api/docs/live-api
 */
class GeminiLiveClient(
    private val apiKey: String,
    private val modelName: String = "models/gemini-2.5-flash-native-audio-preview-09-2025",
    private val voiceName: String,
    private val languageCode: String,
    private val systemPrompt: String,
    private val listener: Listener
) {
    interface Listener {
        fun onOpen()
        fun onAudioChunk(base64Pcm: String)
        fun onTextChunk(text: String) {}
        fun onInterrupted() {}
        fun onTurnComplete() {}
        fun onError(message: String)
        fun onClosed()
    }

    private var webSocket: WebSocket? = null

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // streaming connection, no fixed read timeout
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    fun connect() {
        val url =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"

        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                sendSetupMessage(webSocket)
                listener.onOpen()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleServerMessage(bytes.utf8())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("GeminiLiveClient", "WebSocket failure", t)
                listener.onError(t.message ?: "Connection failed")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                listener.onClosed()
            }
        })
    }

    private fun sendSetupMessage(ws: WebSocket) {
        val setup = JSONObject().apply {
            put("setup", JSONObject().apply {
                put("model", modelName)
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().put("AUDIO"))
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", voiceName)
                            })
                        })
                        put("languageCode", languageCode)
                    })
                })
                if (systemPrompt.isNotBlank()) {
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                    })
                }
            })
        }
        ws.send(setup.toString())
    }

    /** Sends one chunk of mic audio (base64-encoded 16-bit PCM, 16kHz mono) to the model. */
    fun sendAudioChunk(base64Pcm: String) {
        val message = JSONObject().apply {
            put("realtimeInput", JSONObject().apply {
                put("mediaChunks", JSONArray().put(
                    JSONObject().apply {
                        put("mimeType", "audio/pcm;rate=16000")
                        put("data", base64Pcm)
                    }
                ))
            })
        }
        webSocket?.send(message.toString())
    }

    /** Sends a plain text message instead of audio (useful for a "type instead" fallback). */
    fun sendText(text: String) {
        val message = JSONObject().apply {
            put("clientContent", JSONObject().apply {
                put("turns", JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().put(JSONObject().put("text", text)))
                    }
                ))
                put("turnComplete", true)
            })
        }
        webSocket?.send(message.toString())
    }

    private fun handleServerMessage(raw: String) {
        try {
            val json = JSONObject(raw)

            // Model turned audio/text content back to us.
            val serverContent = json.optJSONObject("serverContent")
            if (serverContent != null) {
                if (serverContent.optBoolean("interrupted", false)) {
                    listener.onInterrupted()
                }

                val modelTurn = serverContent.optJSONObject("modelTurn")
                val parts = modelTurn?.optJSONArray("parts")
                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        val inlineData = part.optJSONObject("inlineData")
                        if (inlineData != null) {
                            val mime = inlineData.optString("mimeType", "")
                            if (mime.startsWith("audio/")) {
                                listener.onAudioChunk(inlineData.getString("data"))
                            }
                        }
                        val text = part.optString("text", "")
                        if (text.isNotEmpty()) {
                            listener.onTextChunk(text)
                        }
                    }
                }

                if (serverContent.optBoolean("turnComplete", false)) {
                    listener.onTurnComplete()
                }
            }

            // Setup acknowledgment or errors could be handled here too if needed.
            if (json.has("error")) {
                listener.onError(json.getJSONObject("error").toString())
            }
        } catch (e: Exception) {
            Log.e("GeminiLiveClient", "Failed to parse server message: $raw", e)
        }
    }

    fun close() {
        webSocket?.close(1000, "User ended session")
        webSocket = null
    }
}
