import { FileSearch, RotateCw, ShieldAlert, ShieldCheck, Sparkles, SquarePen, Wallet } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router';
import { ChatComposer } from '@/components/assistant/ChatComposer';
import { ChatMessageBubble, TypingIndicator } from '@/components/assistant/ChatMessageBubble';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { PageHeader } from '@/components/ui/PageHeader';
import { useChat } from '@/hooks/useAi';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { getErrorMessage } from '@/lib/api-client';
import { useAuth } from '@/lib/auth-context';
import { sessionCache } from '@/lib/session-cache';
import type { ChatMessage } from '@/types';

const SUGGESTIONS = [
  { icon: ShieldAlert, text: 'Which warranties expire soon?' },
  { icon: FileSearch, text: 'Find my laptop bill' },
  { icon: Wallet, text: 'Show my most expensive purchase' },
  { icon: ShieldCheck, text: 'Which products are under warranty?' },
];

const MORE_QUESTIONS = [
  'When did I buy my refrigerator?',
  'What is the serial number of my TV?',
  'What products did I buy from Amazon?',
  'Which warranties expire within 90 days?',
];

interface Conversation {
  sessionId: string | null;
  messages: ChatMessage[];
}

const EMPTY: Conversation = { sessionId: null, messages: [] };

export function AssistantPage() {
  useDocumentTitle('AI Assistant');
  const { user } = useAuth();
  const storageKey = sessionCache.key(user?.id ?? 'anonymous', 'assistant');
  const [conversation, setConversation] = useState<Conversation>(() => sessionCache.read<Conversation>(storageKey) ?? EMPTY);
  const [failure, setFailure] = useState<{ text: string; error: string } | null>(null);
  const { mutateAsync, isPending } = useChat();
  const [params, setParams] = useSearchParams();
  const endRef = useRef<HTMLDivElement>(null);

  useEffect(() => sessionCache.write(storageKey, conversation), [storageKey, conversation]);

  useEffect(() => {
    endRef.current?.scrollIntoView?.({ behavior: 'smooth', block: 'end' });
  }, [conversation.messages.length, isPending, failure]);

  const ask = useCallback(
    async (text: string, { appendQuestion = true } = {}) => {
      setFailure(null);
      if (appendQuestion) {
        const question: ChatMessage = {
          id: `local-${Date.now()}`,
          role: 'USER',
          content: text,
          references: [],
          createdAt: new Date().toISOString(),
        };
        setConversation((current) => ({ ...current, messages: [...current.messages, question] }));
      }
      try {
        const response = await mutateAsync({ message: text, sessionId: conversation.sessionId });
        setConversation((current) => ({ sessionId: response.sessionId, messages: [...current.messages, response.message] }));
      } catch (error) {
        setFailure({ text, error: getErrorMessage(error, 'The assistant could not answer right now.') });
      }
    },
    [mutateAsync, conversation.sessionId],
  );

  // "Ask AI" links elsewhere open the assistant with ?q=… — send it once.
  const handledQuery = useRef(false);
  useEffect(() => {
    const question = params.get('q')?.trim();
    if (!question || handledQuery.current) return;
    handledQuery.current = true;
    setParams({}, { replace: true });
    void ask(question);
  }, [params, setParams, ask]);

  function newChat() {
    setConversation(EMPTY);
    setFailure(null);
  }

  const empty = conversation.messages.length === 0 && !isPending;

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader
        title="AI Assistant"
        description="Ask about your purchases, warranties and bills. Answers come only from your own documents."
        actions={
          conversation.messages.length > 0 && (
            <Button variant="secondary" onClick={newChat} leftIcon={<SquarePen className="size-4" aria-hidden />}>
              New chat
            </Button>
          )
        }
      />

      {empty ? (
        <Card className="px-5 py-8 sm:px-8 sm:py-10">
          <div className="text-center">
            <span className="mx-auto flex size-12 items-center justify-center rounded-2xl bg-brand-600 text-white shadow-sm">
              <Sparkles className="size-6" aria-hidden />
            </span>
            <h2 className="mt-4 text-lg font-semibold text-slate-900">What would you like to know?</h2>
            <p className="mt-1 text-sm text-slate-500">
              I search your products, warranties and the text of your bills to answer — and I’ll say so if I can’t find
              something.
            </p>
          </div>
          <div className="mt-8 grid gap-3 sm:grid-cols-2">
            {SUGGESTIONS.map(({ icon: Icon, text }) => (
              <button
                key={text}
                type="button"
                onClick={() => void ask(text)}
                className="flex items-center gap-3 rounded-xl border border-slate-200 bg-white px-4 py-3 text-left text-sm font-medium text-slate-700 transition-colors hover:border-brand-300 hover:bg-brand-50/50"
              >
                <Icon className="size-4 shrink-0 text-brand-600" aria-hidden />
                {text}
              </button>
            ))}
          </div>
          <div className="mt-4 flex flex-wrap justify-center gap-2">
            {MORE_QUESTIONS.map((text) => (
              <button
                key={text}
                type="button"
                onClick={() => void ask(text)}
                className="rounded-full bg-slate-100 px-3 py-1.5 text-xs font-medium text-slate-600 transition-colors hover:bg-slate-200"
              >
                {text}
              </button>
            ))}
          </div>
        </Card>
      ) : (
        <div className="space-y-5 pb-4" role="log" aria-live="polite" aria-label="Conversation">
          {conversation.messages.map((message) => (
            <ChatMessageBubble key={message.id} message={message} />
          ))}
          {isPending && <TypingIndicator />}
          {failure && (
            <div className="ml-11 flex flex-wrap items-center gap-3 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700" role="alert">
              {failure.error}
              <Button
                size="sm"
                variant="secondary"
                onClick={() => void ask(failure.text, { appendQuestion: false })}
                leftIcon={<RotateCw className="size-3.5" aria-hidden />}
              >
                Retry
              </Button>
            </div>
          )}
        </div>
      )}
      <div ref={endRef} />

      <div className="sticky bottom-0 -mx-4 mt-6 bg-gradient-to-t from-slate-50 from-70% to-slate-50/0 px-4 pt-6 pb-4 sm:mx-0 sm:px-0">
        <ChatComposer onSend={(text) => void ask(text)} disabled={isPending} />
        <p className="mt-2 text-center text-xs text-slate-500">
          AI can make mistakes. Answers link to the products and documents they use — check important details.
        </p>
      </div>
    </div>
  );
}
