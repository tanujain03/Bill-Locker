import { api } from './api';

/** One chat bubble. The backend reads the chat so far to understand follow-ups ("and last month?"). */
export type ChatMessage = { role: 'user' | 'assistant'; text: string };

/** The answer, and the SQL that was run for it (null when no query ran). */
export type ChatAnswer = { answer: string; sql: string | null };

/** The backend accepts at most 10 messages, so only the newest ones are sent. */
const MAX_MESSAGES = 10;

export const askAssistant = (messages: ChatMessage[]) =>
  api<ChatAnswer>('/assistant/chat', { body: { messages: messages.slice(-MAX_MESSAGES) } });
