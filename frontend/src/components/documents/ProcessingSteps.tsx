import { Check, CircleX, LoaderCircle } from 'lucide-react';
import { cn } from '@/lib/cn';
import type { ProcessingStep } from './processing';

const STEPS: { key: ProcessingStep; label: string; active: string }[] = [
  { key: 'upload', label: 'Upload', active: 'Uploading…' },
  { key: 'ocr', label: 'Read', active: 'Processing… reading the document (OCR)' },
  { key: 'ai', label: 'Analyze', active: 'Finding the details…' },
  { key: 'ready', label: 'Review', active: 'Ready for review' },
];

interface ProcessingStepsProps {
  step: ProcessingStep;
  failed?: boolean;
  uploadProgress?: number;
  /** Replaces the default caption, e.g. an error message. */
  caption?: string;
}

export function ProcessingSteps({ step, failed = false, uploadProgress, caption }: ProcessingStepsProps) {
  const currentIndex = STEPS.findIndex((item) => item.key === step);
  const done = step === 'ready' && !failed;
  const current = STEPS[currentIndex];
  const defaultCaption =
    step === 'upload' && uploadProgress !== undefined ? `Uploading… ${uploadProgress}%` : current.active;

  return (
    <div>
      <ol className="flex items-center gap-1.5" aria-label="Processing progress">
        {STEPS.map((item, index) => {
          const complete = index < currentIndex || (done && index === currentIndex);
          const active = index === currentIndex && !done;
          const errored = active && failed;
          return (
            <li key={item.key} className="flex flex-1 flex-col gap-1.5">
              <div
                className={cn(
                  'h-1.5 overflow-hidden rounded-full',
                  complete ? 'bg-brand-600' : errored ? 'bg-rose-200' : active ? 'bg-brand-100' : 'bg-slate-200',
                )}
              >
                {active && !errored && (
                  <div
                    className={cn('h-full rounded-full bg-brand-500', item.key === 'upload' ? '' : 'w-1/2 animate-pulse')}
                    style={item.key === 'upload' ? { width: `${uploadProgress ?? 10}%` } : undefined}
                  />
                )}
              </div>
              <span
                className={cn(
                  'flex items-center gap-1 text-[11px] font-medium',
                  complete ? 'text-brand-700' : errored ? 'text-rose-600' : active ? 'text-slate-800' : 'text-slate-400',
                )}
              >
                {complete && <Check className="size-3" aria-hidden />}
                {errored && <CircleX className="size-3" aria-hidden />}
                {active && !errored && <LoaderCircle className="size-3 animate-spin" aria-hidden />}
                {item.label}
                <span className="sr-only">{complete ? ' (done)' : active ? ' (in progress)' : ''}</span>
              </span>
            </li>
          );
        })}
      </ol>
      <p
        className={cn('mt-2 text-xs', failed ? 'font-medium text-rose-600' : done ? 'font-medium text-emerald-700' : 'text-slate-600')}
        aria-live="polite"
      >
        {caption ?? (failed ? 'Processing failed' : defaultCaption)}
      </p>
    </div>
  );
}
