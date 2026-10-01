import { CircleCheck, Database, Pencil, Sparkles, TriangleAlert, type LucideIcon } from 'lucide-react';
import { Badge, type BadgeTone } from '@/components/ui/Badge';
import type { FieldSource } from './confidence';

const STYLE: Record<Exclude<FieldSource, 'unscored'>, { tone: BadgeTone; icon: LucideIcon; label: string }> = {
  high: { tone: 'success', icon: CircleCheck, label: 'High confidence' },
  medium: { tone: 'warning', icon: TriangleAlert, label: 'Please verify' },
  low: { tone: 'danger', icon: TriangleAlert, label: 'Low confidence' },
  missing: { tone: 'warning', icon: TriangleAlert, label: 'Not found' },
  edited: { tone: 'brand', icon: Pencil, label: 'Edited' },
  suggested: { tone: 'brand', icon: Sparkles, label: 'AI suggestion' },
  saved: { tone: 'neutral', icon: Database, label: 'Saved value' },
};

interface ConfidenceBadgeProps {
  source: FieldSource | null;
  score?: number;
}

export function ConfidenceBadge({ source, score }: ConfidenceBadgeProps) {
  if (!source || source === 'unscored') return null;
  const { tone, icon: Icon, label } = STYLE[source];
  const title = typeof score === 'number' && ['high', 'medium', 'low'].includes(source)
    ? `Confidence ${Math.round(score * 100)}%`
    : undefined;
  return (
    <Badge tone={tone} size="sm" title={title} icon={<Icon className="size-3" aria-hidden />}>
      {label}
      {title && <span className="sr-only"> ({title})</span>}
    </Badge>
  );
}
