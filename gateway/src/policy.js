/**
 * Agent authorization policy for Google Home MCP.
 *
 * This is an application-side boundary. Google OAuth scopes remain a separate,
 * broader upstream authorization layer.
 */
export const AGENTS = Object.freeze({
  ARIA: Object.freeze({
    role: "personal_home_assistant",
    read: ["homes", "devices", "states"],
    control: ["lights", "thermostats", "speakers"],
    history: false
  }),
  NOVA: Object.freeze({
    role: "home_automation",
    read: ["homes", "devices", "states"],
    control: ["lights", "thermostats", "plugs"],
    history: false
  }),
  ATLAS: Object.freeze({
    role: "home_monitor",
    read: ["homes", "devices", "states", "history"],
    control: [],
    history: true
  }),
  ECHO: Object.freeze({
    role: "voice_communication",
    read: ["homes", "devices", "states"],
    control: ["speakers"],
    history: false
  })
});

const READ_TOOLS = new Map([
  ["list_homes", "homes"],
  ["list_home_resources", "devices"],
  ["list_home_states", "states"],
  ["list_home_history", "history"]
]);

const FORBIDDEN_TERMS = [
  "lock", "unlock", "door", "garage", "alarm", "security_system",
  "camera", "doorbell", "familiar_face", "facial_recognition"
];

const DEVICE_ALIASES = new Map([
  ["light", "lights"], ["lights", "lights"], ["lighting", "lights"],
  ["thermostat", "thermostats"], ["thermostats", "thermostats"],
  ["climate", "thermostats"], ["temperature", "thermostats"],
  ["speaker", "speakers"], ["speakers", "speakers"], ["media", "speakers"],
  ["plug", "plugs"], ["plugs", "plugs"], ["outlet", "plugs"]
]);

const CONTROL_VERBS = new Set([
  "on", "off", "turn_on", "turn_off", "activate", "deactivate",
  "set", "set_temperature", "set_volume", "volume", "play", "pause",
  "stop", "mute", "unmute", "brightness", "set_brightness"
]);

function normalize(value) {
  return String(value ?? "").trim().toLowerCase().replace(/[ -]+/g, "_");
}

function collectStrings(value, out = [], depth = 0) {
  if (depth > 8 || value == null) return out;
  if (typeof value === "string" || typeof value === "number") {
    out.push(String(value));
  } else if (Array.isArray(value)) {
    for (const item of value) collectStrings(item, out, depth + 1);
  } else if (typeof value === "object") {
    for (const [key, item] of Object.entries(value)) {
      out.push(key);
      collectStrings(item, out, depth + 1);
    }
  }
  return out;
}

function containsForbidden(value) {
  return collectStrings(value).some((part) => {
    const token = normalize(part);
    return FORBIDDEN_TERMS.some((term) =>
      token === term || token.includes(term)
    );
  });
}

/**
 * Extract device categories and commands from common MCP argument shapes.
 * Unknown or ambiguous control payloads are rejected rather than guessed.
 */
function inspectControlArguments(args) {
  if (!args || typeof args !== "object" || Array.isArray(args)) {
    return { ok: false, reason: "Control arguments must be an object." };
  }
  if (containsForbidden(args)) {
    return { ok: false, reason: "This device or operation is globally restricted." };
  }

  const serialized = JSON.stringify(args);
  const categories = new Set();
  for (const [alias, category] of DEVICE_ALIASES) {
    const re = new RegExp(`(^|[^a-z])${alias.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}([^a-z]|$)`, "i");
    if (re.test(serialized)) categories.add(category);
  }

  const commands = new Set();
  for (const part of collectStrings(args)) {
    const token = normalize(part);
    if (CONTROL_VERBS.has(token)) commands.add(token);
  }

  if (categories.size !== 1) {
    return { ok: false, reason: "Could not identify exactly one supported device category." };
  }
  if (commands.size === 0) {
    return { ok: false, reason: "Could not identify a supported control command." };
  }
  return { ok: true, category: [...categories][0], commands: [...commands] };
}

export function authorizeToolCall(agentName, toolName, args = {}) {
  const agent = String(agentName ?? "").toUpperCase();
  const policy = AGENTS[agent];
  if (!policy) return deny("Unknown agent identity.");

  if (READ_TOOLS.has(toolName)) {
    const resource = READ_TOOLS.get(toolName);
    if (!policy.read.includes(resource)) return deny(`${agent} cannot read ${resource}.`);
    return { allowed: true, agent, operation: "read", resource };
  }

  if (toolName !== "run_home_actions") {
    return deny(`MCP tool '${toolName}' is not on the gateway allowlist.`);
  }

  if (policy.control.length === 0) {
    return deny(`${agent} has read-only access.`);
  }

  const inspected = inspectControlArguments(args);
  if (!inspected.ok) return deny(inspected.reason);
  if (!policy.control.includes(inspected.category)) {
    return deny(`${agent} cannot control ${inspected.category}.`);
  }

  return {
    allowed: true,
    agent,
    operation: "control",
    resource: inspected.category,
    commands: inspected.commands
  };
}

function deny(reason) {
  return { allowed: false, reason };
}
