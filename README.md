# Molmo Agent

An open-source Android "computer use" agent that automates phone tasks using [Allen AI's MolmoWeb](https://allenai.org/blog/molmoweb) vision model.

Tell the agent what to do in plain English and it will take screenshots, reason about the UI, and execute taps, swipes, and text input on your phone autonomously.

## How it works

```
You: "Open Settings and turn on WiFi"

Agent loop:
  1. OBSERVE  - takes a screenshot via AccessibilityService
  2. REASON   - sends screenshot + task + history to MolmoWeb-8B
  3. PARSE    - extracts THOUGHT and ACTION from model response
  4. ACT      - executes tap/swipe/type via AccessibilityService gestures
  5. REPEAT   - loops until the task is done or max steps reached
```

The app runs as a floating overlay on top of other apps, so you can trigger it from anywhere.

## Features

- Floating overlay panel with prompt input (works over any app)
- Observe-reason-act agent loop powered by MolmoWeb-8B
- Full action space: tap, long press, swipe, scroll, type, back, home, open app, open URL
- Multi-step task execution with conversation history
- Pause, resume, and stop the agent at any time
- Task history stored locally with Room
- Setup wizard for permissions
- Configurable HuggingFace Inference Endpoint

## Requirements

- Android 11+ (API 30+)
- A [HuggingFace Inference Endpoint](https://huggingface.co/inference-endpoints) running `allenai/MolmoWeb-8B`
- Accessibility Service permission (for screenshots and gestures)
- "Display over other apps" permission (for the floating overlay)

## Setup

### 1. Deploy MolmoWeb-8B

Deploy the model to a HuggingFace Inference Endpoint:

1. Go to [huggingface.co/allenai/MolmoWeb-8B](https://huggingface.co/allenai/MolmoWeb-8B)
2. Click **Deploy** > **Inference Endpoints**
3. Select a GPU instance (NVIDIA A10G or L4 recommended)
4. Click **Create Endpoint** and wait for it to spin up
5. Copy the endpoint URL

Cost is roughly $1-3/hr depending on GPU. You can pause the endpoint when not in use.

### 2. Build the app

```bash
git clone https://github.com/omeirhaeghe/molmo-agent.git
cd molmo-agent
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

Or open the project in Android Studio and run directly on your device.

### 3. Configure the app

1. Install the APK on your Android device
2. Open **Molmo Agent** and follow the setup wizard:
   - Enable the Accessibility Service
   - Grant "Display over other apps" permission
3. Go to **Settings** and enter your HuggingFace endpoint URL and API token
4. Tap **Test Connection** to verify

### 4. Use it

1. The floating overlay appears at the bottom of your screen
2. Type a task (e.g., "open the calculator app and compute 42 * 17")
3. The agent starts working - you can watch its progress in the overlay
4. Hit **Stop** at any time to abort

## Architecture

```
com.example.molmoagent/
├── agent/           # Core agent loop, action parsing, execution
├── accessibility/   # AccessibilityService, gesture dispatch, global actions
├── inference/       # HuggingFace client, prompt builder, image processing
├── screen/          # Screenshot capture manager
├── ui/
│   ├── overlay/     # Floating panel service (works over other apps)
│   ├── setup/       # Permission setup wizard
│   ├── settings/    # Endpoint configuration
│   └── chat/        # Task history view
├── data/            # Room database for task/step persistence
└── di/              # Hilt dependency injection
```

### Action space

| Action | Description |
|---|---|
| `click(x, y)` | Tap at normalized coordinates |
| `long_press(x, y)` | Long press at coordinates |
| `type("text")` | Type into the focused input field |
| `scroll(direction)` | Scroll up/down/left/right |
| `press_back()` | Press the Android back button |
| `press_home()` | Press the home button |
| `open_app("name")` | Launch an app by name |
| `goto("url")` | Open a URL in the browser |
| `send_msg_to_user("msg")` | Task complete, show result to user |

## Tech stack

- **Kotlin** + **Jetpack Compose** for UI
- **Hilt** for dependency injection
- **Room** for local persistence
- **OkHttp** for network requests
- **MolmoWeb-8B** via HuggingFace Inference Endpoints
- **AccessibilityService** for screenshots (API 30+) and gesture dispatch

## Roadmap

- [ ] Finish prototype and stabilize agent loop
- [ ] Memory / context persistence to improve multi-session performance
- [ ] KPI tracking (task success rate, steps per task)
- [ ] Benchmarking against standard mobile automation datasets
- [ ] MolmoPoint-GUI integration for more accurate element grounding
- [ ] On-device inference with quantized MolmoWeb-4B
- [ ] Fine-tune on mobile UI trajectories for a true "MolmoMobile" model

## Status

> Work in progress. This is a prototype demonstrating vision-model-driven mobile automation.
> AI-assisted development.

## License

MIT
