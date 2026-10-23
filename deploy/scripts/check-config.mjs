import fs from "node:fs";
const path = process.argv[2] || "deploy/.env";
const vars = Object.fromEntries(
  fs
    .readFileSync(path, "utf8")
    .split(/\r?\n/)
    .filter((l) => l.trim() && !l.trim().startsWith("#"))
    .map((l) => {
      const i = l.indexOf("=");
      return [l.slice(0, i).trim(), l.slice(i + 1).trim()];
    }),
);
const failures = [];
for (const [key, min] of Object.entries({
  DB_PASSWORD: 24,
  JWT_SECRET: 64,
  INTERNAL_API_KEY: 48,
  MFA_ENCRYPTION_KEY: 64,
  MINIO_ACCESS_KEY: 12,
  MINIO_SECRET_KEY: 32,
})) {
  if (
    !vars[key] ||
    vars[key].length < min ||
    /replace|placeholder|example/i.test(vars[key])
  )
    failures.push(`${key}: en az ${min} karakterlik benzersiz değer gerekli`);
}
for (const key of ["APP_DOMAIN", "S3_DOMAIN"])
  if (
    !vars[key] ||
    !/^[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/.test(vars[key]) ||
    /example\./.test(vars[key])
  )
    failures.push(`${key}: gerçek alan adı gerekli`);
if (vars.APP_DOMAIN === vars.S3_DOMAIN)
  failures.push("Uygulama ve S3 alan adları farklı olmalı");
for (const key of ["TLS_EMAIL", "MAIL_HOST", "MAIL_FROM"])
  if (!vars[key] || /example\./.test(vars[key]))
    failures.push(`${key}: gerçek değer gerekli`);
for (const key of ["MAIL_SMTP_AUTH", "MAIL_SMTP_STARTTLS"])
  if (vars[key] && !["true", "false"].includes(vars[key]))
    failures.push(`${key}: true veya false olmalı`);
const mailPort = Number(vars.MAIL_PORT || 587);
if (!Number.isInteger(mailPort) || mailPort < 1 || mailPort > 65535)
  failures.push("MAIL_PORT: 1–65535 arasında tam sayı olmalı");
if (vars.MAIL_SMTP_AUTH !== "false")
  for (const key of ["MAIL_USERNAME", "MAIL_PASSWORD"])
    if (!vars[key] || /replace|placeholder|example/i.test(vars[key]))
      failures.push(
        `${key}: SMTP kimlik doğrulaması için gerçek değer gerekli`,
      );
if (!!vars.APP_BOOTSTRAP_ADMIN_EMAIL !== !!vars.APP_BOOTSTRAP_ADMIN_PASSWORD)
  failures.push("İlk yönetici e-postası ve parolası birlikte tanımlanmalı");
if (
  vars.APP_BOOTSTRAP_ADMIN_PASSWORD &&
  vars.APP_BOOTSTRAP_ADMIN_PASSWORD.length < 12
)
  failures.push("Yönetici parolası en az 12 karakter olmalı");
if (failures.length) {
  console.error(failures.join("\n"));
  process.exitCode = 1;
} else
  console.log(
    "Dağıtım yapılandırması biçim kontrolünden geçti; bağlantı ve DNS kontrolleri ayrıca çalıştırılmalı.",
  );
