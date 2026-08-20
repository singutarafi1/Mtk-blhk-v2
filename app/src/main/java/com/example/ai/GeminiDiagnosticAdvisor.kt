package com.example.ai

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiDiagnosticAdvisor {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun analyzeMtkLogsAndSuggestFix(
        chipInfo: String,
        scatterPlatform: String,
        recentLogs: String,
        selectedPartition: String
    ): String = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Exception) {
            ""
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "💡 **Diagnostic Summary (Local Expert Engine)**:\n" +
                    "• **Chipset**: $chipInfo\n" +
                    "• **Scatter Platform**: $scatterPlatform\n" +
                    "• **Selected Partition**: $selectedPartition\n\n" +
                    "**Safety Guidance**:\n" +
                    "1. For BROM Handshake sync errors, ensure the test point is shorted to GND for at least 300-500ms before connecting VBUS.\n" +
                    "2. Always verify that NVRAM, NVDATA, and PROTECT1/2 partitions are backed up before flashing modified images.\n" +
                    "3. If preloader handoff times out, verify custom DA (Download Agent) matches the chipset HW_CODE.\n\n" +
                    "*(To enable cloud Gemini AI analysis, provide a GEMINI_API_KEY in the Secrets panel)*"
        }

        val prompt = """
            You are an expert embedded firmware and MediaTek (MTK) BROM / Preloader service engineer.
            Analyze the following repair session details and provide clear, actionable advice on:
            1. Potential BROM handshake or DA handoff issues
            2. Partition flash safety and NVRAM protection recommendations
            3. Exact test-point timing / trigger recommendations for ESP32-S3

            Session Details:
            - Detected Chip: $chipInfo
            - Scatter Platform: $scatterPlatform
            - Selected Partition: $selectedPartition
            - Terminal Log Excerpt:
            $recentLogs

            Provide your response formatted cleanly in markdown with concise bullet points and bold headers.
        """.trimIndent()

        try {
            val jsonBody = JSONObject().apply {
                val contents = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().put("text", prompt))
                        }
                        put("parts", parts)
                    }
                    put(contentObj)
                }
                put("contents", contents)
                
                val genConfig = JSONObject().apply {
                    put("temperature", 0.4)
                }
                put("generationConfig", genConfig)
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val respStr = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext "Gemini API notice: Status ${response.code}. Local diagnostics: ensure BROM test point short to GND is held during USB insertion."
            }

            val rootJson = JSONObject(respStr)
            val candidates = rootJson.optJSONArray("candidates")
            val firstCand = candidates?.optJSONObject(0)
            val content = firstCand?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val text = parts?.optJSONObject(0)?.optString("text")

            return@withContext text ?: "No diagnostic text returned."
        } catch (e: Exception) {
            return@withContext "AI Diagnostic Notice: ${e.message}\nEnsure ESP32-S3 test-point pulse is active LOW and MediaTek USB drivers are loaded."
        }
    }
}
