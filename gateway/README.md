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

## 1. Google Cloud OAuth setup

Google Home MCP is an Early Access product. Google's published prerequisites include a Google Home setup, Google Home Premium Advanced subscription, Google Cloud project, Home API enabled, and OAuth credentials. Review the official [Google Home MCP guide](https://developers.home.google.com/mcp/home).

1. Create/select a Google Cloud project.
2. Enable the **Home API**.
3. Configure Google Auth Platform consent screen and audience/test users.
4. Create an OAuth 2.0 **Web application** client.
5. Deploy this gateway behind HTTPS and choose its public hostname.
6. Set the authorized redirect URI to exactly:
   `https://YOUR_PUBLIC_GATEWAY_HOST/oauth/google/callback`
7. Copy the OAuth Client ID and Client Secret into the gateway's server-side environment.

The gateway requests this scope only:

`https://www.googleapis.com/auth/home.platform.v2`

Do not put OAuth client secrets, Google access/refresh tokens, or agent bearer tokens in the Android APK or source control.

## 2. Configure secrets

Copy `.env.example` to a secret-managed environment (or a local untracked `.env`). Generate separate secrets:

```bash
openssl rand -hex 32 # run separately for each agent and admin token
openssl rand -hex 32 # HOME_TOKEN_ENCRYPTION_KEY
```

Set all of these on the server:

- `ARIA_TOKEN`, `NOVA_TOKEN`, `ATLAS_TOKEN`, `ECHO_TOKEN`: four unique random strings, each at least 32 characters.
- `HOME_ADMIN_TOKEN`: a separate random secret, at least 32 characters.
- `HOME_TOKEN_ENCRYPTION_KEY`: exactly 64 hexadecimal characters (32 bytes).
- `GOOGLE_OAUTH_CLIENT_ID`, `GOOGLE_OAUTH_CLIENT_SECRET`.
- `GOOGLE_OAUTH_REDIRECT_URI`: exact public HTTPS callback registered in Google Cloud.
- `GOOGLE_HOME_TOKEN_FILE`: encrypted token-file location on persistent storage.
- Optional `GOOGLE_HOME_MCP_URL`, `MCP_PROTOCOL_VERSION`, `PORT`, and `HOST`.

The token file is encrypted with AES-256-GCM. Back up the encryption key separately from the encrypted file. Losing the key requires reauthorization.

## 3. Run locally

Requires Node.js 20 or later.

```bash
cd gateway
npm test
npm start
```

The default listener binds to `127.0.0.1`. For remote use, put it behind a trusted HTTPS reverse proxy and configure `HOST=127.0.0.1` behind that proxy, or use an appropriately protected private network. Do not expose the service directly to the public internet.

## 4. Connect Google Home

After the gateway is running and the public HTTPS callback is reachable, open this URL while supplying the administrator bearer token:

```bash
curl -s -H "Authorization: Bearer $HOME_ADMIN_TOKEN" \
  "https://YOUR_PUBLIC_GATEWAY_HOST/oauth/google/start"
```

The endpoint returns an `authorizationUrl`. Open that URL in a browser and complete authorization using the Google account that owns/manages the intended Home structure. Google redirects to the callback; the gateway exchanges the code, stores the refresh token encrypted, and refreshes access tokens automatically.

Check connection status:

```bash
curl -H "Authorization: Bearer $HOME_ADMIN_TOKEN" \
  "https://YOUR_PUBLIC_GATEWAY_HOST/v1/home/connection"
```

Disconnect and erase stored credentials:

```bash
curl -X POST -H "Authorization: Bearer $HOME_ADMIN_TOKEN" \
  "https://YOUR_PUBLIC_GATEWAY_HOST/v1/home/disconnect"
```

## 5. Agent API

Each agent receives only its own bearer token. The Google OAuth credential is shared by the gateway, never returned to agents.

- `GET /health` — service health and Google connection status.
- `GET /v1/agents/me` — return the authenticated agent's effective policy.
- `GET /v1/home/tools` — return Google Home MCP tool schemas filtered by agent policy.
- `POST /v1/home/tools/call` — authorize and forward one MCP tool call.

Example:

```bash
curl "https://YOUR_PUBLIC_GATEWAY_HOST/v1/home/tools" \
  -H "Authorization: Bearer $ARIA_TOKEN"
```

Tool call:

```bash
curl -X POST "https://YOUR_PUBLIC_GATEWAY_HOST/v1/home/tools/call" \
  -H "Authorization: Bearer $ARIA_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"tool":"list_homes","arguments":{}}'
```

## 6. MCP transport and session handling

The MCP client performs JSON-RPC initialization, retains the returned MCP session ID and negotiated protocol version, sends the initialized notification, and uses the session for subsequent tool discovery and calls. It accepts JSON and `text/event-stream` responses and parses SSE `data:` frames into JSON-RPC messages. Expired/unauthorized sessions are cleared and reported; retry after the session is re-established.

The Google Home MCP endpoint is `https://home.googleapis.com/mcp`. The official reference lists `list_homes`, `list_home_resources`, `list_home_states`, `list_home_history`, and `run_home_actions`. Tool schemas are fetched live with `tools/list`; do not hard-code command payloads. See the [official MCP reference](https://developers.home.google.com/reference/home/mcp).

## 7. Security and production notes

1. Use HTTPS, a trusted reverse proxy, rate limiting, and firewall rules.
2. Keep agent tokens, OAuth secrets, and encryption keys in a server-side secret manager.
3. Use persistent encrypted storage for refresh tokens; protect backups and rotate keys with a planned migration.
4. The current audit output is stdout for development. Add durable, access-controlled audit storage before production.
5. Agent policies are an additional application authorization layer; they do not narrow Google's OAuth grant.
6. The gateway filters tool schemas and checks every call, but the current device-category classifier is intentionally conservative. Validate against the live Home MCP schemas and expand tests for your exact device traits before allowing device control.
7. Google Home MCP itself applies safety protections. Keep them enabled. Tell household members before connecting a shared home.
8. Google currently documents Home MCP as Early Access and says automation creation/management is not supported.

## Tests

Run `npm test` to verify agent isolation, read-only ATLAS access, ECHO's speaker-only control, NOVA's plug access, and global denials. GitHub Actions runs these tests when gateway files change.
