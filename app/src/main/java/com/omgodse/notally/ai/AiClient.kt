package com.omgodse.notally.ai

import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class AiClient {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "https://chat.together.ai/api/chat-completion"

    // WARNING: For demonstration only. Replace with your own valid cookie if required by your proxy.
    private val cookieHeader: String = ""

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

        val request = Request.Builder()
            .url(baseUrl)
            .post(RequestBody.create(MediaType.parse("text/plain; charset=UTF-8"), bodyJson.toString()))
            .header("content-type", "text/plain;charset=UTF-8")
            .header("accept", "*/*")
            .apply {
                if (cookieHeader.isNotBlank()) {
                    header("cookie", cookieHeader)
                }
            }
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        callback(Result.failure(IOException("HTTP ${it.code()}")))
                        return
                    }
                    val json = JSONObject(it.body()!!.string())
                    val choices = json.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        val message = choices.getJSONObject(0).getJSONObject("message")
                        val content = message.optString("content")
                        callback(Result.success(content))
                    } else {
                        callback(Result.failure(IllegalStateException("No choices in response")))
                    }
                }
            }
        })
    }
}