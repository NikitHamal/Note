package com.omgodse.notally.ai

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class AiClient {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "https://chat.together.ai/api/chat-completion"

    // Hardcoded cookies and headers from provided example (escaped for Kotlin string)
    private val cookieHeader: String = (
        "_ga=GA1.1.2025824345.1754860976; " +
            "__client_uat=1754861008; " +
            "__client_uat_QCPDW_r5=1754861008; " +
            "__refresh_QCPDW_r5=MYsMKb6c7GaziUeid7po; " +
            "clerk_active_context=sess_3172u1R9gPdr8K4zFnQjkE34PbR:; " +
            "_gcl_au=1.1.93808326.1754861031; " +
            "__session=eyJhbGciOiJSUzI1NiIsImNhdCI6ImNsX0I3ZDRQRDExMUFBQSIsImtpZCI6Imluc18yc2l3SnFqU0lzWDR2NzN0RlhzcFBsVlpscVciLCJ0eXAiOiJKV1QifQ.eyJhenAiOiJodHRwczovL2NoYXQudG9nZXRoZXIuYWkiLCJleHAiOjE3NTQ4NjExMzQsImZ2YSI6WzEsLTFdLCJpYXQiOjE3NTQ4NjEwNzQsImlzcyI6Imh0dHBzOi8vY2xlcmsuY2hhdC50b2dldGhlci5haSIsIm5iZiI6MTc1NDg2MTA2NCwic2lkIjoic2Vzc18zMTcydTFSOWdQZHI4SzR6Rm5RamtFMzRQYlIiLCJzdWIiOiJ1c2VyXzJ4dExFYW4wak1BGTE4QWJpYnNpcTB5OElCZiJ9.y_MIL2DimRuu0_dBcdKwN8Q7CE8n0NXpl_28B5yLmnrp50aYT5C-Z_U7buTTXU40DIkDtN4ZnUnAy_nA7SHZHxX4LUGBhdN0MoHkhiCZEPb19rb-N1e_5xf0wyxkD5HbIx8RVg0E3NqsksV9PX_P-JlNigBEEGHZuUG6zU5j9REUbxUeOUCmA8GobeOL8apjuKBMS9qRkoGL7dJBeDTI2HI3VcnGeWGjNLM-zrTwicNwq2CHYHZroYVynJtBRZVitjabkqITJVppc-9u3mimq8rJz2XDeZtX6OEjj5FosOFxq65kRhBCHuasH1QzVbOTBMA0x8JM108kjIhneGwzbw; " +
            "__session_QCPDW_r5=eyJhbGciOiJSUzI1NiIsImNhdCI6ImNsX0I3ZDRQRDExMUFBQSIsImtpZCI6Imluc18yc2l3SnFqU0lzWDR2NzN0RlhzcFBsVlpscVciLCJ0eXAiOiJKV1QifQ.eyJhenAiOiJodHRwczovL2NoYXQudG9nZXRoZXIuYWkiLCJleHAiOjE3NTQ4NjExMzQsImZ2YSI6WzEsLTFdLCJpYXQiOjE3NTQ4NjEwNzQsImlzcyI6Imh0dHBzOi8vY2xlcmsuY2hhdC50b2dldGhlci5haSIsIm5iZiI6MTc1NDg2MTA2NCwic2lkIjoic2Vzc18zMTcydTFSOWdQZHI4SzR6Rm5RamtFMzRQYlIiLCJzdWIiOiJ1c2VyXzJ4dExFYW4wak1BZTE4QWJpYnNpcTB5OElCZiJ9.y_MIL2DimRuu0_dBcdKwN8Q7CE8n0NXpl_28B5yLmnrp50aYT5C-Z_U7buTTXU40DIkDtN4ZnUnAy_nA7SHZHxX4LUGBhdN0MoHkhiCZEPb19rb-N1e_5xf0wyxkD5HbIx8RVg0E3NqsksV9PX_P-JlNigBEEGHZuUG6zU5j9REUbxUeOUCmA8GobeOL8apjuKBMS9qRkoGL7dJBeDTI2HI3VcnGeWGjNLM-zrTwicNwq2CHYHZroYVynJtBRZVitjabkqITJVppc-9u3mimq8rJz2XDeZtX6OEjj5FosOFxq65kRhBCHuasH1QzVbOTBMA0x8JM108kjIhneGwzbw; " +
            "_ga_BS43X21GZ2=GS2.1.s1754860975\$o1\$g1\$t1754861075\$j24\$l0\$h1396503129; " +
            "_ga_BBHKJ5V8S0=GS2.1.s1754860975\$o1\$g1\$t1754861075\$j24\$l0\$h872211495"
    )

    /**
     * Formats AI-generated content to be properly displayed in the note
     * Ensures proper paragraph spacing and line breaks
     */
    private fun formatContent(rawContent: String): String {
        return rawContent
            .trim()
            // Normalize line breaks - convert Windows/Mac line endings to Unix
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            // Ensure proper paragraph spacing - two newlines between paragraphs
            .replace(Regex("\n{3,}"), "\n\n")
            // Ensure bullet points and headings have proper spacing
            .replace(Regex("(?<=\n)([*•-]\\s)"), "\n$1")
            .replace(Regex("(?<=\n)(#{1,6}\\s)"), "\n$1")
            // Clean up any trailing whitespace
            .replace(Regex("\\s+$"), "")
    }

    fun chatComplete(
        systemPrompt: String,
        userPrompt: String,
        assistantContent: String? = null,
        maxTokens: Int = 8192,
        callback: (Result<String>) -> Unit
    ) {
        val modelId = "0bbf06d8-22bd-47a9-89ad-75e8b2183389"
        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            if (!assistantContent.isNullOrBlank()) {
                put(JSONObject().put("role", "assistant").put("content", assistantContent))
            }
            put(JSONObject().put("role", "user").put("content", userPrompt))
        }
        val bodyJson = JSONObject().apply {
            put("modelId", modelId)
            put("messages", messages)
            put("stream", false)
            put("maxTokens", maxTokens)
            put("options", JSONObject().put("task", "generate-title"))
        }

        val mediaType = "text/plain; charset=UTF-8".toMediaType()
        val requestBody = bodyJson.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(baseUrl)
            .post(requestBody)
            .header("accept", "*/*")
            .header("content-type", "text/plain;charset=UTF-8")
            .header("cookie", cookieHeader)
            .header("origin", "https://chat.together.ai")
            .header("referer", "https://chat.together.ai/")
            .header("sec-ch-ua", "\"Chromium\";v=\"137\", \"Not/A)Brand\";v=\"24\"")
            .header("sec-ch-ua-mobile", "?1")
            .header("sec-ch-ua-platform", "\"Android\"")
            .header("sec-fetch-dest", "empty")
            .header("sec-fetch-mode", "cors")
            .header("sec-fetch-site", "same-origin")
            .header(
                "user-agent",
                "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"
            )
            .header("accept-language", "en-US,en;q=0.9")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        callback(Result.failure(IOException("HTTP ${it.code}")))
                        return
                    }
                    val bodyString = it.body?.string() ?: ""
                    val json = JSONObject(bodyString)
                    val choices = json.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        val message = choices.getJSONObject(0).getJSONObject("message")
                        val rawContent = message.optString("content")
                        val formattedContent = formatContent(rawContent)
                        callback(Result.success(formattedContent))
                    } else {
                        callback(Result.failure(IllegalStateException("No choices in response")))
                    }
                }
            }
        })
    }
}