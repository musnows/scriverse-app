import { AsyncLocalStorage } from 'node:async_hooks';
import { createCipheriv, createDecipheriv, randomBytes, scryptSync } from 'node:crypto';
import { promises as dns } from 'node:dns';
import { createServer } from 'node:http';
import { DatabaseSync } from 'node:sqlite';
import { gzipSync, gunzipSync } from 'node:zlib';
import { mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const results = [];
const record = (name, passed, detail = '') => results.push({ name, passed, detail });

const workdir = mkdtempSync(join(tmpdir(), 'scriverse-runtime-probe-'));
const dbPath = join(workdir, 'probe.db');

try {
  const db = new DatabaseSync(dbPath);
  db.exec('PRAGMA foreign_keys=ON; PRAGMA journal_mode=WAL;');
  db.exec('CREATE TABLE parent(id INTEGER PRIMARY KEY); CREATE TABLE child(id INTEGER PRIMARY KEY, parent_id INTEGER REFERENCES parent(id));');
  db.exec('BEGIN IMMEDIATE; INSERT INTO parent(id) VALUES (1); INSERT INTO child(id, parent_id) VALUES (1, 1); COMMIT;');
  const integrity = db.prepare('PRAGMA integrity_check').get();
  const foreignKeys = db.prepare('PRAGMA foreign_key_check').all();
  record('node:sqlite', integrity.integrity_check === 'ok' && foreignKeys.length === 0, JSON.stringify(integrity));
  db.close();
} catch (error) {
  record('node:sqlite', false, String(error));
}

try {
  const key = scryptSync('probe-password', 'probe-salt', 32);
  const iv = randomBytes(12);
  const cipher = createCipheriv('aes-256-gcm', key, iv);
  const encrypted = Buffer.concat([cipher.update('scriverse'), cipher.final()]);
  const tag = cipher.getAuthTag();
  const decipher = createDecipheriv('aes-256-gcm', key, iv);
  decipher.setAuthTag(tag);
  const plaintext = Buffer.concat([decipher.update(encrypted), decipher.final()]).toString('utf8');
  record('crypto-scrypt-aes-gcm', plaintext === 'scriverse');
} catch (error) {
  record('crypto-scrypt-aes-gcm', false, String(error));
}

try {
  const storage = new AsyncLocalStorage();
  const observed = await storage.run({ requestId: 'probe' }, async () => {
    await Promise.resolve();
    return storage.getStore()?.requestId;
  });
  record('async-local-storage', observed === 'probe');
} catch (error) {
  record('async-local-storage', false, String(error));
}

try {
  const payload = Buffer.alloc(2_500_000, 65);
  const restored = gunzipSync(gzipSync(payload));
  record('zlib-large-payload', restored.equals(payload), `${restored.length}`);
} catch (error) {
  record('zlib-large-payload', false, String(error));
}

try {
  const server = createServer((request, response) => {
    if (request.url === '/events') {
      response.writeHead(200, { 'content-type': 'text/event-stream' });
      response.end('event: ready\ndata: {"ok":true}\n\n');
      return;
    }
    response.writeHead(200, { 'content-type': 'application/json' });
    response.end('{"data":{"ok":true}}');
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const address = server.address();
  const response = await fetch(`http://127.0.0.1:${address.port}/events`);
  const text = await response.text();
  server.close();
  record('http-sse-loopback', text.includes('event: ready') && text.includes('"ok":true'));
} catch (error) {
  record('http-sse-loopback', false, String(error));
}

try {
  const addresses = await dns.lookup('localhost', { all: true });
  record('dns', addresses.length > 0, JSON.stringify(addresses));
} catch (error) {
  record('dns', false, String(error));
}

writeFileSync(join(workdir, 'probe-result.json'), JSON.stringify(results, null, 2));
const passed = results.every((result) => result.passed);
console.log(JSON.stringify({ passed, results }));
process.exitCode = passed ? 0 : 1;
