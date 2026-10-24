export const APP_FONT_SIZES = Object.freeze([80, 90, 100, 110, 120]);
export const DEFAULT_APP_FONT_SIZE = 90;
export const APP_APPEARANCE_KEY = "scriverse.mobile.appearance.v1";

export function normalizeAppFontSize(value) {
  return APP_FONT_SIZES.includes(value) ? value : DEFAULT_APP_FONT_SIZE;
}

export function readAppFontSize(storage) {
  try { return normalizeAppFontSize(JSON.parse(storage.getItem(APP_APPEARANCE_KEY) ?? "null")?.fontSize); }
  catch { return DEFAULT_APP_FONT_SIZE; }
}

export function writeAppFontSize(storage, value) {
  if (!APP_FONT_SIZES.includes(value)) throw new RangeError("Unsupported app font size");
  storage.setItem(APP_APPEARANCE_KEY, JSON.stringify({ fontSize: value }));
  return value;
}

export function scaleTypographyValue(value, property = "font-size") {
  const source = String(value).trim();
  if (!source || source.includes("--app-font-scale") || /^(?:inherit|initial|unset|revert(?:-layer)?|normal|0)$/u.test(source)) return source;
  if (/^-?(?:\d*\.)?\d+(?:em|%)?$/u.test(source)) return source;
  if (property === "line-height" && !/(?:px|rem|pt|pc|cm|mm|in|vw|vh|vmin|vmax|dvh|svh|lvh)\b/u.test(source)) return source;
  if (!/(?:px|rem|pt|pc|cm|mm|in|vw|vh|vmin|vmax|dvh|svh|lvh)\b|var\(/u.test(source)) return source;
  return `calc((${source}) * var(--app-font-scale, 0.9))`;
}
