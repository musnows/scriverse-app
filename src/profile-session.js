export function cachedProfileUser(record, origin) {
  if (!record || record.origin !== origin || typeof record.user?.userId !== "string") return null;
  return record.user;
}
