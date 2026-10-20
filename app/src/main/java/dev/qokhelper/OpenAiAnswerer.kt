package dev.qokhelper

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Asks any server that speaks the OpenAI Chat Completions API (OpenAI, OpenRouter, a proxy,
 * a local server) using a vision-capable model. A blank [apiKey] sends no Authorization header.
 */
class OpenAiAnswerer(
    private val apiKey: String,
    private val model: String,
    baseUrl: String,
) : Answerer {

    private val endpoint = endpoint(baseUrl.ifBlank { Provider.OPENAI.defaultBaseUrl })

    override fun answer(jpegBase64: String): Answer {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
        }
        return try {
            conn.outputStream.use { it.write(requestBody(model, jpegBase64).toByteArray()) }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) return Answer.Failed("API error $code: ${body.take(120)}")
            val text = extractText(body) ?: return Answer.Failed("Unexpected response: ${body.take(120)}")
            AnswerParser.parse(text)
        } catch (e: IOException) {
            Answer.Failed("Network error: ${e.message?.take(120)}")
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        /** Accepts a base URL (`https://host/v1`) or a full `.../chat/completions` URL. */
        fun endpoint(baseUrl: String): String {
            val trimmed = baseUrl.trim().trimEnd('/')
            return if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
        }

        fun requestBody(model: String, jpegBase64: String): String = JSONObject()
            .put("model", model)
            .put(
                "messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", Prompt.SYSTEM))
                    .put(
                        JSONObject().put("role", "user").put(
                            "content", JSONArray()
                                .put(JSONObject().put("type", "text").put("text", Prompt.USER))
                                .put(
                                    JSONObject().put("type", "image_url").put(
                                        "image_url", JSONObject().put("url", "data:image/jpeg;base64,$jpegBase64")
                                    )
                                )
                        )
                    )
            )
            .toString()

        /** The first choice's message text, or null if the response isn't shaped as expected. */
        fun extractText(responseBody: String): String? = runCatching {
            val content = JSONObject(responseBody)
                .getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").get("content")
            when (content) {
                is String -> content
                // Some servers return content as a list of parts.
                is JSONArray -> (0 until content.length())
                    .mapNotNull { content.optJSONObject(it)?.optString("text")?.takeIf(String::isNotEmpty) }
                    .joinToString("\n")
                else -> null
            }
        }.getOrNull()
    }
}
