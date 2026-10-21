import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router';
import { ArrowUpRight, Check, Clock, Info, Search, ShoppingBag, RotateCcw, TriangleAlert } from 'lucide-react';
import { Button, buttonClass } from '../components/Button';
import { Alert, TextField } from '../components/FormParts';
import { CopyButton } from '../components/documents/CopyButton';
import { ApiError, errorMessage } from '../lib/api';
import { comparePrices, getPriceSearchStatus, getPriceStoreLink, isGoogleListing, offerRetailer, RETAILERS, type PriceComparison, type PriceSearchStatus, type PriceOffer } from '../lib/price-comparison';
import { useFeedback } from '../lib/feedback-context';
import { usePageTitle } from '../lib/usePageTitle';
const money = (amount: number) => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(amount);
export function PriceComparisonPage() {
  usePageTitle('Compare prices');
  const [params] = useSearchParams();
  const [query, setQuery] = useState(params.get('q') ?? '');
  const [model, setModel] = useState(params.get('model') ?? '');
  const [status, setStatus] = useState<PriceSearchStatus | null>(null);
  const [result, setResult] = useState<PriceComparison | null>(null);
  const [error, setError] = useState('');
  const [statusError, setStatusError] = useState('');
  const [fields, setFields] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [retailer, setRetailer] = useState('All');
  const [sort, setSort] = useState('match');
  const running = useRef(false), mounted = useRef(true);
  const openingRef = useRef(false);
  const [opening, setOpening] = useState<string | null>(null);
  const { toast } = useFeedback();
  useEffect(() => {
    mounted.current = true;
    void refreshStatus();
    return () => { mounted.current = false; };
  }, []);
  async function refreshStatus() {
    try {
      const next = await getPriceSearchStatus();
      if (mounted.current) { setStatus(next); setStatusError(''); }
    } catch (e) { if (mounted.current) setStatusError(errorMessage(e)); }
  }
  async function search(event: FormEvent) {
    event.preventDefault();
    if (running.current) return;
    running.current = true; setBusy(true); setError(''); setFields({}); setResult(null);
    try {
      const next = await comparePrices(query, model);
      if (mounted.current) { setResult(next); setRetailer('All'); }
    } catch (e) {
      if (mounted.current) { setError(errorMessage(e)); if (e instanceof ApiError) setFields(e.fieldErrors); }
    } finally {
      running.current = false;
      if (mounted.current) { setBusy(false); void refreshStatus(); }
    }
  }
  async function visitStore(offer: PriceOffer) {
    if (!result || openingRef.current) return;
    // Open during the click gesture so popup blockers do not reject the later API response.
    const tab = window.open('about:blank', '_blank');
    if (!tab) { toast('Allow popups for Bill Locker, then try opening the store again.', 'error'); return; }
    tab.opener = null;
    const policy = tab.document.createElement('meta');
    policy.name = 'referrer'; policy.content = 'no-referrer'; tab.document.head.appendChild(policy);
    tab.document.title = 'Opening store'; tab.document.body.textContent = 'Finding the product page…';
    openingRef.current = true; setOpening(offer.url);
    try {
      const direct = await getPriceStoreLink({ query: result.query, model: result.model }, offer);
      tab.location.replace(direct.url);
      if (mounted.current) setResult(current => current ? { ...current, offers: current.offers.map(o => o.url === offer.url && o.seller === offer.seller ? { ...o, directUrl: direct.url } : o) } : current);
    } catch (e) {
      tab.close();
      if (mounted.current) toast(errorMessage(e), 'error');
    } finally {
      openingRef.current = false;
      if (mounted.current) { setOpening(null); void refreshStatus(); }
    }
  }
  const offers = result?.offers.filter(o => retailer === 'All' || offerRetailer(o) === retailer) ?? [];
  const sorted = sort === 'price' ? [...offers].sort((a, b) => a.price - b.price) : offers;
  return <main className="mx-auto max-w-6xl space-y-6 px-4 py-8 sm:px-6">
    <header>
      <div className="flex items-center justify-between gap-3">
        <h1 className="flex items-center gap-3 text-2xl font-semibold"><ShoppingBag className="size-7 text-brand-600" aria-hidden />Compare prices</h1>
        <Button variant="ghost" icon={Info} aria-label="Price comparison information" popoverTarget="price-comparison-info" />
      </div>
      <div id="price-comparison-info" popover="auto" role="dialog" aria-labelledby="price-info-title" className="fixed inset-auto right-4 top-20 m-0 max-h-[calc(100dvh_-_6rem)] w-[calc(100%_-_2rem)] max-w-md sm:right-6 space-y-4 overflow-y-auto rounded-2xl border border-slate-200 bg-white p-5 text-sm text-slate-700 shadow-xl">
        <div className="flex items-center justify-between gap-3"><h2 id="price-info-title" className="font-semibold text-slate-900">Price comparison information</h2><Button size="sm" variant="ghost" popoverTarget="price-comparison-info" popoverTargetAction="hide">Close</Button></div>
        {status?.configured ? <dl className="space-y-3">
          <div className="rounded-xl bg-blue-50 p-3 text-blue-950"><dt className="font-medium">Shared requests remaining</dt><dd>{status.remaining} of {status.limit} in this app’s rolling 31-day budget.</dd></div>
          <div className="rounded-xl bg-violet-50 p-3 text-violet-950"><dt className="font-medium">Your requests remaining today</dt><dd>{status.dailyRemaining} of {status.dailyLimit}. Resets {new Date(status.dailyResetsAt).toLocaleString()}.</dd></div>
          <div className="rounded-xl bg-emerald-50 p-3 text-emerald-950"><dt className="font-medium">Requests available to you now</dt><dd>{Math.min(status.remaining, status.dailyRemaining, status.hourlyRemaining)} · Also subject to a shared limit of 40 requests per hour.</dd></div>
        </dl> : <p>Request counts are available when price comparison is enabled and its status has loaded.</p>}
        <p className="rounded-xl bg-slate-50 p-3">These are this app’s request counters, not the live SerpApi account balance. Usage outside Bill Locker can reduce the provider’s remaining allowance.</p>
        <p className="rounded-xl bg-sky-50 p-3 text-sky-950">Searches run only when you click Compare prices. Your recent searches are reused for 30 minutes without using another request. Adding a model number helps distinguish similar products; verify the variant before buying.</p>
        <p className="rounded-xl bg-orange-50 p-3 text-orange-950">Finding a direct store link may use another request. Previously resolved links are reused. Searches, store lookups and failed attempts share your daily limit. Google listing buttons do not use an API request.</p>
        <p className="rounded-xl bg-amber-50 p-3 text-amber-950">Results come from SerpApi / Google Shopping. Prices are search listings, not verified checkout totals. “Model number found” means the title contains your model; check colour, size, seller, delivery and offer conditions before buying.</p>
      </div>
      <p className="mt-2 text-slate-600">Find a product across stores before you buy. Start with its brand and full model number.</p>
    </header>
    {statusError && <Alert tone="error">{statusError} <Button size="sm" variant="ghost" onClick={refreshStatus}>Retry status</Button></Alert>}
    {status && !status.configured && <Alert tone="info">Price comparison has not been enabled on this server yet. Ask the app owner to configure it.</Alert>}
    <form noValidate onSubmit={search} className="rounded-2xl border border-slate-200 bg-white p-4 sm:p-5">
      <div className="grid items-start gap-4 md:grid-cols-[minmax(0,2fr)_minmax(0,1fr)_auto]">
        <TextField label="What do you want to buy?" type="search" maxLength={160} placeholder="e.g. Casio A168WA-1 silver watch" value={query} onChange={e => setQuery(e.target.value)} error={fields.query} disabled={busy} />
        <TextField label="Model number (optional)" maxLength={80} placeholder="e.g. A168WA-1" value={model} onChange={e => setModel(e.target.value)} error={fields.model} disabled={busy} />
        <div className="flex justify-end md:pt-6">
        <Button type="submit" variant="primary" icon={Search} busy={busy} disabled={status?.configured === false}>{busy ? 'Finding prices…' : 'Compare prices'}</Button>
        </div>
      </div>
    </form>
    {error && <Alert tone="error">{error}</Alert>}
    {busy && <p role="status" className="text-sm text-slate-500">Searching Google Shopping in India…</p>}
    {!result && !busy && !error && <div className="rounded-xl bg-brand-50 p-5 text-sm text-brand-900"><p className="font-medium">A useful search is specific</p><p className="mt-1">Try a model such as “Casio A168WA-1”, rather than just “watch”. We compare the listings found by the search; some stores may not appear.</p></div>}
    {result && <section aria-label="Price comparison results" className="space-y-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div><h2 className="text-xl font-semibold">Results for {result.query}</h2><p className="mt-1 flex items-center gap-1 text-xs text-slate-500"><Clock className="size-3" aria-hidden />Checked {new Date(result.checkedAt).toLocaleString()} · {result.cached ? 'Recent saved results' : 'New search'}</p></div>
        <CopyButton label="Copy price comparison" text={`${result.query}\nChecked: ${new Date(result.checkedAt).toLocaleString()}\n${result.offers.map(o => `${o.seller}: ${money(o.price)} — ${o.title}\n${o.directUrl || o.url}`).join('\n')}\nVerify variants, discounts and final prices at the store.`}>Copy comparison</CopyButton>
      </div>
      {!!result.offers.length && <div className="flex flex-wrap gap-4">
        <label className="text-sm font-medium">Store<select className="ml-2 rounded-lg border border-slate-300 bg-white px-3 py-2" value={retailer} onChange={e => setRetailer(e.target.value)}><option>All</option>{RETAILERS.map(r => <option key={r}>{r}</option>)}<option>Other</option></select></label>
        <label className="text-sm font-medium">Sort<select className="ml-2 rounded-lg border border-slate-300 bg-white px-3 py-2" value={sort} onChange={e => setSort(e.target.value)}><option value="match">Model matches first</option><option value="price">Listed price: low to high</option></select></label>
        <Button variant="ghost" icon={RotateCcw} disabled={retailer === 'All' && sort === 'match'} onClick={() => { setRetailer('All'); setSort('match'); }}>Clear filters</Button>
      </div>}
      {!sorted.length ? <div className="rounded-xl border border-slate-200 bg-white p-6"><p className="font-medium">No comparable offers found{retailer === 'All' ? '' : ` for ${retailer}`}.</p><p className="mt-2 text-sm text-slate-500">Try a full model number or another search. A missing result does not mean the store does not sell the product.</p></div> : <div className="grid gap-4 md:grid-cols-2">{sorted.map(o => <div key={`${o.url}-${o.price}-${o.seller}`} className="relative isolate flex flex-col gap-3 rounded-xl border border-slate-200 bg-white p-5">
        <div className="flex items-start justify-between gap-3"><span className="rounded-md bg-brand-50 px-2.5 py-1 text-xs font-medium text-brand-700">{o.seller}</span>

        </div>
        <h3 className="break-words font-medium">{o.title}</h3>
        <p className="flex items-center gap-1.5 text-xs text-slate-500">{o.match === 'MODEL_MATCH' ? <Check className="size-4" aria-hidden /> : <Info className="size-4" aria-hidden />}{o.match === 'MODEL_MATCH' ? 'Model number found · verify variant' : 'Possible match · check product details'}{o.sponsored ? ' · Sponsored listing' : ''}</p>
        <p className="mt-2 text-2xl font-semibold tabular-nums">{money(o.price)}</p>
        {o.conditional && <p className="flex items-start gap-2 text-xs text-amber-800"><TriangleAlert className="size-4 shrink-0" aria-hidden />May involve instalments, special offers or a different condition. Verify the full price.</p>}
        {o.conditional && <p className="text-xs text-slate-500">Listed as: {o.priceText}</p>}
        {o.delivery && <p className="text-xs text-slate-500">Delivery: {o.delivery}</p>}
        <div className="mt-auto flex flex-wrap gap-2 pt-2">
        <a aria-label={`Visit ${o.seller} for ${o.title}`} href={o.directUrl || o.url} target="_blank" rel="noopener noreferrer" aria-busy={opening === o.url || undefined} onClick={event => {
        if (!o.directUrl && isGoogleListing(o.url)) { event.preventDefault(); void visitStore(o); }
      }} onAuxClick={event => {
        if (event.button === 1 && !o.directUrl && isGoogleListing(o.url)) { event.preventDefault(); void visitStore(o); }
      }} className={`${buttonClass()} focus-visible:outline-2 focus-visible:outline-brand-500`}>{opening === o.url ? 'Opening store…' : 'Visit store'}<ArrowUpRight className="size-4" aria-hidden /></a>
        {isGoogleListing(o.url) && <a href={o.url} target="_blank" rel="noopener noreferrer" aria-label={`Google listing for ${o.title} at ${o.seller}`} className={`${buttonClass()} focus-visible:outline-2 focus-visible:outline-brand-500`}>Google listing<ArrowUpRight className="size-4" aria-hidden /></a>}
        </div>
      </div>)}</div>}
    </section>}
  </main>;
}
