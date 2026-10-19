import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router';
import { ArrowUpRight, Check, Clock, Info, Search, ShoppingBag, RotateCcw, TriangleAlert } from 'lucide-react';
import { Button, buttonClass } from '../components/Button';
import { Alert, TextField } from '../components/FormParts';
import { CopyButton } from '../components/documents/CopyButton';
import { ApiError, errorMessage } from '../lib/api';
import { comparePrices, getPriceSearchStatus, isGoogleListing, offerRetailer, RETAILERS, type PriceComparison, type PriceSearchStatus } from '../lib/price-comparison';
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
  const offers = result?.offers.filter(o => retailer === 'All' || offerRetailer(o) === retailer) ?? [];
  const sorted = sort === 'price' ? [...offers].sort((a, b) => a.price - b.price) : offers;
  return <main className="mx-auto max-w-6xl space-y-6 px-4 py-8 sm:px-6">
    <header>
      <h1 className="flex items-center gap-3 text-2xl font-semibold"><ShoppingBag className="size-7 text-brand-600" aria-hidden />Compare prices</h1>
      <p className="mt-2 text-slate-600">Find a product across stores before you buy. Start with its brand and full model number.</p>
    </header>
    {statusError && <Alert tone="error">{statusError} <Button size="sm" variant="ghost" onClick={refreshStatus}>Retry status</Button></Alert>}
    {status && !status.configured && <Alert tone="info">Price comparison has not been enabled on this server yet. Ask the app owner to configure it.</Alert>}
    <form noValidate onSubmit={search} className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 sm:p-6">
      <div className="grid gap-4 sm:grid-cols-[2fr_1fr]">
        <TextField label="What do you want to buy?" type="search" maxLength={160} placeholder="e.g. Casio A168WA-1 silver watch" value={query} onChange={e => setQuery(e.target.value)} error={fields.query} disabled={busy} />
        <TextField label="Model number (optional)" maxLength={80} placeholder="e.g. A168WA-1" value={model} onChange={e => setModel(e.target.value)} error={fields.model} hint="Helps distinguish similar models. Check the variant before buying." disabled={busy} />
      </div>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-xs text-slate-500">Searches run only when you click Compare. Recent searches are reused for 30 minutes.</p>
        <Button type="submit" variant="primary" icon={Search} busy={busy} disabled={status?.configured === false}>{busy ? 'Finding prices…' : 'Compare prices'}</Button>
      </div>
      {status?.configured && <p className="text-xs text-slate-500">{status.remaining} new searches left in this app’s shared budget · Up to {status.dailyLimit} new searches per person per day.</p>}
    </form>
    {error && <Alert tone="error">{error}</Alert>}
    {busy && <p role="status" className="text-sm text-slate-500">Searching Google Shopping in India…</p>}
    {!result && !busy && !error && <div className="rounded-xl bg-brand-50 p-5 text-sm text-brand-900"><p className="font-medium">A useful search is specific</p><p className="mt-1">Try a model such as “Casio A168WA-1”, rather than just “watch”. We compare the listings found by the search; some stores may not appear.</p></div>}
    {result && <section aria-label="Price comparison results" className="space-y-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div><h2 className="text-xl font-semibold">Results for {result.query}</h2><p className="mt-1 flex items-center gap-1 text-xs text-slate-500"><Clock className="size-3" aria-hidden />Checked {new Date(result.checkedAt).toLocaleString()} · {result.cached ? 'Recent saved results' : 'New search'} · SerpApi / Google Shopping</p></div>
        <CopyButton label="Copy price comparison" text={`${result.query}\nChecked: ${new Date(result.checkedAt).toLocaleString()}\n${result.offers.map(o => `${o.seller}: ${money(o.price)} — ${o.title}\n${o.url}`).join('\n')}\nVerify variants, discounts and final prices at the store.`}>Copy comparison</CopyButton>
      </div>
      <Alert tone="info">Prices are search listings, not verified checkout totals. “Model number found” means the title contains your model; check colour, size, seller, delivery and offer conditions before buying.</Alert>
      {!!result.offers.length && <div className="flex flex-wrap gap-4">
        <label className="text-sm font-medium">Store<select className="ml-2 rounded-lg border border-slate-300 bg-white px-3 py-2" value={retailer} onChange={e => setRetailer(e.target.value)}><option>All</option>{RETAILERS.map(r => <option key={r}>{r}</option>)}<option>Other</option></select></label>
        <label className="text-sm font-medium">Sort<select className="ml-2 rounded-lg border border-slate-300 bg-white px-3 py-2" value={sort} onChange={e => setSort(e.target.value)}><option value="match">Model matches first</option><option value="price">Listed price: low to high</option></select></label>
        <Button variant="ghost" icon={RotateCcw} disabled={retailer === 'All' && sort === 'match'} onClick={() => { setRetailer('All'); setSort('match'); }}>Clear filters</Button>
      </div>}
      {!sorted.length ? <div className="rounded-xl border border-slate-200 bg-white p-6"><p className="font-medium">No comparable offers found{retailer === 'All' ? '' : ` for ${retailer}`}.</p><p className="mt-2 text-sm text-slate-500">Try a full model number or another search. A missing result does not mean the store does not sell the product.</p></div> : <div className="grid gap-4 md:grid-cols-2">{sorted.map(o => <article key={`${o.url}-${o.price}-${o.seller}`} className="flex flex-col gap-3 rounded-xl border border-slate-200 bg-white p-5">
        <div className="flex flex-wrap items-center justify-between gap-2"><span className="text-sm font-semibold text-brand-700">{o.seller}</span><span className="text-xl font-semibold tabular-nums">{money(o.price)}</span></div>
        <h3 className="break-words font-medium">{o.title}</h3>
        <p className="flex items-center gap-1.5 text-xs text-slate-500">{o.match === 'MODEL_MATCH' ? <Check className="size-4" aria-hidden /> : <Info className="size-4" aria-hidden />}{o.match === 'MODEL_MATCH' ? 'Model number found · verify variant' : 'Possible match · check product details'}{o.sponsored ? ' · Sponsored listing' : ''}</p>
        {o.conditional && <p className="flex items-start gap-2 text-xs text-amber-800"><TriangleAlert className="size-4 shrink-0" aria-hidden />May involve instalments, special offers or a different condition. Verify the full price.</p>}
        {o.conditional && <p className="text-xs text-slate-500">Listed as: {o.priceText}</p>}
        {o.delivery && <p className="text-xs text-slate-500">Delivery: {o.delivery}</p>}
        <a href={o.url} target="_blank" rel="noopener noreferrer" className={`${buttonClass()} mt-auto self-start`}>{isGoogleListing(o.url) ? 'View Google listing' : 'Visit store'}<ArrowUpRight className="size-4" aria-hidden /></a>
      </article>)}</div>}
    </section>}
  </main>;
}
