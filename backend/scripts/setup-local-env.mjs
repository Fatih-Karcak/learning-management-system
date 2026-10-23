import { randomBytes } from 'node:crypto';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const backendDirectory = resolve(dirname(fileURLToPath(import.meta.url)), '..');

function escapeProperty(value) {
  return value
    .replaceAll('\\', '\\\\')
    .replaceAll('\r', '\\r')
    .replaceAll('\n', '\\n')
    .replaceAll('\t', '\\t')
    .replace(/^ +/, (spaces) => '\\ '.repeat(spaces.length));
}

export function initializeLocalEnvironment({
  directory = backendDirectory,
  environment = process.env,
} = {}) {
  const target = join(directory, '.env');
  if (existsSync(target)) return { path: target, created: false };

  let content = readFileSync(join(directory, '.env.example'), 'utf8');
  for (const name of ['JWT_SECRET', 'INTERNAL_API_KEY']) {
    const value = environment[name] || randomBytes(32).toString('hex');
    if (value.length < 32) {
      throw new Error(`${name} en az 32 karakter olmalıdır. Mevcut ortam ayarını düzeltin.`);
    }
    const pattern = new RegExp(`^${name}=$`, 'm');
    if (!pattern.test(content)) throw new Error(`.env.example içinde ${name} alanı bulunamadı.`);
    content = content.replace(pattern, () => `${name}=${escapeProperty(value)}`);
  }

  // Exclusive creation preserves files written by another process; keys never enter stdout.
  writeFileSync(target, content, { encoding: 'utf8', flag: 'wx', mode: 0o600 });
  return { path: target, created: true };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const result = initializeLocalEnvironment();
    console.log(result.created
      ? 'backend/.env oluşturuldu. Ortak anahtarların yüklenmesi için tüm backend servislerini yeniden başlatın.'
      : 'Mevcut backend/.env korundu; hiçbir değer değiştirilmedi.');
  } catch (error) {
    console.error(error instanceof Error ? error.message : 'Yerel yapılandırma oluşturulamadı.');
    process.exitCode = 1;
  }
}
