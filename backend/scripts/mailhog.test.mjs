import { test } from 'node:test';
import assert from 'node:assert/strict';
import { decodeMailhogMessage } from './mailhog.mjs';
test('7-bit English email keeps hexadecimal token prefixes intact', () => {
  const html='<a href="https://app.example/verify-email?token=abcdef123">Verify</a>';
  assert.equal(decodeMailhogMessage({MIME:{Parts:[{Headers:{'Content-Type':['text/html'],'Content-Transfer-Encoding':['7bit']},Body:html}]}}),html);
});
test('nested quoted-printable MIME correctly decodes UTF-8 and verification URLs', () => {
  const result=decodeMailhogMessage({MIME:{Parts:[{Headers:{'Content-Type':['multipart/related']},MIME:{Parts:[{Headers:{'Content-Type':['text/html'],'Content-Transfer-Encoding':['quoted-printable']},Body:'=C3=87a=C4=9Fr=C4=B1\r\n<a href=3D"https://app.example/verify-email?token=3Dabcdef123">Verify</a>'}]}}]}});
  assert.ok(result.includes('Çağrı'));assert.ok(result.includes('?token=abcdef123'));
});
