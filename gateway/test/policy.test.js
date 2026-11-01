import test from "node:test";
import assert from "node:assert/strict";
import { authorizeToolCall } from "../src/policy.js";

const readStates = { home_id: "test-home" };

test("all agents can read current device states", () => {
  for (const agent of ["ARIA", "NOVA", "ATLAS", "ECHO"]) {
    assert.equal(authorizeToolCall(agent, "list_home_states", readStates).allowed, true);
  }
});

test("ATLAS is read-only", () => {
  const result = authorizeToolCall("ATLAS", "run_home_actions", {
    actions: [{ device_type: "light", command: "turn_on" }]
  });
  assert.equal(result.allowed, false);
});

test("ECHO can control speakers but not lights", () => {
  assert.equal(authorizeToolCall("ECHO", "run_home_actions", {
    actions: [{ device_type: "speaker", command: "set_volume" }]
  }).allowed, true);
  assert.equal(authorizeToolCall("ECHO", "run_home_actions", {
    actions: [{ device_type: "light", command: "turn_on" }]
  }).allowed, false);
});

test("NOVA can control plugs; ARIA cannot", () => {
  const args = { actions: [{ device_type: "plug", command: "turn_off" }] };
  assert.equal(authorizeToolCall("NOVA", "run_home_actions", args).allowed, true);
  assert.equal(authorizeToolCall("ARIA", "run_home_actions", args).allowed, false);
});

test("all agents are denied lock, garage, alarm and camera operations", () => {
  for (const agent of ["ARIA", "NOVA", "ATLAS", "ECHO"]) {
    for (const term of ["unlock_door", "garage", "alarm", "camera"]) {
      const result = authorizeToolCall(agent, "run_home_actions", {
        actions: [{ device_type: term, command: "activate" }]
      });
      assert.equal(result.allowed, false, `${agent} unexpectedly allowed ${term}`);
    }
  }
});

test("unknown tools and unknown agents fail closed", () => {
  assert.equal(authorizeToolCall("ARIA", "delete_home").allowed, false);
  assert.equal(authorizeToolCall("UNKNOWN", "list_home_states").allowed, false);
});

test("ambiguous control payloads fail closed", () => {
  assert.equal(authorizeToolCall("ARIA", "run_home_actions", { foo: "bar" }).allowed, false);
});
