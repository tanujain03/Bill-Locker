import { Check, Copy } from 'lucide-react';
import { useEffect, useState, type ReactNode } from 'react';
import { buttonClass } from '../Button';

/**
 * Puts `text` on the clipboard. The icon turns into a tick for 1.5 s so the user
 * sees it worked. With `children` it is a labelled button ("Copy all"); without,
 * an icon button that sits inside an input.
 */
export function CopyButton({ text, label, children }: { text: string; label: string; children?: ReactNode }) {
  const [state, setState] = useState<'idle' | 'copied' | 'failed'>('idle');

  useEffect(() => {
    if (state === 'idle') return;
    const timer = setTimeout(() => setState('idle'), 1500);
    return () => clearTimeout(timer);
  }, [state]);

  async function copy() {
    try {
      await navigator.clipboard.writeText(text);
      setState('copied');
    } catch {
      setState('failed'); // e.g. the browser blocked clipboard access
    }
  }

  const Icon = state === 'copied' ? Check : Copy;
  const title = state === 'copied' ? 'Copied' : state === 'failed' ? 'Couldn’t copy' : label;
  return (
    <button
      type="button"
      onClick={copy}
      disabled={!text.trim()}
      aria-label={children ? undefined : label}
      title={title}
      className={
        children
          ? buttonClass()
          : 'rounded-md p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700 disabled:invisible'
      }
    >
      <Icon className={`size-4 ${state === 'copied' ? 'text-emerald-600' : ''}`} aria-hidden />
      {children && (state === 'copied' ? 'Copied' : state === 'failed' ? 'Couldn’t copy' : children)}
    </button>
  );
}
