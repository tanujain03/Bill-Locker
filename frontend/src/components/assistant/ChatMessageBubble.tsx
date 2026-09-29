import { FileText, Package, Sparkles } from 'lucide-react';
import { Link } from 'react-router';
import type { ChatMessage, ChatReference } from '@/types';
import { RichText } from './RichText';

function ReferenceChip({ reference }: { reference: ChatReference }) {
  const isProduct = reference.type === 'PRODUCT';
  const Icon = isProduct ? Package : FileText;
  return (
    <Link
      to={isProduct ? `/products/${reference.id}` : `/documents/${reference.id}`}
      className="inline-flex max-w-full items-center gap-2 rounded-xl border border-slate-200 bg-white px-3 py-2 text-left shadow-xs transition-colors hover:border-brand-300 hover:bg-brand-50/50"
    >
      <Icon className="size-4 shrink-0 text-brand-600" aria-hidden />
      <span className="min-w-0">
        <span className="block truncate text-xs font-semibold text-slate-800">{reference.title}</span>
        {reference.subtitle && <span className="block truncate text-[11px] text-slate-500">{reference.subtitle}</span>}
      </span>
    </Link>
  );
}

export function ChatMessageBubble({ message }: { message: ChatMessage }) {
  if (message.role === 'USER') {
    return (
      <div className="flex justify-end">
        <p className="max-w-[85%] rounded-2xl rounded-br-md bg-brand-600 px-4 py-2.5 text-sm whitespace-pre-wrap text-white shadow-sm">
          <span className="sr-only">You said: </span>
          {message.content}
        </p>
      </div>
    );
  }

  return (
    <div className="flex gap-3">
      <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-brand-600 text-white" aria-hidden>
        <Sparkles className="size-4" />
      </span>
      <div className="min-w-0 flex-1 sm:max-w-[90%]">
        <div className="rounded-2xl rounded-tl-md border border-slate-200 bg-white px-4 py-3 text-sm leading-relaxed text-slate-700 shadow-card">
          <span className="sr-only">Assistant: </span>
          <RichText text={message.content} />
        </div>
        {message.references.length > 0 && (
          <div className="mt-2">
            <p className="mb-1.5 text-xs font-medium text-slate-500">Based on</p>
            <div className="flex flex-wrap gap-2">
              {message.references.map((reference) => (
                <ReferenceChip key={`${reference.type}-${reference.id}`} reference={reference} />
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export function TypingIndicator() {
  return (
    <div className="flex gap-3" role="status">
      <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-brand-600 text-white" aria-hidden>
        <Sparkles className="size-4" />
      </span>
      <div className="flex items-center gap-2 rounded-2xl rounded-tl-md border border-slate-200 bg-white px-4 py-3 shadow-card">
        <span className="flex gap-1" aria-hidden>
          {[0, 150, 300].map((delay) => (
            <span
              key={delay}
              className="size-1.5 animate-bounce rounded-full bg-brand-400"
              style={{ animationDelay: `${delay}ms` }}
            />
          ))}
        </span>
        <span className="text-xs text-slate-500">Searching your documents…</span>
      </div>
    </div>
  );
}
