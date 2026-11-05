# NEURA BAI 0.1

An Android prototype for simulated brain-computer-interface communication.

## What works in this prototype

- Simulated input events: YES, NO, HELP, REPEAT and FOCUS.
- A signal interpreter that maps each event to a structured intent.
- Four selectable agent profiles: ARIA, NOVA, ATLAS and ECHO.
- Role-specific responses and an in-session conversation history.
- Android Text-to-Speech output.
- An optional HTTPS model-gateway integration.
- A visible human-approval boundary for future external actions.

## Important limits

This build does **not** connect to an EEG headset and does not decode thoughts. Signal buttons generate ordinary demo events. It does not control smart-home devices, robots, accounts or other external systems. The permission switch is a UI placeholder, not an authorization implementation.

When no model gateway is configured, the app uses deterministic local interpretation and role-based responses. To connect a real language model, provide a secure HTTPS backend URL in the app. Do not embed an OpenAI API key in the APK.

## Model gateway contract

The app sends an HTTP POST with JSON:

```json
{
  "signal": "HELP",
  "meaning": "Ask for assistance",
  "agent": "ARIA",
  "agentRole": "Companion"
}
```

The backend should return:

```json
{
  "title": "Request for assistance",
  "response": "A model-generated interpretation or response."
}
```

The gateway should authenticate the app, validate requests, apply rate limits and call the model provider using a server-side secret. Use HTTPS only.

## Build

The GitHub Actions workflow `.github/workflows/build-apk.yml` builds a debug APK with Java 17, Gradle 8.9 and Android SDK 35.

Run the workflow from the Actions tab, selecting the `neura-bai-0.1` branch. Download the artifact named `NEURA-BAI-0.1-APK`; it contains `app-debug.apk`.

## Next engineering stages

1. Validate the simulated flow on an Android emulator.
2. Add a secure model gateway and streaming conversational responses.
3. Add persistent, user-controlled memory and per-agent permissions.
4. Inspect the Spyrica headset protocol and implement a separate EEG transport adapter.
5. Calibrate signal classification against actual device data before treating any signal as an intentional command.
