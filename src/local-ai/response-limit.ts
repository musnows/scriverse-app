export function resolveAiResponseMaxBytes(): number { return 64 * 1024 * 1024; }

export function isAiResponseByteLimitExceeded(receivedBytes: number, maximumBytes: number | null): boolean {
  return maximumBytes !== null && receivedBytes > maximumBytes;
}
