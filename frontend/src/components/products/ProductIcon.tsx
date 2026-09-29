import { cn } from '@/lib/cn';
import { iconForProduct } from './product-icons';

interface ProductIconProps {
  name?: string | null;
  categorySlug?: string | null;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
}

const SIZES = {
  sm: 'size-8 rounded-lg [&>svg]:size-4',
  md: 'size-10 rounded-xl [&>svg]:size-5',
  lg: 'size-14 rounded-2xl [&>svg]:size-7',
};

export function ProductIcon({ name, categorySlug, size = 'md', className }: ProductIconProps) {
  const Icon = iconForProduct(name, categorySlug);
  return (
    <span className={cn('flex shrink-0 items-center justify-center bg-brand-50 text-brand-600', SIZES[size], className)}>
      <Icon aria-hidden />
    </span>
  );
}
