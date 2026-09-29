import { Fragment, type ReactNode } from 'react';

type Block = { type: 'p'; text: string } | { type: 'ul' | 'ol'; items: string[] };

/** Splits plain text into paragraphs and bullet/numbered lists. */
function parseBlocks(text: string): Block[] {
  const blocks: Block[] = [];
  let paragraph: string[] = [];
  const flush = () => {
    if (paragraph.length > 0) {
      blocks.push({ type: 'p', text: paragraph.join(' ') });
      paragraph = [];
    }
  };

  for (const raw of text.replace(/\r\n/g, '\n').split('\n')) {
    const line = raw.trim();
    const bullet = /^[-*•]\s+(.*)$/.exec(line);
    const numbered = /^\d+[.)]\s+(.*)$/.exec(line);
    const match = bullet ?? numbered;
    if (match) {
      flush();
      const type = bullet ? 'ul' : 'ol';
      const last = blocks[blocks.length - 1];
      if (last && last.type !== 'p' && last.type === type) last.items.push(match[1]);
      else blocks.push({ type, items: [match[1]] });
    } else if (line === '') {
      flush();
    } else {
      paragraph.push(line);
    }
  }
  flush();
  return blocks;
}

/** Supports **bold** only; everything else stays literal text (no HTML is ever injected). */
function renderInline(text: string): ReactNode[] {
  return text
    .split(/(\*\*[^*]+\*\*)/g)
    .filter(Boolean)
    .map((part, index) =>
      part.startsWith('**') && part.endsWith('**') && part.length > 4 ? (
        <strong key={index} className="font-semibold text-slate-900">
          {part.slice(2, -2)}
        </strong>
      ) : (
        <Fragment key={index}>{part}</Fragment>
      ),
    );
}

/** Safe, minimal formatting for assistant answers. */
export function RichText({ text }: { text: string }) {
  return (
    <div className="space-y-2">
      {parseBlocks(text).map((block, index) => {
        if (block.type === 'p') return <p key={index}>{renderInline(block.text)}</p>;
        const List = block.type;
        return (
          <List key={index} className={block.type === 'ul' ? 'list-disc space-y-1 pl-5' : 'list-decimal space-y-1 pl-5'}>
            {block.items.map((item, itemIndex) => (
              <li key={itemIndex}>{renderInline(item)}</li>
            ))}
          </List>
        );
      })}
    </div>
  );
}
