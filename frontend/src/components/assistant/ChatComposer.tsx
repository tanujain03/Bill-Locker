import { SendHorizontal } from 'lucide-react';
import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { Button } from '@/components/ui/Button';

interface ChatComposerProps {
  onSend: (message: string) => void;
  disabled?: boolean;
}

const MAX_LENGTH = 1000;

/** Auto-growing input: Enter sends, Shift+Enter adds a new line. */
export function ChatComposer({ onSend, disabled = false }: ChatComposerProps) {
  const [value, setValue] = useState('');
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    const textarea = textareaRef.current;
    if (!textarea) return;
    textarea.style.height = 'auto';
    textarea.style.height = `${Math.min(textarea.scrollHeight, 160)}px`;
  }, [value]);

  function submit(event?: FormEvent) {
    event?.preventDefault();
    const message = value.trim();
    if (!message || disabled) return;
    onSend(message);
    setValue('');
  }

  function onKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      submit();
    }
  }

  return (
    <form
      onSubmit={submit}
      className="flex items-end gap-2 rounded-2xl border border-slate-300 bg-white p-2 shadow-card focus-within:border-brand-500 focus-within:ring-4 focus-within:ring-brand-500/15"
    >
      <label htmlFor="assistant-input" className="sr-only">
        Ask about your purchases
      </label>
      <textarea
        id="assistant-input"
        ref={textareaRef}
        rows={1}
        value={value}
        maxLength={MAX_LENGTH}
        onChange={(event) => setValue(event.target.value)}
        onKeyDown={onKeyDown}
        placeholder="Ask anything, e.g. “Is my laptop still under warranty?”"
        className="max-h-40 min-h-10 flex-1 resize-none bg-transparent px-2 py-2 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none"
      />
      <Button type="submit" size="icon" disabled={disabled || value.trim() === ''} aria-label="Send message">
        <SendHorizontal className="size-4" aria-hidden />
      </Button>
    </form>
  );
}
