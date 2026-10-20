package dev.qokhelper

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiAnswererTest {

    @Test
    fun appendsChatCompletionsToBaseUrl() {
        assertEquals("https://api.openai.com/v1/chat/completions", OpenAiAnswerer.endpoint("https://api.openai.com/v1"))
        assertEquals("http://10.0.0.2:11434/v1/chat/completions", OpenAiAnswerer.endpoint(" http://10.0.0.2:11434/v1/ "))
    }

    @Test
    fun keepsFullEndpointUrl() {
        assertEquals(
            "https://openrouter.ai/api/v1/chat/completions",
            OpenAiAnswerer.endpoint("https://openrouter.ai/api/v1/chat/completions"),
        )
    }

    @Test
    fun requestCarriesModelPromptAndImage() {
        val messages = OpenAiAnswerer.initialMessages(webSearch = false, jpegBase64 = "QUJD")
        val body = JSONObject(OpenAiAnswerer.requestBody("gpt-4o", messages, webSearch = false, toolsAllowed = false))
        assertEquals("gpt-4o", body.getString("model"))
        assertFalse(body.has("tools"))
        val sent = body.getJSONArray("messages")
        assertEquals(Prompt.system(false), sent.getJSONObject(0).getString("content"))
        val image = sent.getJSONObject(1).getJSONArray("content").getJSONObject(1)
        assertEquals("data:image/jpeg;base64,QUJD", image.getJSONObject("image_url").getString("url"))
    }

    @Test
    fun webSearchDeclaresToolAndCanDisallowIt() {
        val messages = OpenAiAnswerer.initialMessages(webSearch = true, jpegBase64 = "QUJD")
        assertTrue(messages.getJSONObject(0).getString("content").contains("search the web"))

        val open = JSONObject(OpenAiAnswerer.requestBody("m", messages, webSearch = true, toolsAllowed = true))
        assertEquals("web_search", open.getJSONArray("tools").getJSONObject(0).getJSONObject("function").getString("name"))
        assertEquals("auto", open.getString("tool_choice"))

        val closed = JSONObject(OpenAiAnswerer.requestBody("m", messages, webSearch = true, toolsAllowed = false))
        assertEquals("none", closed.getString("tool_choice"))
    }

    @Test
    fun readsToolCalls() {
        val message = JSONObject(
            """{"role":"assistant","content":null,"tool_calls":[
                {"id":"call_1","type":"function","function":{"name":"web_search","arguments":"{\"query\":\"پایتخت استرالیا\"}"}}]}"""
        )
        assertEquals(
            listOf(OpenAiAnswerer.ToolCall("call_1", "web_search", "پایتخت استرالیا")),
            OpenAiAnswerer.toolCalls(message),
        )
    }

    @Test
    fun noToolCallsIsEmpty() {
        assertTrue(OpenAiAnswerer.toolCalls(JSONObject("""{"content":"1|x|50"}""")).isEmpty())
    }

    @Test
    fun extractsStringContent() {
        val body = """{"choices":[{"message":{"role":"assistant","content":"2|تهران|90"}}]}"""
        assertEquals("2|تهران|90", OpenAiAnswerer.firstMessage(body)?.let(OpenAiAnswerer::messageText))
    }

    @Test
    fun extractsContentParts() {
        val body = """{"choices":[{"message":{"content":[{"type":"text","text":"3|Paris|80"}]}}]}"""
        assertEquals("3|Paris|80", OpenAiAnswerer.firstMessage(body)?.let(OpenAiAnswerer::messageText))
    }

    @Test
    fun malformedResponseIsNull() {
        assertNull(OpenAiAnswerer.firstMessage("""{"error":{"message":"bad"}}"""))
    }
}
