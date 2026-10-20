# QoK Helper

An Android app that reads the current [Quiz of Kings](https://play.google.com/store/apps/details?id=co.palang.QuizOfKingss) question off your screen and tells you which answer is right.

A floating **?** button sits on top of the game. Tap it during a question and the app takes a screenshot, sends it to Claude, and shows the answer in a card at the top of the screen:

```
گزینه 2
تهران
90%
```

That means option 2 counting from the top, the answer text, and how confident the model is.

There's no separate OCR step. Claude reads the Persian text straight from the screenshot, so it works for any question, including ones with pictures.

## Requirements

- Android 11 (API 30) or newer
- An [Anthropic API key](https://console.anthropic.com/)

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

1. Open **QoK Helper** and paste your API key.
2. Tap **Allow display over other apps** and turn the permission on.
3. Tap **Start** and accept the screen-capture prompt. If Android asks, choose to share the entire screen.
4. Open Quiz of Kings. When a question shows up, tap the floating **?**. It turns orange while it's thinking.
5. Tap the answer card to hide it. Drag the **?** to move it out of the way.

To stop, use **Stop** in the app or the **Stop** button on the notification.

## Choosing a model

| Model | Trade-off |
|---|---|
| `claude-opus-5-5` (default) | Most accurate, a few seconds per question |
| `claude-sonnet-5-5` | Faster, still strong on trivia |
| `claude-haiku-5-5` | Fastest and cheapest, less accurate on hard questions |

Every model runs at low effort, so it answers quickly enough for the game's timer. If the timer feels tight, switch to Sonnet or Haiku.

For Opus and Sonnet, requests also turn on the API's server-side refusal fallback. If a safety check declines a question, the API retries it on another model rather than failing.

## How it works

| File | Role |
|---|---|
| `MainActivity.kt` | Settings screen: API key, model, permissions, start/stop |
| `CaptureService.kt` | Foreground service that owns the screen capture and the overlay |
| `ScreenGrabber.kt` | Mirrors the screen into an `ImageReader` and keeps the newest frame, so a capture is instant |
| `OverlayController.kt` | The draggable **?** button and the answer card |
| `AnswerEngine.kt` | Sends a downscaled JPEG to the Claude API (official `anthropic-java` SDK) |
| `AnswerParser.kt` | Parses the model's `position\|answer\|confidence` reply |

Run the unit tests with `./gradlew testDebugUnitTest`.

## Status and caveats

- **Not yet tested on a real device.** The project builds and the parser tests pass, but no one has run the full capture → Claude → overlay flow on a phone. Expect some rough edges on the first run.
- The prompt assumes the four answers are stacked vertically. If the game lays them out in a grid, the option number may be off, but the answer text will still be correct.
- The debug APK is about 50 MB because of the SDK's dependencies. Turning on R8 minification would shrink it.
- Every tap is a paid API call.
- Using this in live matches is against the spirit of the game, and probably against its terms. Your account could get banned.
