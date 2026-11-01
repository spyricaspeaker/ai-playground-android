# AI Playground Google Home Permission Gateway

A Node.js 20+ service that enforces independent application-level permissions for ARIA, NOVA, ATLAS, and ECHO before forwarding Google Home MCP tool calls.

## Permission model

| Agent | Read | Control |
|---|---|---|
| ARIA | homes, devices, states | lights, thermostats, speakers |
| NOVA | homes, devices, states | lights, thermostats, plugs |
| ATLAS | homes, devices, states, history | none |
| ECHO | homes, devices, states | speakers |

All agents are denied camera, door/lock, garage, and alarm operations. Unknown tools, identities, and ambiguous control payloads are denied.

## Run locally

Requires Node.js 20 or later.

Generate four independent random bearer tokens and set them as environment variables. Do not commit real tokens.

```bash
export ARIA_TOKEN="$(openssl rand -hex 32)"
export NOVA_TOKEN="$(openssl rand -hex 32)"
export ATLAS_TOKEN="$(openssl rand -hex 32)"
export ECHO_TOKEN="$(openssl rand -hex 32)"
export PORT=8787
npm test
npm start
```

To enable upstream calls, configure a valid Google Home MCP OAuth access token on the server:

```bash
export GOOGLE_HOME_ACCESS_TOKEN="YOUR_SHORT_LIVED_OAUTH_ACCESS_TOKEN"
export GOOGLE_HOME_MCP_URL="https://home.googleapis.com/mcp"
```

Never place the Google OAuth token or agent bearer tokens in the Android APK, source control, logs, or an AI prompt. Use a secret manager and a token refresh mechanism for a deployed service.

## API

- `GET /health` — service health and upstream configuration status.
- `GET /v1/agents/me` — return the authenticated agent's effective policy.
- `POST /v1/home/tools/call` — authorize and forward one MCP tool call.

Example request:

```bash
curl http://localhost:8787/v1/home/tools/call \
  -H "Authorization: Bearer $ARIA_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"tool":"list_home_states","arguments":{"home_id":"YOUR_HOME_ID"}}'
```

Example control request shape for the policy engine:

```json
{
  "tool": "run_home_actions",
  "arguments": {
    "actions": [
      { "device_type": "light", "command": "turn_on" }
    ]
  }
}
```

The exact arguments must match the live Google Home MCP tool schema returned by its `tools/list` response. The gateway intentionally fails closed when it cannot classify a control request.

## Important deployment notes

1. Put this service behind HTTPS and a trusted reverse proxy. Do not expose it directly to the public internet.
2. Store agent tokens and Google OAuth credentials in a server-side secret manager; rotate them regularly.
3. Add durable, access-controlled audit storage before production use. The current audit output is stdout for development.
4. Implement OAuth refresh and the exact Google Home MCP SSE/session lifecycle for your approved Google OAuth client before treating upstream connectivity as production-ready.
5. The gateway's agent policies do not narrow Google's OAuth grant. They are an additional application-side authorization layer.
6. Google Home MCP itself applies its own safety protections. Keep those enabled.
7. Validate the current upstream tool schemas before enabling any device-control operation.

## Tests

Run `npm test` to verify agent isolation, read-only ATLAS access, ECHO's speaker-only control, NOVA's plug access, and global denials.
