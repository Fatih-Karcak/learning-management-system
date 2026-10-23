import { test } from 'node:test';
import assert from 'node:assert/strict';
import { copyFileSync, mkdtempSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { initializeLocalEnvironment } from './setup-local-env.mjs';

const backendDirectory = resolve(dirname(fileURLToPath(import.meta.url)), '..');
function fixture(t) {
  const directory = mkdtempSync(join(tmpdir(), 'lms-local-env-'));
  copyFileSync(join(backendDirectory, '.env.example'), join(directory, '.env.example'));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  return directory;
}

test('creates private shared settings with separate unpredictable keys', (t) => {
  const directory = fixture(t);
  const result = initializeLocalEnvironment({ directory, environment: {} });
  const contents = readFileSync(result.path, 'utf8');
  const jwt = contents.match(/^JWT_SECRET=(.+)$/m)?.[1];
  const internal = contents.match(/^INTERNAL_API_KEY=(.+)$/m)?.[1];
  assert.equal(result.created, true);
  assert.match(jwt, /^[a-f0-9]{64}$/);
  assert.match(internal, /^[a-f0-9]{64}$/);
  assert.notEqual(jwt, internal);
  if (process.platform !== 'win32') assert.equal(statSync(result.path).mode & 0o777, 0o600);
});

test('rerunning setup preserves existing settings and secrets exactly', (t) => {
  const directory = fixture(t);
  const first = initializeLocalEnvironment({ directory, environment: {} });
  const original = readFileSync(first.path);
  const second = initializeLocalEnvironment({ directory, environment: { JWT_SECRET: 'different' } });
  assert.equal(second.created, false);
  assert.deepEqual(readFileSync(second.path), original);
});

test('reuses configured shared keys rather than replacing existing bindings', (t) => {
  const directory = fixture(t);
  const environment = { JWT_SECRET: 'a'.repeat(64), INTERNAL_API_KEY: 'b'.repeat(64) };
  const result = initializeLocalEnvironment({ directory, environment });
  const contents = readFileSync(result.path, 'utf8');
  assert.ok(contents.includes(`JWT_SECRET=${environment.JWT_SECRET}\n`));
  assert.ok(contents.includes(`INTERNAL_API_KEY=${environment.INTERNAL_API_KEY}\n`));
});

test('rejects short existing keys without creating a partial configuration', (t) => {
  const directory = fixture(t);
  assert.throws(() => initializeLocalEnvironment({ directory, environment: { INTERNAL_API_KEY: 'short' } }), /INTERNAL_API_KEY en az 32/);
  assert.throws(() => readFileSync(join(directory, '.env')), { code: 'ENOENT' });
});

test('supports Windows CRLF checkouts without leaving empty keys', (t) => {
  const directory = fixture(t);
  const template = join(directory, '.env.example');
  writeFileSync(template, readFileSync(template, 'utf8').replace(/\r?\n/g, '\r\n'));
  const result = initializeLocalEnvironment({ directory, environment: {} });
  const content = readFileSync(result.path, 'utf8');
  assert.match(content, /^JWT_SECRET=[a-f0-9]{64}\r$/m);
  assert.match(content, /^INTERNAL_API_KEY=[a-f0-9]{64}\r$/m);
});
