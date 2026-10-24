import { describe, it, expect } from 'vitest';
import { parseEmailImport } from './csv';
describe('learner CSV imports', () => {
  it('supports quoted names, an email header, BOM and duplicate addresses', () => {
    expect(
      parseEmailImport(
        '\uFEFFname,email\r\n"Learner, One",ONE+training@example.test\r\nOther,one+training@example.test',
      ),
    ).toEqual(['one+training@example.test']);
  });
  it('accepts pasted email lists with semicolons', () => {
    expect(parseEmailImport('one@example.test;two@example.test\nthree@example.test')).toHaveLength(
      3,
    );
  });
  it('rejects malformed, oversized and empty imports before making requests', () => {
    for (const value of [
      '',
      'not an email',
      '"unfinished',
      'a'.repeat(1024 * 1024 + 1),
      Array.from({ length: 201 }, (_, i) => `user${i}@example.test`).join('\n'),
    ])
      expect(() => parseEmailImport(value)).toThrow();
  });
});
