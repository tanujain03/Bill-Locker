import { ShieldAlert, ShieldCheck, ShieldQuestionMark, ShieldX, type LucideIcon } from 'lucide-react';
import type { BadgeTone } from '@/components/ui/Badge';
import type { ProgressTone } from '@/components/ui/misc';
import type { WarrantyStatus } from '@/types';

/** One tone + icon per warranty status, shared by badges, meters and charts. */
export const WARRANTY_STATUS_STYLE: Record<WarrantyStatus, { tone: BadgeTone & ProgressTone; icon: LucideIcon }> = {
  ACTIVE: { tone: 'success', icon: ShieldCheck },
  EXPIRING_SOON: { tone: 'warning', icon: ShieldAlert },
  EXPIRED: { tone: 'danger', icon: ShieldX },
  UNKNOWN: { tone: 'neutral', icon: ShieldQuestionMark },
};
