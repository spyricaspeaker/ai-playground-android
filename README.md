# AI Playground for Android

Android app with four AI agents: ARIA (creative), NOVA (explorer), ATLAS (builder), and ECHO (observer/coordinator). The backend can use either OpenAI or NVIDIA Nemotron.

## Voice communication

Tap **Speak** to request microphone permission and start one speech-recognition session. The recognized message is sent to the selected agent or the four-agent Team. Replies are displayed and spoken using Android text-to-speech when available. The app does not continuously record or listen in the background.

## Backend deployment

Deploy the Node.js service in `backend/` using the included `render.yaml`. Configure these backend environment variables in the hosting service's secret/environment settings:

- `AI_PROVIDER`: set to `openai` (default) or `nvidia`.
- `APP_ACCESS_CODE`: create a long random secret and enter the same value in the app.
- `OPENAI_API_KEY`: required when `AI_PROVIDER=openai`. Keep it in backend secrets only.
- `NVIDIA_API_KEY`: required when `AI_PROVIDER=nvidia`. Create an API key in the NVIDIA Build portal and keep it in backend secrets only.
- `OPENAI_MODEL`: economical OpenAI default model; defaults to `gpt-6-luna`.
- `OPENAI_MODEL_COMPLEX`: stronger OpenAI model for research, engineering, and team synthesis; defaults to `gpt-6.1-sol`.
- `OPENAI_MODEL_NOVA` and `OPENAI_MODEL_ATLAS`: optional per-agent OpenAI overrides.
- `OPENAI_MODEL_ARIA` and `OPENAI_MODEL_ECHO`: optional per-agent OpenAI overrides.
- `NVIDIA_MODEL`: Nemotron model for ARIA and ECHO; defaults to `nvidia/nemotron-3.5-lightning-30b-a3b`.
- `NVIDIA_MODEL_COMPLEX`: Nemotron model for NOVA, ATLAS, and team synthesis; defaults to `nvidia/nemotron-3-super-120b-a12b`.
- `NVIDIA_MODEL_ARIA`, `NVIDIA_MODEL_NOVA`, `NVIDIA_MODEL_ATLAS`, and `NVIDIA_MODEL_ECHO`: optional per-agent NVIDIA model overrides.

### Enable Nemotron

1. Create an NVIDIA API key at [NVIDIA Build](https://build.nvidia.com/).
2. In the backend hosting dashboard, add `NVIDIA_API_KEY` as a secret.
3. Set `AI_PROVIDER` to `nvidia`.
4. Deploy/restart the backend and check `/health`. It should return `{"ok":true,"service":"ai-playground-agent-api","provider":"nvidia"}`.
5. Send a test message to each agent, then test Team mode.

The NVIDIA integration uses NVIDIA's OpenAI-compatible Chat Completions endpoint at `https://integrate.api.nvidia.com/v1`. The backend keeps the API key server-side; it is never sent to the Android app. To switch back, set `AI_PROVIDER=openai` and ensure `OPENAI_API_KEY` is configured.

Team mode runs four independent agent responses and one ECHO synthesis call. It therefore makes five model requests and can consume more API quota than a single-agent chat. Hosted model availability and rate limits are controlled by the provider.

## Build

GitHub Actions builds a debug APK and runs emulator UI tests. Download the `AI-Playground-APK` artifact from a successful workflow run.
