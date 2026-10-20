package dev.qokhelper

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.AnthropicIoException
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import java.time.Duration

/** Sends a screenshot to Claude and returns the answer it picks. Blocking; call off the main thread. */
class AnswerEngine(apiKey: String, private val model: String) {

    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .timeout(Duration.ofSeconds(30))
        .maxRetries(1)
        .build()

    fun answer(jpegBase64: String): Answer {
        val params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(4000L)
            // The game's question timer is short; low effort keeps the reply to a few seconds.
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
            .system(SYSTEM_PROMPT)
            .addUserMessageOfBlockParams(
                listOf(
                    ContentBlockParam.ofImage(
                        ImageBlockParam.builder()
                            .source(
                                Base64ImageSource.builder()
                                    .data(jpegBase64)
                                    .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
                                    .build()
                            )
                            .build()
                    ),
                    ContentBlockParam.ofText(TextBlockParam.builder().text("Answer the question.").build()),
                )
            )
            .apply {
                if (model in FALLBACK_MODELS) {
                    // If a safety classifier refuses, let the API retry on another model instead of failing.
                    putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                    putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                }
            }
            .build()

        return try {
            val response = client.messages().create(params)
            if (response.stopReason().orElse(null) == StopReason.REFUSAL) {
                return Answer.Failed("Claude declined this one")
            }
            val text = response.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("\n")
            AnswerParser.parse(text)
        } catch (e: AnthropicServiceException) {
            Answer.Failed("API error ${e.statusCode()}: ${e.message?.take(120)}")
        } catch (e: AnthropicIoException) {
            Answer.Failed("Network error: ${e.message?.take(120)}")
        }
    }

    companion object {
        val MODELS = listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5")
        private val FALLBACK_MODELS = setOf("claude-opus-5-5", "claude-sonnet-5-5")

        private val SYSTEM_PROMPT = """
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
}
