import type {
  Category,
  ChatReference,
  DocumentSummary,
  Product,
  SearchFilters,
  ServiceRecord,
} from '@/types';
import { daysUntil, toISODate } from '@/utils/date';
import { formatCurrency, formatDate } from '@/utils/format';
import { DOCUMENT_TYPE_LABELS, SERVICE_TYPE_LABELS } from '@/utils/labels';
import { upcomingServices } from '@/utils/service';
import { describeWarranty } from '@/utils/warranty';

/**
 * Stand-ins for the backend's LLM features. They answer strictly from the
 * user's own data (like RAG + structured retrieval would) and say so when the
 * answer isn't there — never inventing values.
 */

// ---------------------------------------------------------------------------
// Product vocabulary
// ---------------------------------------------------------------------------

const KINDS: { kind: string; terms: string[]; categorySlug: string }[] = [
  { kind: 'laptop', terms: ['laptop', 'notebook', 'inspiron', 'macbook'], categorySlug: 'computers' },
  { kind: 'printer', terms: ['printer', 'deskjet', 'smart tank'], categorySlug: 'computers' },
  { kind: 'phone', terms: ['phone', 'mobile', 'iphone', 'smartphone'], categorySlug: 'mobile-phones' },
  { kind: 'tv', terms: ['tv', 'television', 'bravia'], categorySlug: 'tv-entertainment' },
  { kind: 'fridge', terms: ['fridge', 'refrigerator'], categorySlug: 'home-appliances' },
  { kind: 'washer', terms: ['washing machine', 'washer', 'washing'], categorySlug: 'home-appliances' },
  { kind: 'ac', terms: ['ac', 'air conditioner', 'aircon', 'split ac'], categorySlug: 'home-appliances' },
  { kind: 'headphones', terms: ['headphone', 'earphone', 'earbud', 'headset', 'rockerz', 'airdopes'], categorySlug: 'audio' },
  { kind: 'watch', terms: ['watch', 'smartwatch'], categorySlug: 'audio' },
  { kind: 'sofa', terms: ['sofa', 'couch'], categorySlug: 'furniture' },
  { kind: 'fryer', terms: ['air fryer', 'airfryer', 'fryer'], categorySlug: 'kitchen' },
  { kind: 'iron', terms: ['iron'], categorySlug: 'home-appliances' },
];

const CATEGORY_WORDS: [RegExp, string][] = [
  [/\bappliances?\b/i, 'home-appliances'],
  [/\bkitchen\b/i, 'kitchen'],
  [/\bfurniture\b/i, 'furniture'],
  [/\b(electronics?|computers?|gadgets?)\b/i, 'computers'],
  [/\b(audio|wearables?)\b/i, 'audio'],
  [/\b(vehicles?|cars?|bikes?)\b/i, 'vehicles'],
];

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/** Whole-word-ish match; allows plurals and trailing digits ("Watch6"). */
function containsTerm(text: string, term: string): boolean {
  return new RegExp(`(^|[^a-z0-9])${escapeRegExp(term.toLowerCase())}(e?s)?($|[^a-z])`, 'i').test(text.toLowerCase());
}

function kindsOf(text: string): string[] {
  return KINDS.filter((entry) => entry.terms.some((term) => containsTerm(text, term))).map((entry) => entry.kind);
}

function productText(product: Product): string {
  return `${product.name} ${product.model ?? ''}`;
}

function mentionedProducts(question: string, products: Product[]): Product[] {
  const questionKinds = kindsOf(question);
  return products.filter((product) => {
    if (kindsOf(productText(product)).some((kind) => questionKinds.includes(kind))) return true;
    return Boolean(product.brand && containsTerm(question, product.brand));
  });
}

function sellerTerm(question: string): string | null {
  const match = /\b(?:from|at|on)\s+([a-z0-9][a-z0-9.&' -]{1,30}?)(?:\?|$|\s+(?:in|during|last|this|for)\b)/i.exec(question);
  return match ? match[1].trim().replace(/\.$/, '') : null;
}

function sellerMatches(seller: string | null, term: string): boolean {
  if (!seller) return false;
  const normalise = (value: string) => value.toLowerCase().replace(/[^a-z0-9]/g, '');
  return normalise(seller).includes(normalise(term));
}

function withinDays(question: string, today: Date): number | null {
  const days = /\b(?:within|in|next)\s+(?:the\s+next\s+)?(\d{1,4})\s*days?\b/i.exec(question);
  if (days) return Number(days[1]);
  const months = /\b(?:within|in|next)\s+(?:the\s+next\s+)?(\d{1,2})\s*months?\b/i.exec(question);
  if (months) return Number(months[1]) * 30;
  if (/\bthis month\b/i.test(question)) {
    const end = new Date(today.getFullYear(), today.getMonth() + 1, 0);
    return daysUntil(toISODate(end), today);
  }
  if (/\bthis year\b/i.test(question)) {
    return daysUntil(`${today.getFullYear()}-12-31`, today);
  }
  return null;
}

// ---------------------------------------------------------------------------
// Assistant
// ---------------------------------------------------------------------------

export interface AssistantContext {
  products: Product[];
  documents: DocumentSummary[];
  services: ServiceRecord[];
  today?: Date;
}

export interface AssistantAnswer {
  content: string;
  references: ChatReference[];
}

function productRef(product: Product): ChatReference {
  return {
    type: 'PRODUCT',
    id: product.id,
    title: product.name,
    subtitle: product.warranty ? describeWarranty(product.warranty) : 'Warranty period not found',
  };
}

function documentRef(document: DocumentSummary): ChatReference {
  return {
    type: 'DOCUMENT',
    id: document.id,
    title: document.fileName,
    subtitle: `${DOCUMENT_TYPE_LABELS[document.documentType]}${document.productName ? ` · ${document.productName}` : ''}`,
  };
}

function invoiceFor(product: Product, documents: DocumentSummary[]): DocumentSummary | undefined {
  const own = documents.filter((document) => document.productId === product.id);
  return own.find((document) => document.documentType === 'INVOICE') ?? own[0];
}

function warrantyLine(product: Product): string {
  const warranty = product.warranty;
  if (!warranty || warranty.status === 'UNKNOWN' || !warranty.expiryDate) {
    return `**${product.name}** — no warranty period on its documents`;
  }
  return `**${product.name}** — ${warranty.status === 'EXPIRED' ? 'expired' : 'until'} ${formatDate(warranty.expiryDate)} (${describeWarranty(warranty).toLowerCase()})`;
}

function list(products: Product[], line: (product: Product) => string): string {
  return products.map((product) => `- ${line(product)}`).join('\n');
}

function answerWarrantyFor(product: Product): string {
  const warranty = product.warranty;
  if (!warranty || warranty.status === 'UNKNOWN' || !warranty.expiryDate) {
    return `I couldn’t find a warranty period on the documents for your **${product.name}**, so I can’t tell whether it’s still covered. You can add the warranty period from the product page.`;
  }
  const expiry = formatDate(warranty.expiryDate);
  const days = warranty.daysRemaining ?? daysUntil(warranty.expiryDate);
  if (warranty.status === 'EXPIRED') {
    return `No — the warranty on your **${product.name}** ended on **${expiry}** (${Math.abs(days)} days ago).`;
  }
  const soon = warranty.status === 'EXPIRING_SOON' ? ' It expires soon, so raise any claim before then.' : '';
  return `Yes — your **${product.name}** is under warranty until **${expiry}** (${days} days left).${soon}`;
}

export function answerQuestion(question: string, context: AssistantContext): AssistantAnswer {
  const today = context.today ?? new Date();
  const q = question.toLowerCase().trim();
  const { products, documents } = context;

  if (products.length === 0 && documents.length === 0) {
    return {
      content:
        'Your locker is empty right now, so I don’t have any purchases to look at. Upload a bill or import one from Gmail and ask me again.',
      references: [],
    };
  }

  const mentioned = mentionedProducts(q, products);
  const refs = (items: Product[]) => items.slice(0, 5).map(productRef);
  const expiringWithin = withinDays(q, today);

  // Serial number
  if (/serial/.test(q)) {
    if (mentioned.length === 0) {
      return { content: 'Which product do you mean? For example: “What is the serial number of my TV?”', references: [] };
    }
    const lines = mentioned.map((product) =>
      product.serialNumber
        ? `The serial number of your **${product.name}** is **${product.serialNumber}**.`
        : `Your **${product.name}** has no serial number on its documents, so I can’t tell you — you can add it from the product page.`,
    );
    return { content: lines.join('\n\n'), references: refs(mentioned) };
  }

  // Purchase date
  if (/when (did|was|were) (i|it|they|my)?.*\b(buy|bought|purchase|purchased|get|got)\b|purchase date|date of purchase/.test(q)) {
    if (mentioned.length === 0) {
      return { content: 'Which product do you mean? For example: “When did I buy my refrigerator?”', references: [] };
    }
    const lines = mentioned.map((product) =>
      product.purchaseDate
        ? `You bought your **${product.name}** on **${formatDate(product.purchaseDate)}**${product.seller ? ` from ${product.seller}` : ''}${product.purchasePrice !== null ? ` for ${formatCurrency(product.purchasePrice, product.currency)}` : ''}.`
        : `I couldn’t find a purchase date for your **${product.name}** on its documents.`,
    );
    return { content: lines.join('\n\n'), references: refs(mentioned) };
  }

  // Find an invoice / bill
  if (/\b(invoice|bill|receipt)s?\b/.test(q) && (mentioned.length > 0 || /\b(find|show|where|get|open|need)\b/.test(q))) {
    if (mentioned.length === 0) {
      return { content: 'Which product’s invoice are you looking for? For example: “Find the invoice for my headphones.”', references: [] };
    }
    const found = mentioned.map((product) => ({ product, document: invoiceFor(product, documents) }));
    const lines = found.map(({ product, document }) =>
      document
        ? `Here’s the ${DOCUMENT_TYPE_LABELS[document.documentType].toLowerCase()} for your **${product.name}**: **${document.fileName}** (added ${formatDate(document.createdAt)}).`
        : `I couldn’t find an invoice for your **${product.name}** in your locker.`,
    );
    const references = found.flatMap(({ product, document }) => (document ? [documentRef(document), productRef(product)] : [productRef(product)]));
    return { content: lines.join('\n\n'), references: references.slice(0, 6) };
  }

  const expiring = (days: number) =>
    products
      .filter((product) => product.warranty?.daysRemaining !== null && product.warranty?.daysRemaining !== undefined)
      .filter((product) => (product.warranty?.daysRemaining ?? -1) >= 0 && (product.warranty?.daysRemaining ?? Infinity) <= days)
      .sort((a, b) => (a.warranty?.daysRemaining ?? 0) - (b.warranty?.daysRemaining ?? 0));

  // Warranties expiring within a window
  if (expiringWithin !== null && /expir|warrant|end/.test(q)) {
    const matches = expiring(expiringWithin);
    const window = /this month/.test(q) ? 'this month' : `in the next ${expiringWithin} days`;
    if (matches.length === 0) return { content: `None of your warranties expire ${window}. 🎉`, references: [] };
    return {
      content: `${matches.length === 1 ? 'One warranty expires' : `${matches.length} warranties expire`} ${window}:\n${list(matches, warrantyLine)}`,
      references: refs(matches),
    };
  }

  // Expiring soon
  if (/expir\w* soon|about to expire|ending soon|expire next/.test(q)) {
    const matches = products.filter((product) => product.warranty?.status === 'EXPIRING_SOON');
    if (matches.length === 0) return { content: 'No warranties are expiring in the next 30 days.', references: [] };
    return {
      content: `These warranties expire within 30 days:\n${list(matches, warrantyLine)}\n\nIf anything needs a repair, claim it before the expiry date.`,
      references: refs(matches),
    };
  }

  // Warranty status of a specific product
  if (mentioned.length > 0 && /warrant|covered|guarantee/.test(q)) {
    return { content: mentioned.map(answerWarrantyFor).join('\n\n'), references: refs(mentioned) };
  }

  // Expired
  if (/expired|out of warranty|no longer (under|covered)/.test(q)) {
    const matches = products.filter((product) => product.warranty?.status === 'EXPIRED');
    if (matches.length === 0) return { content: 'None of your warranties have expired.', references: [] };
    return { content: `${matches.length === 1 ? 'This warranty has' : 'These warranties have'} expired:\n${list(matches, warrantyLine)}`, references: refs(matches) };
  }

  // Under warranty (all)
  if (/under warranty|still covered|in warranty|active warrant|which .* covered/.test(q)) {
    const matches = products.filter((product) => product.warranty?.status === 'ACTIVE' || product.warranty?.status === 'EXPIRING_SOON');
    if (matches.length === 0) return { content: 'None of your products are currently under warranty.', references: [] };
    return { content: `${matches.length} of your products are under warranty:\n${list(matches, warrantyLine)}`, references: refs(matches) };
  }

  // Most / least expensive
  const priced = products.filter((product) => product.purchasePrice !== null);
  if (/most expensive|costliest|highest price|biggest purchase|priciest/.test(q) && priced.length > 0) {
    const top = [...priced].sort((a, b) => (b.purchasePrice ?? 0) - (a.purchasePrice ?? 0))[0];
    return {
      content: `Your most expensive purchase is the **${top.name}** — ${formatCurrency(top.purchasePrice, top.currency)}${top.purchaseDate ? `, bought on ${formatDate(top.purchaseDate)}` : ''}${top.seller ? ` from ${top.seller}` : ''}.`,
      references: refs([top]),
    };
  }
  if (/cheapest|least expensive|lowest price/.test(q) && priced.length > 0) {
    const bottom = [...priced].sort((a, b) => (a.purchasePrice ?? 0) - (b.purchasePrice ?? 0))[0];
    return {
      content: `Your least expensive purchase is the **${bottom.name}** — ${formatCurrency(bottom.purchasePrice, bottom.currency)}.`,
      references: refs([bottom]),
    };
  }

  // Bought from a seller
  const seller = /\b(from|at)\b/.test(q) && /(buy|bought|purchase|order|product|item|got)/.test(q) ? sellerTerm(q) : null;
  if (seller) {
    const matches = products.filter((product) => sellerMatches(product.seller, seller));
    if (matches.length === 0) return { content: `I couldn’t find any purchases from “${seller}” in your documents.`, references: [] };
    return {
      content: `You bought ${matches.length === 1 ? 'one product' : `${matches.length} products`} from ${matches[0].seller}:\n${list(
        matches,
        (product) => `**${product.name}** — ${formatCurrency(product.purchasePrice, product.currency)} on ${formatDate(product.purchaseDate)}`,
      )}`,
      references: refs(matches),
    };
  }

  // Spending
  if (/how much|total spen|spent|spending/.test(q)) {
    const total = priced.reduce((sum, product) => sum + (product.purchasePrice ?? 0), 0);
    return {
      content: `You’ve recorded **${formatCurrency(total)}** across ${priced.length} purchases. The biggest was the **${
        [...priced].sort((a, b) => (b.purchasePrice ?? 0) - (a.purchasePrice ?? 0))[0]?.name ?? '—'
      }**.`,
      references: [],
    };
  }

  // Services
  if (/service|maintenance|repair/.test(q)) {
    const scheduled = upcomingServices(context.services).filter((record) => mentioned.length === 0 || mentioned.some((product) => product.id === record.productId));
    if (scheduled.length === 0) return { content: 'I couldn’t find any upcoming services. Add a next service date to a service record to track it.', references: [] };
    return {
      content: `Upcoming services:\n${scheduled
        .map((record) => `- **${record.productName}** — ${formatDate(record.nextServiceDate)} (last: ${SERVICE_TYPE_LABELS[record.serviceType].toLowerCase()} on ${formatDate(record.serviceDate)})`)
        .join('\n')}`,
      references: scheduled
        .map((record) => products.find((product) => product.id === record.productId))
        .filter((product): product is Product => Boolean(product))
        .map(productRef),
    };
  }

  // A product was mentioned but no specific question
  if (mentioned.length > 0) {
    return {
      content: mentioned
        .map(
          (product) =>
            `**${product.name}**: bought ${product.purchaseDate ? `on ${formatDate(product.purchaseDate)}` : '(date unknown)'}${product.seller ? ` from ${product.seller}` : ''}${product.purchasePrice !== null ? ` for ${formatCurrency(product.purchasePrice, product.currency)}` : ''}. ${answerWarrantyFor(product)}`,
        )
        .join('\n\n'),
      references: refs(mentioned),
    };
  }

  return {
    content:
      'I couldn’t find anything about that in your documents. I can help with warranties, purchase dates, prices, sellers, serial numbers, invoices and service dates — for example “Which warranties expire this month?”',
    references: [],
  };
}

// ---------------------------------------------------------------------------
// Natural-language search → structured filters (executed by "the database")
// ---------------------------------------------------------------------------

const STOP_WORDS = new Set(
  'show me my all the a an any find list which what did i do have products product items item purchases purchase bought buy please with of for that whose are is'.split(
    ' ',
  ),
);

function parseAmount(raw: string, suffix?: string): number {
  const value = Number(raw.replace(/,/g, ''));
  if (suffix?.toLowerCase() === 'k') return value * 1000;
  if (suffix?.toLowerCase() === 'l' || suffix?.toLowerCase() === 'lakh') return value * 100_000;
  return value;
}

export function interpretSearch(query: string, categories: Category[], products: Product[], today = new Date()): SearchFilters {
  const q = query.toLowerCase();
  const filters: SearchFilters = {};

  const days = withinDays(q, today);
  if (days !== null && /expir|warrant|end/.test(q)) {
    filters.daysUntilExpiry = days;
    filters.sortBy = 'WARRANTY_EXPIRY';
    filters.sortDirection = 'ASC';
  } else if (/expired|out of warranty/.test(q)) {
    filters.warrantyStatus = 'EXPIRED';
  } else if (/expir\w* soon|about to expire/.test(q)) {
    filters.warrantyStatus = 'EXPIRING_SOON';
    filters.sortBy = 'WARRANTY_EXPIRY';
    filters.sortDirection = 'ASC';
  } else if (/under warranty|active warrant|still covered|in warranty/.test(q)) {
    filters.warrantyStatus = 'ACTIVE';
  } else if (/no warranty|unknown warranty|without warranty/.test(q)) {
    filters.warrantyStatus = 'UNKNOWN';
  }

  const seller = /\b(from|at)\b/.test(q) ? sellerTerm(q) : null;
  if (seller && products.some((product) => sellerMatches(product.seller, seller))) filters.seller = seller;

  const brand = products.map((product) => product.brand).find((value) => value && containsTerm(q, value) && !(seller && sellerMatches(value, seller)));
  if (brand) filters.brand = brand;

  const kinds = kindsOf(q);
  const categorySlug = CATEGORY_WORDS.find(([pattern]) => pattern.test(q))?.[1];
  if (categorySlug && categories.some((category) => category.slug === categorySlug)) filters.categorySlug = categorySlug;
  if (kinds.length > 0) filters.text = KINDS.find((entry) => entry.kind === kinds[0])?.terms[0] ?? null;

  const over = /\b(?:over|above|more than|greater than|costing more than)\s*(?:rs\.?|₹|inr)?\s*([\d,]+)\s*(k|l|lakh)?\b/i.exec(q);
  if (over) filters.minPrice = parseAmount(over[1], over[2]);
  const under = /\b(?:under|below|less than|cheaper than)\s*(?:rs\.?|₹|inr)?\s*([\d,]+)\s*(k|l|lakh)?\b/i.exec(q);
  if (under) filters.maxPrice = parseAmount(under[1], under[2]);

  const year = /\b(?:in|during)\s+(20\d{2})\b/.exec(q);
  if (year) {
    filters.purchasedAfter = `${year[1]}-01-01`;
    filters.purchasedBefore = `${year[1]}-12-31`;
  } else if (/\blast year\b/.test(q)) {
    filters.purchasedAfter = `${today.getFullYear() - 1}-01-01`;
    filters.purchasedBefore = `${today.getFullYear() - 1}-12-31`;
  } else if (/\bthis year\b/.test(q) && filters.daysUntilExpiry === undefined) {
    filters.purchasedAfter = `${today.getFullYear()}-01-01`;
  }

  if (/most expensive|costliest|priciest|highest price/.test(q)) {
    filters.sortBy = 'PRICE';
    filters.sortDirection = 'DESC';
    filters.limit = 1;
  } else if (/cheapest|least expensive|lowest price/.test(q)) {
    filters.sortBy = 'PRICE';
    filters.sortDirection = 'ASC';
    filters.limit = 1;
  } else if (/\b(latest|newest|recent)\b/.test(q)) {
    filters.sortBy = 'PURCHASE_DATE';
    filters.sortDirection = 'DESC';
  } else if (/\boldest\b/.test(q)) {
    filters.sortBy = 'PURCHASE_DATE';
    filters.sortDirection = 'ASC';
  }

  const understood = Object.keys(filters).length > 0;
  if (!understood) {
    const words = q
      .replace(/[^a-z0-9 ]/g, ' ')
      .split(/\s+/)
      .filter((word) => word && !STOP_WORDS.has(word));
    filters.text = words.join(' ') || null;
  }
  return filters;
}

function matchesText(product: Product, text: string): boolean {
  const haystack = `${product.name} ${product.brand ?? ''} ${product.model ?? ''} ${product.seller ?? ''} ${product.categoryName ?? ''}`.toLowerCase();
  if (haystack.includes(text.toLowerCase())) return true;
  const wanted = kindsOf(text);
  return wanted.length > 0 && kindsOf(productText(product)).some((kind) => wanted.includes(kind));
}

/** The "database query": structured filters only — no generated SQL. */
export function applySearch(products: Product[], filters: SearchFilters): Product[] {
  let results = products.filter((product) => {
    const warranty = product.warranty;
    if (filters.text && !matchesText(product, filters.text)) return false;
    if (filters.categorySlug && product.categorySlug !== filters.categorySlug) return false;
    if (filters.brand && product.brand?.toLowerCase() !== filters.brand.toLowerCase()) return false;
    if (filters.seller && !sellerMatches(product.seller, filters.seller)) return false;
    if (filters.warrantyStatus && (warranty?.status ?? 'UNKNOWN') !== filters.warrantyStatus) return false;
    if (typeof filters.daysUntilExpiry === 'number') {
      const days = warranty?.daysRemaining;
      if (days === null || days === undefined || days < 0 || days > filters.daysUntilExpiry) return false;
    }
    if (typeof filters.minPrice === 'number' && (product.purchasePrice ?? -1) < filters.minPrice) return false;
    if (typeof filters.maxPrice === 'number' && (product.purchasePrice ?? Infinity) > filters.maxPrice) return false;
    if (filters.purchasedAfter && (!product.purchaseDate || product.purchaseDate < filters.purchasedAfter)) return false;
    if (filters.purchasedBefore && (!product.purchaseDate || product.purchaseDate > filters.purchasedBefore)) return false;
    return true;
  });

  const direction = filters.sortDirection === 'ASC' ? 1 : -1;
  if (filters.sortBy === 'PRICE') results = results.sort((a, b) => direction * ((a.purchasePrice ?? 0) - (b.purchasePrice ?? 0)));
  if (filters.sortBy === 'PURCHASE_DATE') results = results.sort((a, b) => direction * (a.purchaseDate ?? '').localeCompare(b.purchaseDate ?? ''));
  if (filters.sortBy === 'WARRANTY_EXPIRY') {
    results = results.sort((a, b) => direction * (a.warranty?.expiryDate ?? '9999').localeCompare(b.warranty?.expiryDate ?? '9999'));
  }
  if (typeof filters.limit === 'number') results = results.slice(0, filters.limit);
  return results;
}

export function explainSearch(filters: SearchFilters, categories: Category[]): string {
  if (filters.limit === 1 && filters.sortBy === 'PRICE') {
    return filters.sortDirection === 'ASC' ? 'Showing your least expensive purchase.' : 'Showing your most expensive purchase.';
  }
  const parts: string[] = [];
  if (filters.text) parts.push(`matching “${filters.text}”`);
  if (filters.categorySlug) parts.push(`in ${categories.find((category) => category.slug === filters.categorySlug)?.name ?? filters.categorySlug}`);
  if (filters.brand) parts.push(`made by ${filters.brand}`);
  if (filters.seller) parts.push(`bought from ${filters.seller}`);
  if (filters.warrantyStatus === 'ACTIVE') parts.push('that are under warranty');
  if (filters.warrantyStatus === 'EXPIRED') parts.push('whose warranty has expired');
  if (filters.warrantyStatus === 'EXPIRING_SOON') parts.push('whose warranty expires within 30 days');
  if (filters.warrantyStatus === 'UNKNOWN') parts.push('with no warranty information');
  if (typeof filters.daysUntilExpiry === 'number') parts.push(`whose warranty expires within the next ${filters.daysUntilExpiry} days`);
  if (typeof filters.minPrice === 'number') parts.push(`costing at least ${formatCurrency(filters.minPrice)}`);
  if (typeof filters.maxPrice === 'number') parts.push(`costing at most ${formatCurrency(filters.maxPrice)}`);
  if (filters.purchasedAfter && filters.purchasedBefore) {
    parts.push(`bought between ${formatDate(filters.purchasedAfter)} and ${formatDate(filters.purchasedBefore)}`);
  } else if (filters.purchasedAfter) {
    parts.push(`bought after ${formatDate(filters.purchasedAfter)}`);
  }
  let sentence = `Showing products ${parts.join(', ')}`.trim();
  if (parts.length === 0) sentence = 'Showing all products';
  if (filters.sortBy === 'WARRANTY_EXPIRY') sentence += ', soonest expiry first';
  if (filters.sortBy === 'PURCHASE_DATE') sentence += filters.sortDirection === 'ASC' ? ', oldest first' : ', newest first';
  return `${sentence}.`;
}
