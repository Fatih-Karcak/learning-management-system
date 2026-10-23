import fs from "node:fs";
import { spawnSync } from "node:child_process";
const file = process.argv[2] || "deploy/.env";
const vars = Object.fromEntries(
  fs
    .readFileSync(file, "utf8")
    .split(/\r?\n/)
    .filter((l) => l.trim() && !l.trim().startsWith("#"))
    .map((l) => {
      const i = l.indexOf("=");
      return [l.slice(0, i).trim(), l.slice(i + 1).trim()];
    }),
);
const required = [
  "MINIO_ACCESS_KEY",
  "MINIO_SECRET_KEY",
  "BACKUP_S3_ENDPOINT",
  "BACKUP_S3_ACCESS_KEY",
  "BACKUP_S3_SECRET_KEY",
  "BACKUP_S3_BUCKET",
];
const missing = required.filter((k) => !vars[k]);
if (missing.length) {
  console.error("Eksik ortam değişkenleri: " + missing.join(", "));
  process.exit(1);
}
const destination = new URL(vars.BACKUP_S3_ENDPOINT);
if (destination.protocol !== "https:")
  throw new Error("Harici yedekleme HTTPS kullanmalı");
destination.username = vars.BACKUP_S3_ACCESS_KEY;
destination.password = vars.BACKUP_S3_SECRET_KEY;
const source = new URL("http://minio:9000");
source.username = vars.MINIO_ACCESS_KEY;
source.password = vars.MINIO_SECRET_KEY;
const childEnv = {
  ...process.env,
  MC_HOST_source: source.toString(),
  MC_HOST_backup: destination.toString(),
};
for (const bucket of [
  "lms-raw-videos",
  "lms-hls",
  "lms-images",
  "lms-certificates",
]) {
  const result = spawnSync(
    "docker",
    [
      "run",
      "--rm",
      "--network",
      vars.LMS_DOCKER_NETWORK || "lms-production_app",
      "--env",
      "MC_HOST_source",
      "--env",
      "MC_HOST_backup",
      "minio/mc:RELEASE.2025-04-16T18-13-26Z",
      "mirror",
      "--overwrite",
      "--preserve",
      `source/${bucket}`,
      `backup/${vars.BACKUP_S3_BUCKET}/${bucket}`,
    ],
    { env: childEnv, stdio: "ignore" },
  );
  if (result.status !== 0) {
    console.error(
      `${bucket}: harici yedekleme başarısız; Docker ağı, bucket ve erişim yetkilerini kontrol edin`,
    );
    process.exit(1);
  }
  console.log(`${bucket}: yedeklendi`);
}
