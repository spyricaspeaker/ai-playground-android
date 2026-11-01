import { createHash, randomBytes, timingSafeEqual } from "node:crypto";
import { loadTokens, saveTokens, clearTokens } from "./token-store.js";

const AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
const TOKEN_URL = "https://oauth2.googleapis.com/token";
export const HOME_SCOPE = "https://www.googleapis.com/auth/home.platform.v2";
const pendingStates = new Map();
let refreshInFlight = null;

function required(name) {
  const value = process.env[name];
  if (!value) throw new Error(`Missing required environment variable: ${name}`);
  return value;
}

export function googleRedirectUri() {
  return required("GOOGLE_OAUTH_REDIRECT_URI");
}

function oauthClient() {
  return {
    client_id: required("GOOGLE_OAUTH_CLIENT_ID"),
    client_secret: required("GOOGLE_OAUTH_CLIENT_SECRET")
  };
}

function constantTimeEqual(a, b) {
  const aa = Buffer.from(String(a));
  const bb = Buffer.from(String(b));
  return aa.length === bb.length && timingSafeEqual(aa, bb);
}

export function createGoogleAuthorizationUrl() {
  const { client_id } = oauthClient();
  const state = randomBytes(32).toString("base64url");
  const verifier = randomBytes(48).toString("base64url");
  const challenge = createHash("sha256").update(verifier).digest("base64url");

  pendingStates.set(state, { verifier, createdAt: Date.now() });
  for (const [key, value] of pendingStates) {
    if (Date.now() - value.createdAt > 10 * 60 * 1000) pendingStates.delete(key);
  }

  const url = new URL(AUTH_URL);
  url.search = new URLSearchParams({
    client_id,
    redirect_uri: googleRedirectUri(),
    response_type: "code",
    scope: HOME_SCOPE,
    access_type: "offline",
    prompt: "consent",
    include_granted_scopes: "true",
    state,
    code_challenge: challenge,
    code_challenge_method: "S256"
  }).toString();
  return url.toString();
}

export async function completeGoogleAuthorization(code, state) {
  const pending = pendingStates.get(state);
  pendingStates.delete(state);
  if (!pending || Date.now() - pending.createdAt > 10 * 60 * 1000) {
    throw new Error("OAuth state is missing, expired, or already used.");
  }
  const { client_id, client_secret } = oauthClient();
  const response = await fetch(TOKEN_URL, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      code,
      client_id,
      client_secret,
      redirect_uri: googleRedirectUri(),
      grant_type: "authorization_code",
      code_verifier: pending.verifier
    }),
    signal: AbortSignal.timeout(20000)
  });
  const payload = await response.json();
  if (!response.ok) {
    throw new Error(`Google OAuth token exchange failed: ${payload.error_description || payload.error || response.status}`);
  }
  if (!payload.access_token) throw new Error("Google OAuth did not return an access token.");

  const previous = await loadTokens();
  const tokens = {
    access_token: payload.access_token,
    refresh_token: payload.refresh_token || previous?.refresh_token,
    token_type: payload.token_type || "Bearer",
    scope: payload.scope || HOME_SCOPE,
    expires_at: Date.now() + Number(payload.expires_in || 3600) * 1000
  };
  if (!tokens.refresh_token) {
    throw new Error("Google did not return a refresh token. Revoke the app grant and authorize again with offline access.");
  }
  await saveTokens(tokens);
  return { connected: true, expires_at: tokens.expires_at };
}

export async function getGoogleAccessToken() {
  if (refreshInFlight) return refreshInFlight;
  refreshInFlight = (async () => {
    let tokens = await loadTokens();
    if (!tokens?.refresh_token) return null;
    if (tokens.access_token && tokens.expires_at > Date.now() + 90_000) {
      return tokens.access_token;
    }

    const { client_id, client_secret } = oauthClient();
    const response = await fetch(TOKEN_URL, {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        client_id,
        client_secret,
        refresh_token: tokens.refresh_token,
        grant_type: "refresh_token"
      }),
      signal: AbortSignal.timeout(20000)
    });
    const payload = await response.json();
    if (!response.ok) {
      if (payload.error === "invalid_grant") await clearTokens();
      throw new Error(`Google OAuth refresh failed: ${payload.error_description || payload.error || response.status}`);
    }
    tokens = {
      ...tokens,
      access_token: payload.access_token,
      token_type: payload.token_type || "Bearer",
      scope: payload.scope || tokens.scope,
      expires_at: Date.now() + Number(payload.expires_in || 3600) * 1000
    };
    await saveTokens(tokens);
    return tokens.access_token;
  })();

  try {
    return await refreshInFlight;
  } finally {
    refreshInFlight = null;
  }
}

export async function googleConnectionStatus() {
  const tokens = await loadTokens();
  return {
    connected: Boolean(tokens?.refresh_token),
    accessTokenExpiresAt: tokens?.expires_at || null
  };
}

export async function disconnectGoogle() {
  await clearTokens();
}
