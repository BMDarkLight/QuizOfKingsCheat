package dev.qokhelper

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        val body = JSONObject(OpenAiAnswerer.requestBody("gpt-4o", "QUJD"))
        assertEquals("gpt-4o", body.getString("model"))
        val messages = body.getJSONArray("messages")
        assertEquals(Prompt.SYSTEM, messages.getJSONObject(0).getString("content"))
        val image = messages.getJSONObject(1).getJSONArray("content").getJSONObject(1)
        assertEquals("data:image/jpeg;base64,QUJD", image.getJSONObject("image_url").getString("url"))
    }

    @Test
    fun extractsStringContent() {
        val body = """{"choices":[{"message":{"role":"assistant","content":"2|تهران|90"}}]}"""
        assertEquals("2|تهران|90", OpenAiAnswerer.extractText(body))
    }

    @Test
    fun extractsContentParts() {
        val body = """{"choices":[{"message":{"content":[{"type":"text","text":"3|Paris|80"}]}}]}"""
        assertEquals("3|Paris|80", OpenAiAnswerer.extractText(body))
    }

    @Test
    fun malformedResponseIsNull() {
        assertNull(OpenAiAnswerer.extractText("""{"error":{"message":"bad"}}"""))
    }
}
