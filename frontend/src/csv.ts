export function parseEmailImport(text: string): string[] {
  if (text.length > 1024 * 1024) throw new Error('The import file must be smaller than 1 MiB.');
  const rows: string[][] = [];
  let row: string[] = [],
    field = '',
    quoted = false;
  for (let index = 0; index < text.length; index++) {
    const char = text[index];
    if (char === '"') {
      if (quoted && text[index + 1] === '"') {
        field += '"';
        index++;
      } else quoted = !quoted;
    } else if (!quoted && [',', ';', '\n'].includes(char)) {
      row.push(field.trim());
      field = '';
      if (char === '\n') {
        if (row.some(Boolean)) rows.push(row);
        row = [];
      }
    } else if (char !== '\r') field += char;
  }
  if (quoted) throw new Error('The CSV file contains an unclosed quoted field.');
  row.push(field.trim());
  if (row.some(Boolean)) rows.push(row);
  if (!rows.length) throw new Error('Add at least one learner email address.');
  const header = rows[0].findIndex(
    (value) => value.replace(/^\uFEFF/, '').toLowerCase() === 'email',
  );
  const values = header >= 0 ? rows.slice(1).map((value) => value[header] || '') : rows.flat();
  const emails = Array.from(
    new Set(values.filter(Boolean).map((value) => value.trim().toLowerCase())),
  );
  if (!emails.length || emails.length > 200)
    throw new Error('Import between 1 and 200 learners at a time.');
  if (emails.some((value) => value.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)))
    throw new Error('Use valid email addresses or a CSV file with an email column.');
  return emails;
}
