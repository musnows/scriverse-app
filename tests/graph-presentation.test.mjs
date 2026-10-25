import test from "node:test";
import assert from "node:assert/strict";
import { createGraphPresentationSession } from "../src/graph-presentation-session.js";

test("ordinary graph previews never change device orientation", async () => {
  const calls = [];
  const session = createGraphPresentationSession(async (value) => calls.push(value));
  await session.opened("map"); await session.opened("galaxy"); await session.closed("galaxy"); await session.closed("map");
  assert.deepEqual(calls, []);
  assert.deepEqual(session.state(), { active: false, map: false, galaxy: false });
  assert.throws(() => session.enter("other"), RangeError);
});

test("a nested galaxy keeps the map landscape session until the map closes", async () => {
  const calls = [];
  const session = createGraphPresentationSession(async (value) => calls.push(value));
  await session.enter("map"); await session.opened("galaxy");
  assert.deepEqual(session.state(), { active: true, map: true, galaxy: true });
  await session.closed("galaxy");
  assert.deepEqual(session.state(), { active: true, map: true, galaxy: false });
  assert.deepEqual(calls, [true]);
  await session.closed("map");
  assert.deepEqual(calls, [true, false]);
});

test("explicit exit restores ordinary viewing across nested graph dialogs", async () => {
  const calls = [];
  const session = createGraphPresentationSession(async (value) => calls.push(value));
  await session.enter("map"); await session.opened("galaxy"); await session.exit();
  await session.closed("galaxy"); await session.closed("map");
  assert.deepEqual(session.state(), { active: false, map: false, galaxy: false });
  assert.deepEqual(calls, [true, false]);
  await session.enter("galaxy"); await session.closed("galaxy");
  assert.deepEqual(calls, [true, false, true, false]);
});

test("rapid enter exit and reentry are applied in order without resaving native orientation", async () => {
  const calls = [];
  let release;
  const first = new Promise((resolve) => { release = resolve; });
  const session = createGraphPresentationSession(async (value) => { calls.push(value); if (calls.length === 1) await first; });
  void session.enter("map"); void session.enter("map"); void session.exit(); void session.enter("map");
  release(); await session.idle();
  assert.deepEqual(calls, [true, false, true]);
  assert.equal(session.state().map, true);
});

test("a failed native entry clears fullscreen UI and allows a fresh retry", async () => {
  const calls = []; const errors = [];
  const failure = new Error("Native presentation unavailable");
  const session = createGraphPresentationSession(async (value) => { calls.push(value); if (calls.length === 1) throw failure; }, { onError: (error) => errors.push(error) });
  await session.enter("galaxy");
  assert.deepEqual(calls, [true, false]);
  assert.deepEqual(session.state(), { active: false, map: false, galaxy: false });
  assert.deepEqual(errors, [failure]);
  await session.enter("galaxy");
  assert.equal(session.state().galaxy, true);
  assert.deepEqual(calls, [true, false, true]);
});

test("an old failed request does not undo a newer exit", async () => {
  const calls = []; const errors = [];
  const session = createGraphPresentationSession(async (value) => { calls.push(value); if (value) throw new Error("Stale native failure"); }, { onError: (error) => errors.push(error) });
  void session.enter("map"); await session.exit();
  assert.deepEqual(calls, [true, false]);
  assert.equal(session.state().active, false);
  assert.deepEqual(errors, []);
});
