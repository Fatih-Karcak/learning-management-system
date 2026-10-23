import { useRef, useState } from 'react';
import ReactMarkdown from 'react-markdown';

// Markdown stays compatible with saved courses; raw HTML is never executed in the preview.
export default function RichTextEditor({
  value,
  defaultValue = '',
  onChange,
  id,
  name,
  disabled,
  required,
  rows = 7,
  maxLength = 100000,
  'aria-describedby': describedBy,
}: {
  value?: string;
  defaultValue?: string;
  onChange?: (value: string) => void;
  id?: string;
  name?: string;
  disabled?: boolean;
  required?: boolean;
  rows?: number;
  maxLength?: number;
  'aria-describedby'?: string;
}) {
  const ref = useRef<HTMLTextAreaElement>(null);
  const [local, setLocal] = useState(defaultValue);
  const [preview, setPreview] = useState(false);
  const [error, setError] = useState('');
  const content = value ?? local;
  function update(next: string) {
    setLocal(next);
    onChange?.(next);
  }
  function format(before: string, after = '', placeholder = 'metin', lines = false) {
    const input = ref.current;
    if (!input || input.disabled || input.matches(':disabled')) return;
    let start = input.selectionStart,
      end = input.selectionEnd;
    if (lines) {
      start = content.lastIndexOf('\n', start - 1) + 1;
      const nextBreak = content.indexOf('\n', end);
      end = nextBreak < 0 ? content.length : nextBreak;
    }
    const selected = content.slice(start, end) || placeholder;
    const inserted = lines
      ? selected
          .split('\n')
          .map((line, index) => `${before === '1. ' ? `${index + 1}. ` : before}${line}`)
          .join('\n')
      : before + selected + after;
    const next = content.slice(0, start) + inserted + content.slice(end);
    if (next.length > maxLength) {
      setError('Metin uzunluk sınırını aşıyor.');
      return;
    }
    update(next);
    setError('');
    requestAnimationFrame(() => {
      input.focus();
      input.setSelectionRange(start, start + inserted.length);
    });
  }
  const tools = [
    ['Kalın', '**', '**'],
    ['İtalik', '*', '*'],
    ['Başlık', '## ', ''],
    ['Madde işaretli liste', '- ', ''],
    ['Numaralı liste', '1. ', ''],
    ['Alıntı', '> ', ''],
    ['Kod bloğu', '\n```\n', '\n```\n'],
  ];
  return (
    <div className="rich-editor">
      <div role="toolbar" aria-label="Metin biçimlendirme" className="form-actions rich-toolbar">
        {tools.map(([label, before, after]) => (
          <button
            key={label}
            type="button"
            className="button secondary small"
            disabled={disabled}
            onMouseDown={(e) => e.preventDefault()}
            onClick={() =>
              format(
                before,
                after,
                'metin',
                ['Başlık', 'Madde işaretli liste', 'Numaralı liste', 'Alıntı'].includes(label),
              )
            }
          >
            {label}
          </button>
        ))}
        <button
          type="button"
          className="button secondary small"
          disabled={disabled}
          onMouseDown={(e) => e.preventDefault()}
          onClick={() => {
            const url = window.prompt('HTTPS bağlantısı', 'https://');
            if (!url) return;
            try {
              const parsed = new URL(url);
              if (
                parsed.protocol !== 'https:' ||
                parsed.username ||
                parsed.password ||
                /[\s<>]/.test(url)
              )
                throw new Error();
              format(
                '[',
                `](${url.replaceAll('(', '%28').replaceAll(')', '%29')})`,
                'bağlantı metni',
              );
            } catch {
              setError('Geçerli bir HTTPS bağlantısı yazın.');
            }
          }}
        >
          Bağlantı
        </button>
        <button
          type="button"
          className="button secondary small"
          aria-pressed={preview}
          onClick={() => setPreview(!preview)}
        >
          Metni ön izle
        </button>
      </div>
      <textarea
        ref={ref}
        id={id}
        name={name}
        value={content}
        required={required}
        rows={rows}
        disabled={disabled}
        maxLength={maxLength}
        aria-describedby={describedBy}
        onChange={(e) => update(e.target.value)}
      />
      {error && (
        <p role="alert" className="notice error">
          {error}
        </p>
      )}
      {preview && (
        <div className="rich-preview markdown" role="region" aria-label="Biçimlendirilmiş metin">
          <ReactMarkdown skipHtml>{content || 'Ön izlemek için metin yazın.'}</ReactMarkdown>
        </div>
      )}
    </div>
  );
}
