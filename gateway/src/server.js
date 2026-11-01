import http from "node:http";
import { randomUUID, timingSafeEqual } from "node:crypto";
import { AGENTS, authorizeToolCall } from "./policy.js";

const PORT = Number(process.env.PORT || 8787);
const UPSTREAM_URL = process.env.GOOGLE_HOME_MCP_URL || "https://home.googleapis.com/mcp";
const UPSTREAM_TOKEN = process.env.GOOGLE_HOME_ACCESS_TOKEN || "";
const MAX_BODY = 256 * 1024;

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

async function proxyToolCall(toolName, args, requestId) {
  if (!UPSTREAM_TOKEN) {
    const error = new Error("Google Home is not connected. Configure GOOGLE_HOME_ACCESS_TOKEN on the server.");
    error.status = 503;
    throw error;
  }

  const upstream = await fetch(UPSTREAM_URL, {
    method: "POST",
    headers: {
      "authorization": `Bearer ${UPSTREAM_TOKEN}`,
      "content-type": "application/json",
      "accept": "application/json, text/event-stream"
    },
    body: JSON.stringify({
      jsonrpc: "2.0",
      id: requestId,
      method: "tools/call",
      params: { name: toolName, arguments: args }
    }),
    signal: AbortSignal.timeout(30000)
  });

  const contentType = upstream.headers.get("content-type") || "";
  const text = await upstream.text();
  if (!upstream.ok) {
    const error = new Error(`Google Home MCP returned HTTP ${upstream.status}.`);
    error.status = 502;
    throw error;
  }

  // Support JSON-RPC JSON responses and the SSE data frames used by MCP.
  if (contentType.includes("text/event-stream")) {
    const frames = text.split(/\r?\n\r?\n/);
    for (const frame of frames) {
      const data = frame.split(/\r?\n/).find((line) => line.startsWith("data:"));
      if (data) {
        const raw = data.slice(5).trim();
        try { return JSON.parse(raw); } catch { /* try next SSE frame */ }
      }
    }
    throw new Error("Google Home MCP returned no parseable JSON-RPC result.");
  }

  try {
    return JSON.parse(text);
  } catch {
    throw new Error("Google Home MCP returned an unexpected response.");
  }
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);

  if (req.method === "GET" && url.pathname === "/health") {
    return send(res, 200, {
      ok: true,
      service: "ai-playground-home-gateway",
      googleHomeConfigured: Boolean(UPSTREAM_TOKEN)
    });
  }

  if (req.method === "GET" && url.pathname === "/v1/agents/me") {
    const agent = identifyAgent(getBearer(req));
    if (!agent) return send(res, 401, { error: "Unauthorized agent." });
    return send(res, 200, { agent, permissions: AGENTS[agent] });
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
    const result = await proxyToolCall(toolName, args, requestId);
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

server.listen(PORT, "0.0.0.0", () => {
  process.stdout.write(`AI Playground Home Gateway listening on port ${PORT}\n`);
});
