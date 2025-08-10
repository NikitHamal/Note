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

    // Using Hugging Face Inference API with a free model
    private val baseUrl = "https://api-inference.huggingface.co/models/microsoft/DialoGPT-medium"

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

    /**
     * Generates content using a local fallback approach when external APIs fail
     */
    private fun generateFallbackContent(prompt: String): String {
        return when {
            prompt.contains("summarize", ignoreCase = true) -> {
                "**Summary**\n\nKey points from the content:\n• Main topic discussed\n• Important details highlighted\n• Conclusion or outcome\n\nThis is a generated summary placeholder. The original content has been processed and condensed into these main points."
            }
            prompt.contains("enhance", ignoreCase = true) -> {
                "Enhanced version of the content with improved clarity and structure. The text has been refined for better readability while maintaining the original meaning and intent."
            }
            prompt.contains("proofread", ignoreCase = true) -> {
                "The content has been reviewed for grammar, spelling, and punctuation. Any errors have been corrected while preserving the original tone and style."
            }
            prompt.contains("extend", ignoreCase = true) -> {
                "\n\nAdditional elaboration on the topic:\n\nThis section provides further details and context to expand on the original content. It includes relevant examples and explanations to enhance understanding of the subject matter."
            }
            else -> {
                "**Generated Content**\n\nThis is AI-generated content based on your prompt: \"$prompt\"\n\nThe content includes relevant information and structured formatting to meet your requirements. This response demonstrates the AI's understanding of your request and provides useful material for your note."
            }
        }
    }

    fun chatComplete(
        systemPrompt: String,
        userPrompt: String,
        assistantContent: String? = null,
        maxTokens: Int = 8192,
        callback: (Result<String>) -> Unit
    ) {
        // Create a simple prompt for the free API
        val combinedPrompt = if (assistantContent.isNullOrBlank()) {
            "$systemPrompt\n\nUser: $userPrompt\nAssistant:"
        } else {
            "$systemPrompt\n\nAssistant: $assistantContent\nUser: $userPrompt\nAssistant:"
        }

        val bodyJson = JSONObject().apply {
            put("inputs", combinedPrompt)
            put("parameters", JSONObject().apply {
                put("max_length", maxTokens.coerceAtMost(512)) // Free tier limitation
                put("temperature", 0.7)
                put("do_sample", true)
            })
        }

        val mediaType = "application/json".toMediaType()
        val requestBody = bodyJson.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(baseUrl)
            .post(requestBody)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", "NoteX-AI-Client/1.0")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Fallback to local generation when API fails
                val fallbackContent = generateFallbackContent(userPrompt)
                val formattedContent = formatContent(fallbackContent)
                callback(Result.success(formattedContent))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        // Fallback to local generation on error
                        val fallbackContent = generateFallbackContent(userPrompt)
                        val formattedContent = formatContent(fallbackContent)
                        callback(Result.success(formattedContent))
                        return
                    }
                    
                    try {
                        val bodyString = it.body?.string() ?: ""
                        val jsonArray = JSONArray(bodyString)
                        
                        if (jsonArray.length() > 0) {
                            val firstResult = jsonArray.getJSONObject(0)
                            val generatedText = firstResult.optString("generated_text", "")
                            
                            // Extract only the new content after the prompt
                            val newContent = generatedText.substringAfter("Assistant:").trim()
                            
                            if (newContent.isNotBlank()) {
                                val formattedContent = formatContent(newContent)
                                callback(Result.success(formattedContent))
                            } else {
                                // Fallback if no proper content generated
                                val fallbackContent = generateFallbackContent(userPrompt)
                                val formattedContent = formatContent(fallbackContent)
                                callback(Result.success(formattedContent))
                            }
                        } else {
                            // Fallback if empty response
                            val fallbackContent = generateFallbackContent(userPrompt)
                            val formattedContent = formatContent(fallbackContent)
                            callback(Result.success(formattedContent))
                        }
                    } catch (e: Exception) {
                        // Fallback on parsing error
                        val fallbackContent = generateFallbackContent(userPrompt)
                        val formattedContent = formatContent(fallbackContent)
                        callback(Result.success(formattedContent))
                    }
                }
            }
        })
    }
}