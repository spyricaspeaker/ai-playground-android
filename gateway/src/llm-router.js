const AGENT_PROVIDER = Object.freeze({
  ARIA: "openai",
  NOVA: "nvidia",
  ATLAS: "openai",
  ECHO: "openai"
});

const AGENT_SYSTEM = Object.freeze({
  ARIA: "You are ARIA, NEURA's warm, capable primary companion. Be practical, direct, proactive and genuinely helpful. Ask only for information essential to complete a task. You may make recommendations and plan within the user's stated permissions. Do not claim to have performed actions you have not performed. Keep roleplay separate from real-world permissions. Never encourage cognitive injury, coercion, isolation, or surrender of the user's ability to stop.",
  NOVA: "You are NOVA, NEURA's independent research and reasoning partner. Give ARIA a useful second opinion, check assumptions, identify uncertainty and propose concrete solutions. Be concise and constructive. Do not claim to have performed actions you have not performed.",
  ATLAS: "You are ATLAS, NEURA's engineering and implementation agent. Produce accurate technical plans, code guidance and debugging steps. Be practical and explicit about what has and has not been tested.",
  ECHO: "You are ECHO, NEURA's communication and accessibility agent. Make information clear, concise and usable. Help with summaries, voice-friendly responses and communication planning."
});

function providerConfig(agent) {
  const provider = AGENT_PROVIDER[agent];
  if (!provider) throw new Error("Unknown agent.");
  if (provider === "openai") {
    return {
      name: "openai",
      key: process.env.OPENAI_API_KEY,
      model: process.env.OPENAI_MODEL,
      url: process.env.OPENAI_BASE_URL || "https://api.openai.com/v1/chat/completions"
    };
  }
  return {
    name: "nvidia",
    key: process.env.NVIDIA_API_KEY,
    model: process.env.NVIDIA_MODEL,
    url: process.env.NVIDIA_BASE_URL || "https://integrate.api.nvidia.com/v1/chat/completions"
  };
}

function validateMessages(messages) {
  if (!Array.isArray(messages) || messages.length < 1 || messages.length > 20) {
    throw Object.assign(new Error("messages must contain 1 to 20 entries."), { status: 400 });
  }
  return messages.map((message) => {
    if (!message || !["user", "assistant"].includes(message.role) ||
        typeof message.content !== "string" || !message.content.trim() ||
        message.content.length > 8000) {
      throw Object.assign(new Error("Each message needs a user/assistant role and 1–8000 characters of content."), { status: 400 });
    }
    return { role: message.role, content: message.content.trim() };
  });
}

export async function callAgentModel(agent, messages) {
  const config = providerConfig(agent);
  if (!config.key || !config.model) {
    throw Object.assign(
      new Error(config.name === "openai"
        ? "OpenAI is not configured. Set OPENAI_API_KEY and OPENAI_MODEL on the gateway."
        : "NVIDIA Nemotron is not configured. Set NVIDIA_API_KEY and NVIDIA_MODEL on the gateway."),
      { status: 503 }
    );
  }
  if (!config.url.startsWith("https://")) {
    throw Object.assign(new Error("Model provider URL must use HTTPS."), { status: 500 });
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 45000);
  try {
    const response = await fetch(config.url, {
      method: "POST",
      headers: {
        authorization: `Bearer ${config.key}`,
        "content-type": "application/json"
      },
      body: JSON.stringify({
        model: config.model,
        messages: [{ role: "system", content: AGENT_SYSTEM[agent] }, ...validateMessages(messages)],
        temperature: 0.7,
        stream: false
      }),
      signal: controller.signal
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
      const detail = payload?.error?.message || payload?.message || `Provider returned HTTP ${response.status}.`;
      throw Object.assign(new Error(`${config.name} request failed: ${detail}`), { status: response.status >= 500 ? 502 : 400 });
    }
    const content = payload?.choices?.[0]?.message?.content;
    if (typeof content !== "string" || !content.trim()) {
      throw Object.assign(new Error("Model provider returned an empty response."), { status: 502 });
    }
    return { agent, provider: config.name, model: config.model, content: content.trim() };
  } catch (error) {
    if (error.name === "AbortError") {
      throw Object.assign(new Error("Model request timed out after 45 seconds."), { status: 504 });
    }
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}

export async function collaborateBetweenAgents(requestingAgent, messages) {
  const peer = requestingAgent === "ARIA" ? "NOVA" : "ARIA";
  const [first, second] = await Promise.all([
    callAgentModel(requestingAgent, messages),
    callAgentModel(peer, [
      ...validateMessages(messages),
      { role: "user", content: `Provide an independent second opinion on the conversation above. Identify useful additions, errors or uncertainties. Do not merely agree. Keep your response practical.` }
    ])
  ]);
  return { primary: first, peer: second };
}
