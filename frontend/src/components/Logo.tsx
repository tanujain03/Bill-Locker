import { ReceiptText } from 'lucide-react';

export function Logo({ size = 'md' }: { size?: 'md' | 'lg' }) {
  const large = size === 'lg';
  return (
    <span className={`inline-flex shrink-0 items-center font-semibold whitespace-nowrap text-slate-900 ${large ? 'gap-3 text-2xl' : 'gap-2'}`}>
      <span className={`grid place-items-center bg-brand-600 text-white ${large ? 'size-12 rounded-xl' : 'size-8 rounded-lg'}`}>
        <ReceiptText className={large ? 'size-7' : 'size-4.5'} aria-hidden />
      </span>
      Bill Locker
    </span>
  );
}
