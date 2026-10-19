import { api } from './api';
export type SplitMember = {
  id: string;
  name: string;
  joined: boolean;
  self: boolean;
  owner: boolean;
  balance: number;
};
export type SplitShare = {
  memberId: string;
  amount: number | null;
};
export type SplitExpense = {
  id: string;
  title: string;
  amount: number;
  expenseDate: string;
  payerId: string;
  mode: 'EQUAL' | 'CUSTOM';
  shares: SplitShare[];
  voided: boolean;
  sellerName: string | null;
  documentNumber: string | null;
};
export type SplitSettlement = {
  id: string;
  fromMemberId: string;
  toMemberId: string;
  amount: number;
  status: 'PENDING' | 'CONFIRMED' | 'REJECTED';
  canConfirm: boolean;
  canReject: boolean;
  createdAt: string;
};
export type SplitGroup = {
  id: string;
  name: string;
  owner: boolean;
  members: SplitMember[];
  expenses: SplitExpense[];
  settlements: SplitSettlement[];
  activity: {
    id: string;
    message: string;
    createdAt: string;
  }[];
};
export type GroupSummary = {
  id: string;
  name: string;
  owner: boolean;
  members: number;
};
export type SplitInvitation = {
  token: string;
  expiresAt: string;
  memberName: string;
};
export type InvitePreview = {
  groupName: string;
  memberName: string;
  expiresAt: string;
};
export type ExpenseInput = {
  requestId: string;
  title: string;
  amount: number | null;
  expenseDate: string;
  payerId: string;
  mode: 'EQUAL' | 'CUSTOM';
  shares: SplitShare[];
  sourceDocumentId?: string;
};
export type PaymentInput = {
  requestId: string;
  fromMemberId: string;
  toMemberId: string;
  amount: number | null;
};
const base = '/splits/groups';
export const splitApi = {
  list: () => api<GroupSummary[]>(base),
  create: (name: string) => api<SplitGroup>(base, {
    body: {
      name
    }
  }),
  delete: (id: string) => api<void>(`${base}/${id}`, { method: 'DELETE' }),
  get: (id: string) => api<SplitGroup>(`${base}/${id}`),
  member: (id: string, name: string) => api<SplitGroup>(`${base}/${id}/members`, {
    body: {
      name
    }
  }),
  invite: (id: string, member: string) => api<SplitInvitation>(`${base}/${id}/members/${member}/invitation`, {
    method: 'POST'
  }),
  revoke: (id: string, member: string) => api<void>(`${base}/${id}/members/${member}/invitation`, {
    method: 'DELETE'
  }),
  preview: (token: string) => api<InvitePreview>('/splits/invitations/preview', {
    body: {
      token
    }
  }),
  accept: (token: string) => api<SplitGroup>('/splits/invitations/accept', {
    body: {
      token
    }
  }),
  expense: (id: string, body: ExpenseInput) => api<SplitGroup>(`${base}/${id}/expenses`, {
    body
  }),
  void: (id: string, expense: string) => api<SplitGroup>(`${base}/${id}/expenses/${expense}/void`, {
    method: 'POST'
  }),
  payment: (id: string, body: PaymentInput) => api<SplitGroup>(`${base}/${id}/settlements`, {
    body
  }),
  decide: (id: string, payment: string, action: 'confirm' | 'reject') => api<SplitGroup>(`${base}/${id}/settlements/${payment}/${action}`, {
    method: 'POST'
  }),
};
export const splitMoney = (n: number) => new Intl.NumberFormat('en-IN', {
  style: 'currency', currency: 'INR'
}).format(n);
/** Integer paise, matching the server's deterministic UUID ordering. */
export function equalShares(amount: number, ids: string[]): SplitShare[] {
  if (!Number.isFinite(amount) || amount <= 0 || !ids.length)
    return [];
  // Java UUID.compareTo compares signed 64-bit halves, unlike plain string sorting.
  const half = (id: string, start: number) => BigInt.asIntN(64, BigInt('0x' + id.replaceAll('-', '').slice(start, start + 16)));
  const sorted = [...ids].sort((a, b) => {
    const ah = half(a, 0), bh = half(b, 0);
    if (ah !== bh)
      return ah < bh ? -1 : 1;
    return half(a, 16) < half(b, 16) ? -1 : half(a, 16) > half(b, 16) ? 1 : 0;
  });
  const paise = Math.round(amount * 100), each = Math.floor(paise / sorted.length), extra = paise % sorted.length;
  return sorted.map((memberId, i) => ({
    memberId, amount: (each + (i < extra ? 1 : 0)) / 100
  }));
}
export function inviteUrl(token: string): string {
  return `${window.location.origin}/split-invite#token=${encodeURIComponent(token)}`;
}
