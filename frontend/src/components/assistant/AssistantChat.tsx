import { LoaderCircle, MessageCircle, RotateCcw, Send, X } from 'lucide-react';
import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent, type ReactNode } from 'react';
import { Link } from 'react-router';
import { errorMessage } from '../../lib/api';
import { askAssistant, type ChatMessage } from '../../lib/assistant';
import { Button } from '../Button';

/** A bubble in the panel: the message, and whether it is an error. */
type Bubble = ChatMessage & { error?: boolean };

const EXAMPLES = ['How much did I spend this month?', 'Which warranties expire soon?', 'Show my watch bill'];

/**
 * The floating "Ask to your locker" chat. AppLayout renders it once, so the chat stays while
 * you move between pages; a refresh or sign-out clears it (nothing is saved).
 * The backend answers from the signed-in user's own data, read-only.
 */
export function AssistantChat() {
  const [open, setOpen] = useState(false);
  const [bubbles, setBubbles] = useState<Bubble[]>([]);
  const [draft, setDraft] = useState('');
  const [busy, setBusy] = useState(false);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const endRef = useRef<HTMLDivElement>(null);

  function close() {
    setOpen(false);
    buttonRef.current?.focus();
  }

  // Esc closes the panel.
  useEffect(() => {
    if (!open) return;
    const onKey = (e: globalThis.KeyboardEvent) => e.key === 'Escape' && close();
    window.addEventListener('keydown', onKey);
    inputRef.current?.focus();
    return () => window.removeEventListener('keydown', onKey);
  }, [open]);

  // Keep the newest message in view. Braces: newer browsers return a Promise from
  // scrollIntoView, and an effect must return nothing (or a clean-up function).
  useEffect(() => {
    endRef.current?.scrollIntoView({ block: 'end' });
  }, [bubbles, busy, open]);

  async function ask(question: string) {
    const text = question.trim();
    if (!text || busy) return;
    // Failed answers aren't real answers, so they aren't sent back as chat history.
    const history: ChatMessage[] = [
      ...bubbles.filter((b) => !b.error).map(({ role, text }) => ({ role, text })),
      { role: 'user', text },
    ];
    setBubbles((current) => [...current, { role: 'user', text }]);
    setDraft('');
    setBusy(true);
    try {
      const { answer } = await askAssistant(history);
      setBubbles((current) => [...current, { role: 'assistant', text: answer }]);
    } catch (error) {
      setBubbles((current) => [...current, { role: 'assistant', text: errorMessage(error), error: true }]);
    } finally {
      setBusy(false);
    }
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    void ask(draft);
  }

  // Enter sends; Shift+Enter makes a new line.
  function onKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      void ask(draft);
    }
  }

  return (
    <>
      <button
        ref={buttonRef}
        type="button"
        onClick={() => (open ? close() : setOpen(true))}
        aria-label={open ? 'Close assistant' : 'Open assistant'}
        aria-expanded={open}
        className="fixed right-4 bottom-20 z-40 flex size-14 items-center justify-center rounded-full bg-brand-600 text-white shadow-lg transition hover:bg-brand-700 sm:right-6 sm:bottom-6"
      >
        {open ? <X className="size-6" aria-hidden /> : <MessageCircle className="size-6" aria-hidden />}
      </button>

      {open && (
        <section
          role="dialog"
          aria-label="Ask to your locker"
          className="fixed inset-0 z-50 flex flex-col bg-white sm:inset-auto sm:right-6 sm:bottom-24 sm:h-[560px] sm:max-h-[calc(100vh-8rem)] sm:w-[380px] sm:rounded-2xl sm:border sm:border-slate-200 sm:shadow-2xl"
        >
          <header className="flex items-center gap-2 border-b border-slate-200 px-4 py-3">
            <MessageCircle className="size-5 text-brand-600" aria-hidden />
            <h2 className="flex-1 font-semibold text-slate-900">Ask to your locker</h2>
            {bubbles.length > 0 && (
              <Button variant="ghost" size="sm" icon={RotateCcw} onClick={() => setBubbles([])} disabled={busy}>
                New chat
              </Button>
            )}
            <button
              type="button"
              onClick={close}
              aria-label="Close assistant"
              className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100"
            >
              <X className="size-5" aria-hidden />
            </button>
          </header>

          <div className="flex-1 space-y-3 overflow-y-auto px-4 py-4" aria-live="polite">
            {bubbles.length === 0 && (
              <div className="space-y-3 text-sm text-slate-600">
                <p>Ask anything about your bills, products, warranties or Gmail imports. I can only look things up.</p>
                <div className="flex flex-col items-start gap-2">
                  {EXAMPLES.map((example) => (
                    <button
                      key={example}
                      type="button"
                      onClick={() => void ask(example)}
                      className="rounded-full border border-slate-200 px-3 py-1.5 text-left text-slate-700 hover:border-brand-300 hover:bg-brand-50"
                    >
                      {example}
                    </button>
                  ))}
                </div>
              </div>
            )}
            {bubbles.map((bubble, i) => (
              <MessageBubble key={i} bubble={bubble} onLinkClick={() => window.innerWidth < 640 && setOpen(false)} />
            ))}
            {busy && (
              <div className="flex items-center gap-2 text-sm text-slate-500">
                <LoaderCircle className="size-4 animate-spin" aria-hidden /> Looking it up…
              </div>
            )}
            <div ref={endRef} />
          </div>

          <form onSubmit={onSubmit} className="flex items-end gap-2 border-t border-slate-200 p-3">
            <textarea
              ref={inputRef}
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={onKeyDown}
              rows={1}
              maxLength={2000}
              placeholder="Ask about your bills…"
              aria-label="Your question"
              className="max-h-32 min-h-10 flex-1 resize-none rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
            />
            <Button type="submit" variant="primary" icon={Send} disabled={!draft.trim()} busy={busy}>
              <span className="sr-only">Send</span>
            </Button>
          </form>
        </section>
      )}
    </>
  );
}

function MessageBubble({ bubble, onLinkClick }: { bubble: Bubble; onLinkClick: () => void }) {
  if (bubble.role === 'user') {
    return (
      <div className="ml-auto max-w-[85%] rounded-2xl rounded-br-sm bg-brand-600 px-3 py-2 text-sm whitespace-pre-wrap text-white">
        {bubble.text}
      </div>
    );
  }
  return (
    <div
      className={`max-w-[90%] rounded-2xl rounded-bl-sm px-3 py-2 text-sm whitespace-pre-wrap ${
        bubble.error ? 'bg-rose-50 text-rose-800' : 'bg-slate-100 text-slate-800'
      }`}
    >
      <AnswerText text={bubble.text} onLinkClick={onLinkClick} />
    </div>
  );
}

const BILL_PATH = String.raw`\/documents\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}`;
/** "[label](/documents/<id>)" (the AI sometimes writes Markdown links anyway) or a bare "/documents/<id>". */
const DOCUMENT_LINK = new RegExp(String.raw`\[([^\]\n]{1,80})\]\((${BILL_PATH})\)|(${BILL_PATH})`, 'g');

/** The answer as plain text (never HTML); a bill's path becomes a link to that bill. */
function AnswerText({ text, onLinkClick }: { text: string; onLinkClick: () => void }) {
  const parts: ReactNode[] = [];
  let last = 0;
  for (const match of text.matchAll(DOCUMENT_LINK)) {
    const [, label, labelledPath, barePath] = match;
    parts.push(text.slice(last, match.index));
    parts.push(
      <Link
        key={match.index}
        to={labelledPath ?? barePath}
        onClick={onLinkClick}
        className="font-medium text-brand-700 underline"
      >
        {label ?? 'Open bill'}
      </Link>,
    );
    last = match.index + match[0].length;
  }
  parts.push(text.slice(last));
  return <>{parts}</>;
}
