package dev.qokhelper

/** Turns a screenshot into an answer. Implementations block; call off the main thread. */
interface Answerer {
    fun answer(jpegBase64: String): Answer
}

enum class Provider(val label: String, val defaultModel: String, val defaultBaseUrl: String) {
    CLAUDE("Claude (Anthropic)", "claude-opus-5-5", "https://api.anthropic.com"),
    OPENAI("OpenAI-compatible", "gpt-4o", "https://api.openai.com/v1");

    fun create(settings: ProviderSettings): Answerer = when (this) {
        CLAUDE -> ClaudeAnswerer(settings.apiKey, settings.model, settings.baseUrl)
        OPENAI -> OpenAiAnswerer(settings.apiKey, settings.model, settings.baseUrl)
    }
}

data class ProviderSettings(val apiKey: String, val model: String, val baseUrl: String)

object Prompt {
    const val USER = "Answer the question."

    val SYSTEM = """
        You are looking at a phone screenshot of Quiz of Kings (کوییز آف کینگز), a Persian trivia game.
        The screen shows one multiple-choice question and four answer buttons. Work out the correct answer.

        Reply with exactly one line and nothing else:
        <position>|<answer text>|<confidence>

        - position: 1-4, the correct button's position counting from the top. If the buttons are in a
          2x2 grid, count the top row first, right to left.
        - answer text: the correct button's text, copied exactly as shown.
        - confidence: 0-100, how sure you are.

        If no question is visible on screen, reply NONE.
    """.trimIndent()
}
