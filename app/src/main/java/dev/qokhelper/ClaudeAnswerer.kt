package dev.qokhelper

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import java.time.Duration

/** Asks Claude through the official Anthropic SDK. A blank [baseUrl] means the official API. */
class ClaudeAnswerer(apiKey: String, private val model: String, baseUrl: String) : Answerer {

    private val customBaseUrl = baseUrl.isNotBlank()

    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .apply { if (customBaseUrl) baseUrl(baseUrl.trim().trimEnd('/')) }
        .timeout(Duration.ofSeconds(30))
        .maxRetries(1)
        .build()

    override fun answer(jpegBase64: String): Answer {
        val params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(4000L)
            // The game's question timer is short; low effort keeps the reply to a few seconds.
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
            .system(Prompt.SYSTEM)
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
                    ContentBlockParam.ofText(TextBlockParam.builder().text(Prompt.USER).build()),
                )
            )
            .apply {
                // Proxies may not pass beta fields through, so only ask for fallbacks on the official API.
                if (model in FALLBACK_MODELS && !customBaseUrl) {
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
        val SUGGESTED_MODELS = listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5")
        private val FALLBACK_MODELS = setOf("claude-opus-5-5", "claude-sonnet-5-5")
    }
}
