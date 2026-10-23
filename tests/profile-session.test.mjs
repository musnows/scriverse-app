import test from "node:test";
import assert from "node:assert/strict";
import { cachedProfileUser } from "../src/profile-session.js";

test("changing a profile origin never reuses a cached account from another Server", () => {
  const record = { origin: "https://original.example", user: { userId: crypto.randomUUID(), username: "writer" } };
  assert.equal(cachedProfileUser(record, "https://original.example"), record.user);
  assert.equal(cachedProfileUser(record, "https://different.example"), null);
  assert.equal(cachedProfileUser(record, "http://original.example"), null);
  assert.equal(cachedProfileUser(record, "https://original.example:24400"), null);
  assert.equal(cachedProfileUser({ user: record.user }, "https://original.example"), null);
});
