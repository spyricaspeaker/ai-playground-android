import { randomUUID } from "node:crypto";
import { getGoogleAccessToken } from "./google-oauth.js";

const MCP_URL = process.env.GOOGLE_HOME_MCP_URL || "https://home.googleapis.com/mcp";
const PROTOCOL_VERSION = process.env.MCP_PROTOCOL_VERSION || "2025-03-26";
const CLIENT_INFO = { name: "ai-playground-home-gateway", version: "0.2.0" };

let session = null;
let initializePromise = null;
let toolsCache = null;
let toolsCacheAt = 0;

export function parseSse(text) {
  const messages = [];
  for (const block of text.split(/\r?\n\r?\n/)) {
    const dataLines = block.split(/\r?\n/)
      .filter((line) => line.startsWith("data:"))
      .map((line) => line.slice(5).replace(/^ /, ""));
    if (!dataLines.length) continue;
    const data = dataLines.join("\n");
    if (data === "[DONE]") continue;
    try { messages.push(JSON.parse(data)); } catch { /* ignore non-JSON SSE events */ }
  }
  return messages;
}

async function postRpc(method, params = {}, id = randomUUID(), { allowNoSession = false } = {}) {
  const accessToken = await getGoogleAccessToken();
  if (!accessToken) {
    const error = new Error("Google Home is not connected. Complete /oauth/google/start first.");
    error.status = 503;
    throw error;
  }

  const headers = {
    authorization: `Bearer ${accessToken}`,
    "content-type": "application/json",
    accept: "application/json, text/event-stream"
  };
  if (session?.id) headers["mcp-session-id"] = session.id;
  if (session?.protocolVersion) headers["mcp-protocol-version"] = session.protocolVersion;

  const response = await fetch(MCP_URL, {
    method: "POST",
    headers,
    body: JSON.stringify({
      jsonrpc: "2.0",
      ...(id ? { id } : {}),
      method,
      params
    }),
    signal: AbortSignal.timeout(45000)
  });

  const newSessionId = response.headers.get("mcp-session-id");
  if (newSessionId) {
    session = { ...(session || {}), id: newSessionId };
  }

  if (response.status === 401) {
    session = null;
    const error = new Error("Google Home rejected the OAuth access token. Reconnect Google Home.");
    error.status = 503;
    throw error;
  }
  if (response.status === 404 && session?.id) {
    session = null;
    const error = new Error("Google Home MCP session expired. Retry the request to create a new session.");
    error.status = 503;
    throw error;
  }
  if (!response.ok && response.status !== 202) {
    const detail = (await response.text()).slice(0, 1000);
    const error = new Error(`Google Home MCP HTTP ${response.status}: ${detail}`);
    error.status = 502;
    throw error;
  }
  if (response.status === 202 || method.startsWith("notifications/")) return null;

  const contentType = response.headers.get("content-type") || "";
  const body = await response.text();
  let messages;
  if (contentType.includes("text/event-stream")) {
    messages = parseSse(body);
  } else if (body.trim()) {
    try { messages = [JSON.parse(body)]; } catch {
      throw new Error("Google Home MCP returned neither JSON nor parseable SSE.");
    }
  } else {
    messages = [];
  }

  const rpc = messages.find((message) => message?.id === id) || messages.at(-1);
  if (rpc?.error) {
    const error = new Error(rpc.error.message || "Google Home MCP JSON-RPC error.");
    error.code = rpc.error.code;
    error.status = 502;
    throw error;
  }
  if (!rpc && !allowNoSession) {
    throw new Error(`Google Home MCP returned no JSON-RPC response for ${method}.`);
  }
  return rpc?.result ?? null;
}

export async function ensureMcpSession() {
  if (session?.initialized) return session;
  if (initializePromise) return initializePromise;

  initializePromise = (async () => {
    const result = await postRpc("initialize", {
      protocolVersion: PROTOCOL_VERSION,
      capabilities: {},
      clientInfo: CLIENT_INFO
    });
    const negotiated = result?.protocolVersion || PROTOCOL_VERSION;
    session = { ...(session || {}), initialized: true, protocolVersion: negotiated };
    await postRpc("notifications/initialized", {}, null, { allowNoSession: true });
    return session;
  })();

  try {
    return await initializePromise;
  } catch (error) {
    session = null;
    throw error;
  } finally {
    initializePromise = null;
  }
}

export async function listGoogleHomeTools(forceRefresh = false) {
  if (!forceRefresh && toolsCache && Date.now() - toolsCacheAt < 60_000) return toolsCache;
  await ensureMcpSession();
  const result = await postRpc("tools/list");
  const tools = result?.tools;
  if (!Array.isArray(tools)) throw new Error("Google Home MCP tools/list response did not contain a tools array.");
  toolsCache = tools;
  toolsCacheAt = Date.now();
  return tools;
}

export async function callGoogleHomeTool(name, args = {}) {
  await ensureMcpSession();
  return postRpc("tools/call", { name, arguments: args });
}

export function resetMcpSession() {
  session = null;
  toolsCache = null;
  toolsCacheAt = 0;
}
