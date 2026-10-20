# QoK Helper

An Android app that reads the current [Quiz of Kings](https://play.google.com/store/apps/details?id=co.palang.QuizOfKingss) question off your screen and tells you which answer is right.

A floating **?** button sits on top of the game. Tap it during a question and the app takes a screenshot, sends it to a vision AI model, and shows the answer in a card at the top of the screen. The model can be Claude, or any OpenAI-compatible API with your own key and base URL:

```
گزینه 2
تهران
90%
```

That means option 2 counting from the top, the answer text, and how confident the model is.

There's no separate OCR step. The model reads the Persian text straight from the screenshot, so it works for any question, including ones with pictures.

## Requirements

- Android 11 (API 30) or newer
- One of these:
  - an [Anthropic API key](https://console.anthropic.com/)
  - an OpenAI key
  - any OpenAI-compatible endpoint with a vision model (OpenRouter, a proxy, Ollama/LM Studio/vLLM on your network)

## Install

Build the APK:

```bash
./gradlew assembleDebug
```

Then install it on a phone with USB debugging on:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Building needs JDK 17+ and the Android SDK (platform 36). Set `sdk.dir` in `local.properties` or `ANDROID_HOME`.

## Use

1. Open **QoK Helper**, pick a provider, and fill in your API key (plus model and base URL if needed; see below).
2. Tap **Allow display over other apps** and turn the permission on.
3. Tap **Start** and accept the screen-capture prompt. If Android asks, choose to share the entire screen.
4. Open Quiz of Kings. When a question shows up, tap the floating **?**. It turns orange while it's thinking.
5. Tap the answer card to hide it. Drag the **?** to move it out of the way.

To stop, use **Stop** in the app or the **Stop** button on the notification.

## Providers

Each provider remembers its own key, model, and base URL, so you can switch back and forth without retyping.

### Claude (Anthropic)

Uses the official `anthropic-java` SDK.

| Model | Trade-off |
|---|---|
| `claude-opus-5-5` (default) | Most accurate, a few seconds per question |
| `claude-sonnet-5-5` | Faster, still strong on trivia |
| `claude-haiku-5-5` | Fastest and cheapest, less accurate on hard questions |

Every model runs at low effort, so it answers quickly enough for the game's timer. If the timer feels tight, switch to Sonnet or Haiku.

Leave **Base URL** blank to use `https://api.anthropic.com`, or set it to an Anthropic-compatible proxy.

When you use the official endpoint with Opus or Sonnet, requests also turn on the API's server-side refusal fallback. If a safety check declines a question, the API retries it on another model rather than failing. This is skipped when a custom base URL is set, in case the proxy doesn't support it.

### OpenAI-compatible

Calls `POST {base URL}/chat/completions` and sends the screenshot as an `image_url` data URI. The model must support images.

| Use with | Base URL | Model example |
|---|---|---|
| OpenAI | blank (defaults to `https://api.openai.com/v1`) | `gpt-4o` |
| OpenRouter | `https://openrouter.ai/api/v1` | `openai/gpt-4o` |
| Ollama on your network | `http://<computer-ip>:11434/v1` | `qwen2.5vl` |
| Any other compatible server or proxy | its `/v1` URL (a full `.../chat/completions` URL also works) | whatever it serves |

The API key is sent as `Authorization: Bearer <key>`. You can leave it blank for keyless local servers, as long as a base URL is set. Plain `http://` URLs are allowed, so LAN servers work.

## Web search

Turn on **Let the model search the web** in settings (it's on by default) and the model can look things up before it answers. It's told to answer from memory when it's confident and to search only when it isn't, or when the question is about recent events, exact numbers, or dates. It can make two searches per question at most. Expect a search to add a few seconds.

| Provider | How it searches |
|---|---|
| Claude | Anthropic's server-side web search tool. Search runs on Anthropic's side, with no extra setup. API web searches are billed per search on top of tokens. |
| OpenAI-compatible | A `web_search` function tool. When the model calls it, the app searches [DuckDuckGo](https://html.duckduckgo.com/html/) from the phone, falls back to Wikipedia (Persian or English, matching the query) if that comes back empty, and returns the top results to the model. No extra key needed, but the model must support tool calling. |

DuckDuckGo's HTML page isn't an official API, so it could change or block requests. If that happens, the Wikipedia fallback still works.

## How it works

| File | Role |
|---|---|
| `MainActivity.kt` | Settings screen: provider, API key, model, base URL, web search, permissions, start/stop |
| `CaptureService.kt` | Foreground service that owns the screen capture and the overlay |
| `ScreenGrabber.kt` | Mirrors the screen into an `ImageReader` and keeps the newest frame, so a capture is instant |
| `OverlayController.kt` | The draggable **?** button and the answer card |
| `Answerer.kt` | The provider interface, provider list, and the shared prompt |
| `ClaudeAnswerer.kt` | Sends a downscaled JPEG to the Claude API through the `anthropic-java` SDK, with optional server-side web search |
| `OpenAiAnswerer.kt` | Sends it to any OpenAI-compatible Chat Completions endpoint and runs the `web_search` tool loop |
| `WebSearch.kt` | Keyless search on the phone (DuckDuckGo, then Wikipedia) for the OpenAI-compatible tool |
| `AnswerParser.kt` | Parses the model's `position\|answer\|confidence` reply |

Run the unit tests with `./gradlew testDebugUnitTest`.

## Status and caveats

- **Not yet tested on a real device.** The project builds and the unit tests pass, but no one has run the full capture → model → overlay flow on a phone or made a live call to either provider. Expect some rough edges on the first run.
- The prompt assumes the four answers are stacked vertically. If the game lays them out in a grid, the option number may be off, but the answer text will still be correct.
- The debug APK is about 50 MB because of the SDK's dependencies. Turning on R8 minification would shrink it.
- Every tap is an API call, which costs money unless you run a local model. The screenshot is sent to whichever provider you configure.
- Using this in live matches is against the spirit of the game, and probably against its terms. Your account could get banned.
