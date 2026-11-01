import http from "node:http";
import { randomUUID, timingSafeEqual } from "node:crypto";
import { AGENTS, authorizeToolCall } from "./policy.js";
import {
  createGoogleAuthorizationUrl, completeGoogleAuthorization,
  googleConnectionStatus, disconnectGoogle
} from "./google-oauth.js";
import {
  listGoogleHomeTools, callGoogleHomeTool, resetMcpSession
} from "./mcp-client.js";

const PORT = Number(process.env.PORT || 8787);
const HOST = process.env.HOST || "127.0.0.1";
const MAX_BODY = 256 * 1024;

const configuredAgentTokens = Object.keys(AGENTS).map((name) => process.env[`${name}_TOKEN`] || "");
const adminToken = process.env.HOME_ADMIN_TOKEN || "";
if (configuredAgentTokens.some((token) => token.length < 32) || new Set(configuredAgentTokens).size !== 4) {
  throw new Error("Set four unique agent tokens, each at least 32 characters long, before starting the gateway.");
}
if (adminToken.length < 32) throw new Error("Set HOME_ADMIN_TOKEN to a unique secret of at least 32 characters.");

function safeEqual(a, b) {
  const left = Buffer.from(String(a));
  const right = Buffer.from(String(b));
  return left.length === right.length && timingSafeEqual(left, right);
}

function getBearer(req) {
  const match = /^Bearer\s+(.+)$/i.exec(req.headers.authorization || "");
  return match?.[1] || "";
}

function identifyAgent(token) {
  for (const name of Object.keys(AGENTS)) {
    const expected = process.env[`${name}_TOKEN`];
    if (expected && safeEqual(token, expected)) return name;
  }
  return null;
}

async function readJson(req) {
  let body = "";
  for await (const chunk of req) {
    body += chunk;
    if (Buffer.byteLength(body) > MAX_BODY) {
      const error = new Error("Request body too large.");
      error.status = 413;
      throw error;
    }
  }
  try {
    return body ? JSON.parse(body) : {};
  } catch {
    const error = new Error("Request body must be valid JSON.");
    error.status = 400;
    throw error;
  }
}

function send(res, status, payload) {
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
    "x-content-type-options": "nosniff"
  });
  res.end(JSON.stringify(payload));
}

function audit(entry) {
  // Replace with durable encrypted storage before production deployment.
  // Never log OAuth tokens, agent bearer tokens, or full camera/media payloads.
  process.stdout.write(JSON.stringify({
    timestamp: new Date().toISOString(),
    ...entry
  }) + "\n");
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);

  if (req.method === "GET" && url.pathname === "/health") {
    const status = await googleConnectionStatus().catch(() => ({ connected: false }));
    return send(res, 200, { ok: true, service: "ai-playground-home-gateway", googleHomeConnected: status.connected });
  }

  if (req.method === "GET" && url.pathname === "/oauth/google/start") {
    if (!safeEqual(getBearer(req), adminToken)) return send(res, 401, { error: "Administrator authentication required." });
    try {
      res.writeHead(302, { location: createGoogleAuthorizationUrl(), "cache-control": "no-store" });
      return res.end();
    } catch (error) { return send(res, 500, { error: error.message }); }
  }

  if (req.method === "GET" && url.pathname === "/oauth/google/callback") {
    const code = url.searchParams.get("code");
    const state = url.searchParams.get("state");
    const oauthError = url.searchParams.get("error");
    if (oauthError) return send(res, 400, { error: `Google authorization failed: ${oauthError}` });
    if (!code || !state) return send(res, 400, { error: "Missing OAuth code or state." });
    try {
      await completeGoogleAuthorization(code, state);
      resetMcpSession();
      res.writeHead(200, { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" });
      return res.end("<!doctype html><html><body><h2>Google Home connected</h2><p>You can close this window and return to AI Playground.</p></body></html>");
    } catch (error) { return send(res, 400, { error: error.message }); }
  }

  if (req.method === "GET" && url.pathname === "/v1/home/connection") {
    if (!safeEqual(getBearer(req), adminToken)) return send(res, 401, { error: "Administrator authentication required." });
    return send(res, 200, await googleConnectionStatus());
  }

  if (req.method === "POST" && url.pathname === "/v1/home/disconnect") {
    if (!safeEqual(getBearer(req), adminToken)) return send(res, 401, { error: "Administrator authentication required." });
    await disconnectGoogle();
    resetMcpSession();
    return send(res, 200, { disconnected: true });
  }

  if (req.method === "GET" && url.pathname === "/v1/agents/me") {
    const agent = identifyAgent(getBearer(req));
    if (!agent) return send(res, 401, { error: "Unauthorized agent." });
    return send(res, 200, { agent, permissions: AGENTS[agent] });
  }

  if (req.method === "GET" && url.pathname === "/v1/home/tools") {
    const agent = identifyAgent(getBearer(req));
    if (!agent) return send(res, 401, { error: "Unauthorized agent." });
    try {
      const tools = await listGoogleHomeTools();
      const policy = AGENTS[agent];
      const visible = tools.filter((tool) => {
        if (["list_homes", "list_home_resources", "list_home_states"].includes(tool.name)) return true;
        if (tool.name === "list_home_history") return policy.history;
        if (tool.name === "run_home_actions") return policy.control.length > 0;
        return false;
      });
      return send(res, 200, { tools: visible });
    } catch (error) { return send(res, error.status || 502, { error: error.message }); }
  }

  if (req.method !== "POST" || url.pathname !== "/v1/home/tools/call") {
    return send(res, 404, { error: "Not found." });
  }

  const agent = identifyAgent(getBearer(req));
  if (!agent) return send(res, 401, { error: "Unauthorized agent." });

  try {
    const body = await readJson(req);
    const toolName = body.tool;
    const args = body.arguments ?? {};
    if (typeof toolName !== "string" || !toolName) {
      return send(res, 400, { error: "A tool name is required." });
    }

    const decision = authorizeToolCall(agent, toolName, args);
    if (!decision.allowed) {
      audit({ agent, tool: toolName, allowed: false, reason: decision.reason });
      return send(res, 403, { error: "Permission denied.", reason: decision.reason });
    }

    const requestId = randomUUID();
    const result = await callGoogleHomeTool(toolName, args);
    audit({
      agent,
      tool: toolName,
      allowed: true,
      operation: decision.operation,
      resource: decision.resource,
      requestId
    });
    return send(res, 200, { requestId, result });
  } catch (error) {
    const status = error.status || 502;
    audit({ agent, allowed: false, error: error.message });
    return send(res, status, { error: error.message });
  }
});

server.listen(PORT, HOST, () => {
  process.stdout.write(`AI Playground Home Gateway listening on port ${PORT}\n`);
});
