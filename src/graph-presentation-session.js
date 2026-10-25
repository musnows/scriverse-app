const kinds = new Set(["map", "galaxy"]);

export function createGraphPresentationSession(apply, { onChange = () => undefined, onError = () => undefined } = {}) {
  const targets = new Set();
  let requested = false;
  let revision = 0;
  let pending = Promise.resolve();
  const state = () => Object.freeze({ active: targets.size > 0, map: targets.has("map"), galaxy: targets.has("galaxy") });
  const validate = (kind) => { if (!kinds.has(kind)) throw new RangeError("Unsupported graph presentation"); };
  function sync() {
    const value = state();
    onChange(value);
    if (requested === value.active) return pending;
    requested = value.active;
    const current = ++revision;
    pending = pending.then(() => apply(value.active)).catch(async (error) => {
      if (current !== revision) return;
      targets.clear(); requested = false;
      onChange(state());
      await Promise.resolve().then(() => apply(false)).catch(() => undefined);
      onError(error);
    });
    return pending;
  }
  return {
    state,
    enter(kind) { validate(kind); targets.add(kind); return sync(); },
    opened(kind) { validate(kind); if (kind === "galaxy" && targets.has("map")) targets.add(kind); return sync(); },
    closed(kind) { validate(kind); targets.delete(kind); return sync(); },
    exit() { targets.clear(); return sync(); },
    idle() { return pending; }
  };
}
