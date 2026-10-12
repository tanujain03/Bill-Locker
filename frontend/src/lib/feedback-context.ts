import { createContext, useContext } from 'react';

/**
 * Two app-wide helpers that any page can call (FeedbackProvider shows them):
 * - toast("Saved"): a short message in the corner that goes away by itself.
 * - await confirm({...}): a styled "Are you sure?" dialog → true (go ahead) / false.
 *   It replaces the browser's window.confirm ("localhost says…").
 */
export type ToastTone = 'success' | 'error' | 'info';

export type ConfirmOptions = {
  title: string;
  message?: string;
  /** The button that goes ahead, e.g. "Delete". Say what happens, not "OK". */
  confirmLabel: string;
  /** Red button: for things that can't be undone or lose work. */
  danger?: boolean;
};

export type Feedback = {
  toast: (text: string, tone?: ToastTone) => void;
  confirm: (options: ConfirmOptions) => Promise<boolean>;
};

export const FeedbackContext = createContext<Feedback | null>(null);

export function useFeedback(): Feedback {
  const feedback = useContext(FeedbackContext);
  if (!feedback) throw new Error('useFeedback must be used inside <FeedbackProvider>');
  return feedback;
}
