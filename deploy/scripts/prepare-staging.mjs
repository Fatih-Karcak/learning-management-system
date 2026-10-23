import { randomBytes } from "node:crypto";
import { writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";

export function prepareStaging(path) {
  const secret = (bytes = 32) => randomBytes(bytes).toString("hex");
  const vars = {
    APP_DOMAIN: "academy.localhost",
    S3_DOMAIN: "files.localhost",
    TLS_EMAIL: "ops@academy.localhost",
    DB_PASSWORD: secret(),
    JWT_SECRET: secret(),
    INTERNAL_API_KEY: secret(),
    MFA_ENCRYPTION_KEY: secret(),
    MINIO_ACCESS_KEY: secret(9),
    MINIO_SECRET_KEY: secret(),
    MAIL_HOST: "mailhog",
    MAIL_PORT: "1025",
    MAIL_USERNAME: "",
    MAIL_PASSWORD: "",
    MAIL_FROM: "academy@academy.localhost",
    MAIL_SMTP_AUTH: "false",
    MAIL_SMTP_STARTTLS: "false",
    APP_BOOTSTRAP_ADMIN_EMAIL: "admin@academy.localhost",
    APP_BOOTSTRAP_ADMIN_PASSWORD: secret(24),
    RELEASE_TAG: "staging",
  };
  writeFileSync(
    path,
    "# Local staging only. Do not publish this file.\n" +
      Object.entries(vars)
        .map(([key, value]) => `${key}=${value}`)
        .join("\n") +
      "\n",
    { flag: "wx", mode: 0o600 },
  );
}
if (
  process.argv[1] &&
  pathToFileURL(resolve(process.argv[1])).href === import.meta.url
) {
  try {
    const path = process.argv[2] || "deploy/.env.staging";
    prepareStaging(path);
    console.log(
      `Deneme ayarları oluşturuldu: ${path}. Yönetici bilgileri yalnızca bu özel dosyada saklanır.`,
    );
  } catch (error) {
    console.error(
      error.code === "EEXIST"
        ? "Ayar dosyası zaten var; mevcut anahtarlar değiştirilmedi."
        : "Deneme ayar dosyası oluşturulamadı.",
    );
    process.exitCode = 1;
  }
}
