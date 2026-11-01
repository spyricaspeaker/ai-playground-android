# AI Playground for Android

Android app with four OpenAI-powered agents: ARIA (creative), NOVA (explorer), ATLAS (builder), and ECHO (observer/coordinator).

## Voice communication

Tap **Speak** to request microphone permission and start one speech-recognition session. The recognized message is sent to the selected agent or the four-agent Team. Replies are displayed and spoken using Android text-to-speech when available. The app does not continuously record or listen in the background.

## Backend deployment

Deploy the Node.js service in \`backend/\` using the included \`render.yaml\`. Configure these backend environment variables:

- \`OPENAI_API_KEY\`: OpenAI API key. Store it only in backend secret settings, never in the Android app or Git.
- \`APP_ACCESS_CODE\`: create a long random secret and enter the same value in the app.
- `OPENAI_MODEL`: economical default model; defaults to `gpt-6-luna`.
- `OPENAI_MODEL_COMPLEX`: stronger model for research, engineering, and team synthesis; defaults to `gpt-6.1-sol`.
- `OPENAI_MODEL_NOVA` and `OPENAI_MODEL_ATLAS`: optional per-agent overrides. If unset, both use `OPENAI_MODEL_COMPLEX`.
- `OPENAI_MODEL_ARIA` and `OPENAI_MODEL_ECHO`: optional per-agent overrides. If unset, both use `OPENAI_MODEL`.

After deployment, enter the service HTTPS URL and app access code in the app. The \`/health\` endpoint should return \`{"ok":true}\`.

Team mode runs four independent agent responses and one ECHO synthesis call. NOVA and ATLAS use the stronger model by default, and final synthesis uses it too. Team mode therefore uses more API calls and can cost more than a single-agent chat.

## Build

GitHub Actions builds a debug APK and runs emulator UI tests. Download the \`AI-Playground-APK\` artifact from a successful workflow run.
