# Clawlando

> **🔴 Known issue:** Tap coordinates are currently unreliable — the agent frequently taps in the wrong area of the screen. This is a known bug under investigation, likely a coordinate scaling issue between the model's output space and the device's physical display. Actions that do not require precise tapping (scrolling, text input, app launching via `open_app`) work correctly.

An open-source Android "computer use" agent. Tell it what to do in plain English and it takes screenshots, reasons about the UI, and executes taps, swipes, and text input on your phone autonomously.

## How it works

```
You: "Open Settings and turn on WiFi"

Agent loop:
  1. OBSERVE  - takes a screenshot via AccessibilityService
  2. REASON   - sends screenshot + task + history to a vision-language model
  3. PARSE    - extracts THOUGHT and ACTION from model response
  4. ACT      - executes tap/swipe/type via AccessibilityService gestures
  5. REPEAT   - loops until the task is done or max steps reached
```

The app runs as a floating overlay on top of other apps, so you can trigger it from anywhere. An animated glow border shows the agent's current state (reasoning, observing, acting).

## Inference backends

Clawlando supports three inference backends:

| Backend | Model | Where it runs | Pros | Cons |
|---------|-------|---------------|------|------|
| **Qwen** | Qwen2.5-VL-7B | HuggingFace Inference Endpoint | Strong vision + UI understanding, easy to deploy with TGI | Requires cloud GPU (~$1-3/hr) |
| **Cloud** | MolmoWeb-8B | HuggingFace Inference Endpoint | Purpose-built for web/UI interaction | Limited deployment support |
| **Local** | SmolVLM2-2.2B | On-device via llama.cpp | Fully offline, no server needed | Requires ~3GB RAM, slower, less accurate |

All cloud backends use the OpenAI-compatible `/v1/chat/completions` API format.

## Features

- Floating overlay panel with prompt input (works over any app)
- Observe-reason-act agent loop with three swappable backends
- Full action space: tap, long press, swipe, scroll, type, back, home, open app, open URL
- Multi-step task execution with conversation history
- Animated glow overlay showing agent state
- Pause, resume, and stop the agent at any time
- On-device inference with llama.cpp (ARM64, SmolVLM2-2.2B Q4_K_M)
- In-app model download with progress tracking
- Task history stored locally with Room
- Setup wizard for permissions
- Detailed logcat logging for debugging (`adb logcat -s Clawlando:* LlamaCppJNI:*`)

## Requirements

- Android 11+ (API 30+), ARM64 device
- For cloud/Qwen: a [HuggingFace Inference Endpoint](https://huggingface.co/inference-endpoints)
- For local: ~3GB free RAM + ~1.7GB storage for model files
- Accessibility Service permission (for screenshots and gestures)
- "Display over other apps" permission (for the floating overlay)

## Setup

### 1. Deploy a model (cloud)

**Qwen2.5-VL-7B (recommended):**

1. Go to [HuggingFace Inference Endpoints](https://huggingface.co/inference-endpoints)
2. Create a new endpoint with model `Qwen/Qwen2.5-VL-7B-Instruct`
3. Select a GPU instance (NVIDIA A10G or L4)
4. Wait for it to spin up, copy the endpoint URL

**Or use local inference** — no server needed, just download the model from Settings.

### 2. Build the app

```bash
git clone https://github.com/omeirhaeghe/molmo-agent.git
cd molmo-agent
git submodule update --init --recursive
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### 3. Configure

1. Install the APK on your Android device
2. Open **Clawlando** and follow the setup wizard (Accessibility Service + overlay permission)
3. Go to **Settings**, pick your backend (Qwen / Cloud / Local), and enter the endpoint URL or download the local model
4. Tap **Test Connection** to verify (cloud backends)

### 4. Use it

1. Tap the overlay icon — the floating panel appears at the bottom of your screen
2. Type a task (e.g., "open the calculator app and compute 42 * 17")
3. Watch the agent work — the glow border shows its state
4. Hit **Stop** at any time to abort

## Architecture

```
com.example.molmoagent/
├── agent/           # Core agent loop, action parsing, execution
├── accessibility/   # AccessibilityService, gesture dispatch
├── inference/
│   ├── local/       # llama.cpp JNI bridge, SmolVLM2 client, model download
│   ├── HuggingFaceClient   # Cloud + Qwen (OpenAI-compatible API)
│   ├── InferenceClientManager  # Switches between backends transparently
│   ├── ResponseParser      # Shared THOUGHT/ACTION extraction
│   └── ImageProcessor      # Screenshot resize + base64 encoding
├── screen/          # Screenshot capture
├── ui/
│   ├── overlay/     # Floating panel + animated glow overlay
│   ├── setup/       # Permission setup wizard
│   ├── settings/    # Backend selection + endpoint config + model download
│   └── chat/        # Task history view
├── data/            # Room database for task/step persistence
├── di/              # Hilt dependency injection
└── cpp/             # llama.cpp submodule + JNI bridge (C++)
```

### Action space

| Action | Description |
|---|---|
| `click(x, y)` | Tap at normalized coordinates (0.0-1.0) |
| `long_press(x, y)` | Long press at coordinates |
| `type("text")` | Type into the focused input field |
| `scroll(direction)` | Scroll up/down/left/right |
| `press_back()` | Press the Android back button |
| `press_home()` | Press the home button |
| `open_app("name")` | Launch an app by name |
| `goto("url")` | Open a URL in the browser |
| `wait()` | Wait for the UI to settle |
| `send_msg_to_user("msg")` | Task complete, report result |

## Tech stack

- **Kotlin** + **Jetpack Compose** for UI
- **Hilt** for dependency injection
- **Room** for local persistence
- **OkHttp** for network requests
- **llama.cpp** + JNI for on-device inference (ARM64)
- **HuggingFace TGI** for cloud inference (OpenAI-compatible API)
- **AccessibilityService** for screenshots and gesture dispatch

## Debugging

Monitor local inference in real-time:

```bash
adb logcat -s Clawlando:* LlamaCppJNI:*
```

This shows model loading progress, prefill speed, token generation rate, and parsed actions.

## License

MIT
