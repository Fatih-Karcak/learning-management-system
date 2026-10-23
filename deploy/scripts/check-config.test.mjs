import assert from "node:assert/strict";
import { randomBytes } from "node:crypto";
import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { spawnSync } from "node:child_process";
import test from "node:test";

function check(overrides = {}) {
  const vars = {
    APP_DOMAIN: "academy.test",
    S3_DOMAIN: "files.academy.test",
    TLS_EMAIL: "ops@academy.test",
    DB_PASSWORD: randomBytes(24).toString("hex"),
    JWT_SECRET: randomBytes(32).toString("hex"),
    INTERNAL_API_KEY: randomBytes(24).toString("hex"),
    MFA_ENCRYPTION_KEY: randomBytes(32).toString("hex"),
    MINIO_ACCESS_KEY: randomBytes(12).toString("hex"),
    MINIO_SECRET_KEY: randomBytes(24).toString("hex"),
    MAIL_HOST: "smtp.academy.test",
    MAIL_FROM: "academy@academy.test",
    MAIL_PORT: "587",
    MAIL_USERNAME: "mailer@academy.test",
    MAIL_PASSWORD: randomBytes(24).toString("hex"),
    MAIL_SMTP_AUTH: "true",
    MAIL_SMTP_STARTTLS: "true",
    ...overrides,
  };
  const dir = mkdtempSync(join(tmpdir(), "lms-config-test-"));
  try {
    const path = join(dir, "deploy.env");
    writeFileSync(
      path,
      Object.entries(vars)
        .map(([key, value]) => `${key}=${value}`)
        .join("\n"),
      { mode: 0o600 },
    );
    const result = spawnSync(
      process.execPath,
      [new URL("./check-config.mjs", import.meta.url).pathname, path],
      { encoding: "utf8" },
    );
    assert.ifError(result.error);
    const output = result.stdout + result.stderr;
    for (const [key, value] of Object.entries(vars))
      if (value && /PASSWORD|SECRET|KEY/.test(key))
        assert.ok(!output.includes(value), `Output must not expose ${key}`);
    return { status: result.status, output };
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

test("accepts a complete authenticated SMTP configuration", () => {
  assert.equal(check().status, 0);
});

test("rejects SMTP example credentials without disclosing their values", () => {
  const result = check({
    MAIL_USERNAME: "replace-with-smtp-user",
    MAIL_PASSWORD: "replace-with-smtp-secret",
  });
  assert.equal(result.status, 1);
  assert.match(result.output, /MAIL_USERNAME/);
  assert.match(result.output, /MAIL_PASSWORD/);
});

test("requires SMTP credentials when authentication uses its default", () => {
  const result = check({
    MAIL_SMTP_AUTH: "",
    MAIL_USERNAME: "",
    MAIL_PASSWORD: "",
  });
  assert.equal(result.status, 1);
  assert.match(result.output, /MAIL_USERNAME/);
  assert.match(result.output, /MAIL_PASSWORD/);
});

test("allows an explicitly unauthenticated relay with empty credentials", () => {
  assert.equal(
    check({ MAIL_SMTP_AUTH: "false", MAIL_USERNAME: "", MAIL_PASSWORD: "" })
      .status,
    0,
  );
});

test("rejects invalid SMTP flags and ports", () => {
  const result = check({ MAIL_PORT: "65536", MAIL_SMTP_STARTTLS: "yes" });
  assert.equal(result.status, 1);
  assert.match(result.output, /MAIL_PORT/);
  assert.match(result.output, /MAIL_SMTP_STARTTLS/);
});
