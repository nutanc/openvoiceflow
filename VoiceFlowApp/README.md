# VoiceFlow for Android

AI-powered voice dictation with a floating bubble overlay and OpenAI GPT.

## How It Works

1. **Tap any text field** in any app
2. **Tap the floating 🎙️ bubble**
3. **Speak naturally** — Android's speech recognition transcribes your voice
4. **OpenAI corrects** the transcription (removes fillers, fixes grammar, adapts style)
5. **Text appears** in the focused text field

## Setup

### Prerequisites

- Android device running **API 26+** (Android 8.0 Oreo or later)
- OpenAI API key ([get one here](https://platform.openai.com/api-keys))

### Build & Install

```bash
cd VoiceFlowApp
./gradlew installDebug
```

### First Launch

1. Enter your **OpenAI API key** on the main screen
2. Grant the required permissions:
   - **Overlay** — for the floating bubble
   - **Microphone** — for speech recognition
   - **Accessibility** — for pasting text into any app
3. Tap **Start VoiceFlow** — the bubble appears!

### Settings

- **Model**: Default is `gpt-4o-mini`. Change to `gpt-4o` or any OpenAI-compatible model.
- **API URL**: Default is OpenAI. Change for Azure, local LLM servers, or any compatible endpoint.
- **System Prompt**: Customize how the AI corrects your transcriptions.
- **Language**: Select your speech recognition language.

## Architecture

| Component | File | Purpose |
|---|---|---|
| Floating Bubble | `FloatingBubbleService.kt` | Draggable overlay, tap to record |
| Speech Recognition | `SpeechRecognitionManager.kt` | Android SpeechRecognizer wrapper |
| LLM Corrector | `OpenAiLlmCorrector.kt` | OpenAI API via OkHttp |
| Text Pasting | `TextPasteAccessibilityService.kt` | Paste into focused text fields |
| Pipeline | `VoiceFlowOrchestrator.kt` | Coordinates the full flow |
| Config | `AppConfig.kt` | Settings, API key, system prompt |

## Based On

Android port of [OpenVoiceFlow](../openvoiceflow), a desktop voice dictation tool. Key differences:

- **ASR**: Android SpeechRecognizer (free, on-device) instead of Whisper API
- **Interface**: Floating bubble overlay instead of keyboard hotkey
- **Text Pasting**: AccessibilityService instead of clipboard + Ctrl+V
- **LLM**: Same OpenAI GPT as desktop version
