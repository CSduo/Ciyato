package com.ciyato.launcher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * GeminiAndroidClient — Suggestion #40
 * Wires the Android app to the /api/v1/ai/query proxy endpoint.
 * Uses the JWT token stored in LauncherSettingsRepository for auth.
 *
 * The server routes to Gemini 1.5 Flash or GPT-4o-mini depending on the model param.
 */
object GeminiAndroidClient {

    private const val DEFAULT_API_BASE = "https://ciyato-api.example.com"

    data class AiResponse(
        val text: String,
        val model: String,
        val inputTokens: Int = 0,
        val outputTokens: Int = 0,
    )

    sealed class AiResult {
        data class Success(val response: AiResponse) : AiResult()
        data class Error(val message: String) : AiResult()
    }

    /**
     * Send a query to the AI proxy endpoint.
     *
     * @param prompt  The user prompt.
     * @param jwt     Bearer token from user session.
     * @param model   "gemini" (default) or "openai".
     * @param apiBase Override base URL (e.g. from settings).
     */
    suspend fun query(
        prompt: String,
        jwt: String,
        model: String = "gemini",
        apiBase: String = DEFAULT_API_BASE,
    ): AiResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("$apiBase/api/v1/ai/query")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $jwt")
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000

            val body = JSONObject().apply {
                put("prompt", prompt)
                put("model", model)
                put("maxTokens", 512)
            }.toString()

            OutputStreamWriter(conn.outputStream).use { it.write(body) }

            val code = conn.responseCode
            val responseText = if (code in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
            }

            if (code in 200..299) {
                val json = JSONObject(responseText)
                AiResult.Success(
                    AiResponse(
                        text = json.optString("text", ""),
                        model = json.optString("model", model),
                        inputTokens = json.optInt("inputTokens", 0),
                        outputTokens = json.optInt("outputTokens", 0),
                    )
                )
            } else {
                AiResult.Error("Server error $code: $responseText")
            }
        } catch (e: Exception) {
            AiResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Build a prompt for the daily agenda summary feature (#35).
     */
    fun buildAgendaPrompt(
        recentApps: List<String>,
        calendarEvents: List<String>,
        hour: Int,
    ): String = buildString {
        append("You are Ciyato, an AI phone assistant. Generate a concise, friendly daily agenda summary.\n\n")
        append("Time of day: ${if (hour < 12) "morning" else if (hour < 17) "afternoon" else "evening"}\n")
        if (recentApps.isNotEmpty()) append("Recently used apps: ${recentApps.joinToString(", ")}\n")
        if (calendarEvents.isNotEmpty()) append("Upcoming events: ${calendarEvents.joinToString("; ")}\n")
        append("\nWrite a 2-3 sentence summary with a productivity tip. Be warm and concise.")
    }

    /**
     * Build a prompt for cleanup suggestions (#26).
     */
    fun buildCleanupPrompt(unusedApps: List<String>): String =
        "I haven't used these apps in 30+ days: ${unusedApps.joinToString(", ")}. " +
        "Briefly explain which ones are safe to uninstall and why, in 3-4 bullet points."
}
