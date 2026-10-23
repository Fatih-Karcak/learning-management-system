import test from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, readFileSync, rmSync, statSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { spawnSync } from "node:child_process";
import { prepareStaging } from "./prepare-staging.mjs";

test("staging generates private independent secrets, passes validation and never overwrites existing settings", () => {
  const dir = mkdtempSync(join(tmpdir(), "lms-staging-settings-"));
  try {
    const path = join(dir, ".env.staging");
    prepareStaging(path);
    const contents = readFileSync(path, "utf8");
    const vars = Object.fromEntries(
      contents
        .split("\n")
        .filter((l) => l.includes("="))
        .map((l) => l.split("=")),
    );
    assert.equal(statSync(path).mode & 0o777, 0o600);
    const keys = [
      "DB_PASSWORD",
      "JWT_SECRET",
      "INTERNAL_API_KEY",
      "MFA_ENCRYPTION_KEY",
      "MINIO_SECRET_KEY",
      "APP_BOOTSTRAP_ADMIN_PASSWORD",
    ];
    assert.equal(new Set(keys.map((k) => vars[k])).size, keys.length);
    const result = spawnSync(
      process.execPath,
      [new URL("./check-config.mjs", import.meta.url).pathname, path],
      { encoding: "utf8" },
    );
    assert.equal(result.status, 0);
    for (const key of keys)
      assert.ok(!(result.stdout + result.stderr).includes(vars[key]));
    assert.throws(() => prepareStaging(path), { code: "EEXIST" });
    assert.equal(readFileSync(path, "utf8"), contents);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});
