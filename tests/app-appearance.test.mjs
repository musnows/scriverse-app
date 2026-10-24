import test from "node:test";
import assert from "node:assert/strict";
import { APP_APPEARANCE_KEY, DEFAULT_APP_FONT_SIZE, normalizeAppFontSize, readAppFontSize, writeAppFontSize, scaleTypographyValue } from "../src/app-appearance-settings.js";

test("global app text defaults one step below the original and rejects invalid stored sizes", () => {
  assert.equal(DEFAULT_APP_FONT_SIZE, 90);
  for (const value of [null, undefined, "120", -100, 0, 89, 500, {}, []]) assert.equal(normalizeAppFontSize(value), 90);
  for (const value of [80, 90, 100, 110, 120]) assert.equal(normalizeAppFontSize(value), value);
  for (const value of [null, "invalid json", '{"fontSize":500}', '{"fontSize":"120"}']) assert.equal(readAppFontSize({ getItem: () => value }), 90);
});

test("global typography survives reload and storage errors never silently claim a save", () => {
  const values = new Map();
  const storage = { getItem: (key) => values.get(key) ?? null, setItem: (key, value) => values.set(key, value) };
  assert.equal(writeAppFontSize(storage, 120), 120);
  assert.equal(readAppFontSize(storage), 120);
  assert.deepEqual(JSON.parse(values.get(APP_APPEARANCE_KEY)), { fontSize: 120 });
  assert.throws(() => writeAppFontSize(storage, 999), RangeError);
  assert.throws(() => writeAppFontSize({ setItem() { throw new Error("Storage is unavailable"); } }, 90), /Storage is unavailable/u);
  assert.equal(readAppFontSize({ getItem() { throw new Error("Storage is unavailable"); } }), 90);
});

test("global scaling covers fixed and variable typography without double scaling relative text", () => {
  for (const value of ["16px", "1.25rem", "clamp(24px, 4vw, 36px)", "calc(12px * var(--ui-font-scale))", "var(--editor-font-size)"]) {
    const scaled = scaleTypographyValue(value);
    assert.ok(scaled.includes("var(--app-font-scale, 0.9)"), value);
    assert.equal(scaleTypographyValue(scaled), scaled);
  }
  for (const value of ["", "inherit", "normal", "initial", "unset", "revert", "1.5", "1.2em", "90%", "0"]) assert.equal(scaleTypographyValue(value), value);
  for (const value of ["var(--editor-line-height)", "var(--reader-line-height, 1.9)"]) assert.equal(scaleTypographyValue(value, "line-height"), value);
  assert.match(scaleTypographyValue("22px", "line-height"), /--app-font-scale/u);
});
