import test from "node:test";
import assert from "node:assert/strict";
import { parseSse } from "../src/mcp-client.js";

test("parses multiple JSON-RPC messages from SSE data frames", () => {
  const messages = parseSse(
    'event: message\ndata: {"jsonrpc":"2.0","id":1,"result":{"ok":true}}\n\n' +
    'event: message\ndata: {"jsonrpc":"2.0","id":2,"result":{"tools":[]}}\n\n'
  );
  assert.equal(messages.length, 2);
  assert.deepEqual(messages[0].result, { ok: true });
  assert.deepEqual(messages[1].result, { tools: [] });
});

test("ignores DONE and non-JSON SSE events", () => {
  const messages = parseSse("data: ping\n\ndata: [DONE]\n\ndata: {\"jsonrpc\":\"2.0\",\"id\":3,\"result\":{}}\n\n");
  assert.equal(messages.length, 1);
  assert.equal(messages[0].id, 3);
});

test("joins multi-line data fields before parsing", () => {
  const messages = parseSse('data: {"jsonrpc":"2.0",\ndata: "id":4,"result":{}}\n\n');
  assert.equal(messages.length, 1);
  assert.equal(messages[0].id, 4);
});
