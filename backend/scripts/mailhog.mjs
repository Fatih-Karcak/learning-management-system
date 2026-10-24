function header(headers, name) {
  const key = Object.keys(headers || {}).find(key => key.toLowerCase() === name.toLowerCase());
  const value = key ? headers[key] : '';
  return Array.isArray(value) ? value.join('; ') : String(value || '');
}
function decode(body, encoding) {
  if (/base64/i.test(encoding)) return Buffer.from(body.replace(/\s/g, ''), 'base64').toString('utf8');
  if (!/quoted-printable/i.test(encoding)) return body;
  const text = body.replace(/=\r?\n/g, '');
  const bytes = [];
  for (let index = 0; index < text.length; index++) {
    if (text[index] === '=' && /^[0-9a-f]{2}$/i.test(text.slice(index + 1, index + 3))) {
      bytes.push(parseInt(text.slice(index + 1, index + 3), 16)); index += 2;
    } else bytes.push(...Buffer.from(text[index], 'utf8'));
  }
  return Buffer.from(bytes).toString('utf8');
}
export function decodeMailhogMessage(message) {
  function find(part) {
    if (!part) return undefined;
    if (/text\/html/i.test(header(part.Headers, 'Content-Type')))
      return decode(part.Body || '', header(part.Headers, 'Content-Transfer-Encoding'));
    for (const child of part.Parts || part.MIME?.Parts || []) {
      const result = find(child); if (result !== undefined) return result;
    }
    return undefined;
  }
  return find(message.MIME) ?? decode(message.Content?.Body || '', header(message.Content?.Headers, 'Content-Transfer-Encoding'));
}
