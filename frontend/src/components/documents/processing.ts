import type { DocumentSummary } from '@/types';

export type ProcessingStep = 'upload' | 'ocr' | 'ai' | 'ready';

/** Maps the backend processing state to the step shown to the user. */
export function stepForDocument(
  document: Pick<DocumentSummary, 'processingStatus' | 'processingStage'> | undefined,
): ProcessingStep {
  if (!document) return 'upload';
  const inAiStage = document.processingStage === 'EXTRACTION' || document.processingStage === 'INDEXING';
  switch (document.processingStatus) {
    case 'UPLOADED':
      return 'ocr';
    case 'PROCESSING':
    case 'FAILED':
      return inAiStage ? 'ai' : 'ocr';
    default:
      return 'ready';
  }
}
