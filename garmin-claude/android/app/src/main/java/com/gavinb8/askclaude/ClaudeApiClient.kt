package com.gavinb8.askclaude

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Talks to the Claude API directly (current Anthropic Messages API,
 * https://docs.claude.com/en/api/messages), or -- if BRIDGE_SERVER_URL is
 * configured -- to the optional /server bridge instead, so the raw API key
 * never has to live on the phone at all. This is where the API key is used;
 * it never leaves this process, and it is never sent to the watch.
 */
class ClaudeApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    fun ask(
        history: List<ConversationStore.Turn>,
        prompt: String,
        config: AppConfig
    ): String {
        return if (config.bridgeServerUrl.isNotBlank()) {
            askViaBridge(history, prompt, config)
        } else {
            askAnthropicDirect(history, prompt, config)
        }
    }

    private fun askAnthropicDirect(
        history: List<ConversationStore.Turn>,
        prompt: String,
        config: AppConfig
    ): String {
        if (config.apiKey.isBlank()) {
            throw IllegalStateException(
                "No Claude API key configured. Set it in the app's Settings screen or in android/secrets.properties."
            )
        }

        val messages = JSONArray()
        for (turn in history) {
            messages.put(
                JSONObject()
                    .put("role", turn.role)
                    .put("content", turn.text)
            )
        }
        messages.put(JSONObject().put("role", "user").put("content", prompt))

        val body = JSONObject()
            .put("model", config.model)
            .put("max_tokens", 1024)
            .put("messages", messages)

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", config.apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val apiMessage = runCatching {
                    JSONObject(responseBody).getJSONObject("error").getString("message")
                }.getOrDefault(responseBody.take(200))
                throw IOException("Claude API error (${response.code}): $apiMessage")
            }

            val json = JSONObject(responseBody)
            val content = json.getJSONArray("content")
            val text = StringBuilder()
            for (i in 0 until content.length()) {
                val block = content.getJSONObject(i)
                if (block.optString("type") == "text") {
                    text.append(block.getString("text"))
                }
            }
            return text.toString()
        }
    }

    private fun askViaBridge(
        history: List<ConversationStore.Turn>,
        prompt: String,
        config: AppConfig
    ): String {
        val historyJson = JSONArray()
        for (turn in history) {
            historyJson.put(JSONObject().put("role", turn.role).put("content", turn.text))
        }
        val body = JSONObject()
            .put("model", config.model)
            .put("prompt", prompt)
            .put("history", historyJson)

        val requestBuilder = Request.Builder()
            .url(config.bridgeServerUrl.trimEnd('/') + "/ask")
            .post(body.toString().toRequestBody(jsonMedia))
        if (config.bridgeSecret.isNotBlank()) {
            requestBuilder.addHeader("x-bridge-secret", config.bridgeSecret)
        }
        val request = requestBuilder.build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Bridge server error (${response.code}): ${responseBody.take(200)}")
            }
            return JSONObject(responseBody).getString("text")
        }
    }
}
