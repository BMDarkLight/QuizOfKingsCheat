package dev.qokhelper

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Asks any server that speaks the OpenAI Chat Completions API (OpenAI, OpenRouter, a proxy,
 * a local server) using a vision-capable model. A blank [apiKey] sends no Authorization header.
 *
 * With [webSearch], the model gets a `web_search` function tool that this app runs itself
 * (see [WebSearch]), so it works with any server whose model supports tool calls.
 */
class OpenAiAnswerer(
    private val apiKey: String,
    private val model: String,
    baseUrl: String,
    private val webSearch: Boolean,
) : Answerer {

    private val endpoint = endpoint(baseUrl.ifBlank { Provider.OPENAI.defaultBaseUrl })

    override fun answer(jpegBase64: String): Answer {
        val messages = initialMessages(webSearch, jpegBase64)
        return try {
            for (round in 0..MAX_SEARCH_ROUNDS) {
                // On the last round, take the tools away so the model has to answer.
                val toolsAllowed = webSearch && round < MAX_SEARCH_ROUNDS
                val (code, body) = post(requestBody(model, messages, webSearch, toolsAllowed))
                if (code !in 200..299) return Answer.Failed("API error $code: ${body.take(120)}")
                val message = firstMessage(body) ?: return Answer.Failed("Unexpected response: ${body.take(120)}")

                val calls = toolCalls(message)
                if (calls.isEmpty()) {
                    val text = messageText(message) ?: return Answer.Failed("Empty response")
                    return AnswerParser.parse(text)
                }
                messages.put(message)
                for (call in calls) {
                    val result = if (call.name == "web_search") WebSearch.search(call.query) else "Unknown tool: ${call.name}"
                    messages.put(JSONObject().put("role", "tool").put("tool_call_id", call.id).put("content", result))
                }
            }
            Answer.Failed("Model kept searching without answering")
        } catch (e: IOException) {
            Answer.Failed("Network error: ${e.message?.take(120)}")
        }
    }

    private fun post(body: String): Pair<Int, String> {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            return code to text
        } finally {
            conn.disconnect()
        }
    }

    data class ToolCall(val id: String, val name: String, val query: String)

    companion object {
        private const val MAX_SEARCH_ROUNDS = 2

        /** Accepts a base URL (`https://host/v1`) or a full `.../chat/completions` URL. */
        fun endpoint(baseUrl: String): String {
            val trimmed = baseUrl.trim().trimEnd('/')
            return if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
        }

        fun initialMessages(webSearch: Boolean, jpegBase64: String): JSONArray = JSONArray()
            .put(JSONObject().put("role", "system").put("content", Prompt.system(webSearch)))
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

        /**
         * Tools stay declared on every round once a conversation has used them (some servers reject
         * tool messages otherwise); [toolsAllowed] = false just stops the model from calling them.
         */
        fun requestBody(model: String, messages: JSONArray, webSearch: Boolean, toolsAllowed: Boolean): String =
            JSONObject()
                .put("model", model)
                .put("messages", messages)
                .apply {
                    if (webSearch) {
                        put("tools", JSONArray().put(SEARCH_TOOL))
                        put("tool_choice", if (toolsAllowed) "auto" else "none")
                    }
                }
                .toString()

        private val SEARCH_TOOL: JSONObject
            get() = JSONObject()
                .put("type", "function")
                .put(
                    "function", JSONObject()
                        .put("name", "web_search")
                        .put("description", "Search the web. Returns the top results with title, snippet and URL.")
                        .put(
                            "parameters", JSONObject()
                                .put("type", "object")
                                .put("properties", JSONObject().put("query", JSONObject().put("type", "string")))
                                .put("required", JSONArray().put("query"))
                        )
                )

        fun firstMessage(responseBody: String): JSONObject? = runCatching {
            JSONObject(responseBody).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        }.getOrNull()

        fun toolCalls(message: JSONObject): List<ToolCall> {
            val calls = message.optJSONArray("tool_calls") ?: return emptyList()
            return (0 until calls.length()).mapNotNull { i ->
                val call = calls.optJSONObject(i) ?: return@mapNotNull null
                val fn = call.optJSONObject("function") ?: return@mapNotNull null
                // Arguments are a JSON string; a model that sends malformed JSON gets its raw text searched.
                val rawArgs = fn.optString("arguments")
                val query = runCatching { JSONObject(rawArgs).getString("query") }.getOrDefault(rawArgs)
                ToolCall(id = call.optString("id"), name = fn.optString("name"), query = query)
            }
        }

        /** The message's text, whether the server sent it as a string or as a list of parts. */
        fun messageText(message: JSONObject): String? = when (val content = message.opt("content")) {
            is String -> content
            is JSONArray -> (0 until content.length())
                .mapNotNull { content.optJSONObject(it)?.optString("text")?.takeIf(String::isNotEmpty) }
                .joinToString("\n")
            else -> null
        }?.takeIf { it.isNotBlank() }
    }
}
