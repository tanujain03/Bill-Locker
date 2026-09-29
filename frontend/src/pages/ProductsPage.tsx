import { Package, PackagePlus, Search, SearchX, Sparkles, Upload, X } from 'lucide-react';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router';
import { useUpload } from '@/components/documents/upload-context';
import { ProductCard } from '@/components/products/ProductCard';
import { ProductFormDialog } from '@/components/products/ProductFormDialog';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Select } from '@/components/ui/Field';
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/feedback';
import { PageHeader } from '@/components/ui/PageHeader';
import { useAiSearch } from '@/hooks/useAi';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { useCategories, useProducts } from '@/hooks/useProducts';
import { cn } from '@/lib/cn';
import { WARRANTY_STATUSES, type Product, type WarrantyStatus } from '@/types';
import { pluralize } from '@/utils/format';
import { describeSearchFilters, PRODUCT_SORT_LABELS, sortProducts, type ProductSort } from '@/utils/search';
import { WARRANTY_STATUS_LABELS } from '@/utils/warranty';

const AI_EXAMPLES = [
  'Warranties expiring within 90 days',
  'Most expensive purchase',
  'What did I buy from Amazon?',
  'Expired warranties',
];

export function ProductsPage() {
  useDocumentTitle('My Products');
  const [params, setParams] = useSearchParams();
  const aiQuery = params.get('ai')?.trim() ?? '';
  const [input, setInput] = useState(aiQuery);
  const [categoryId, setCategoryId] = useState('');
  const [status, setStatus] = useState<WarrantyStatus | ''>('');
  const [sort, setSort] = useState<ProductSort>('recent');
  const [addOpen, setAddOpen] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const { openUpload } = useUpload();

  // Keep the box in sync when an AI query arrives from the global search.
  useEffect(() => {
    if (aiQuery) setInput(aiQuery);
  }, [aiQuery]);

  useEffect(() => {
    if (params.get('focus') === 'search') inputRef.current?.focus();
  }, [params]);

  const keyword = useDebouncedValue(aiQuery ? '' : input.trim(), 250);
  const products = useProducts({ search: keyword || undefined, categoryId: categoryId || undefined, warrantyStatus: status || undefined });
  const { data: categories = [] } = useCategories();
  const hasFilters = Boolean(keyword || categoryId || status);

  function runAiSearch(event?: FormEvent) {
    event?.preventDefault();
    const query = input.trim();
    if (query) setParams({ ai: query });
  }

  function clearAiSearch() {
    setParams({});
    setInput('');
  }

  function clearFilters() {
    setInput('');
    setCategoryId('');
    setStatus('');
  }

  return (
    <>
      <PageHeader
        title="My Products"
        description="Everything you’ve bought, with warranty status at a glance."
        actions={
          <>
            <Button variant="secondary" onClick={() => setAddOpen(true)} leftIcon={<PackagePlus className="size-4" aria-hidden />}>
              Add manually
            </Button>
            <Button onClick={() => openUpload()} leftIcon={<Upload className="size-4" aria-hidden />}>
              Upload bill
            </Button>
          </>
        }
      />

      <form onSubmit={runAiSearch} role="search" className="mb-4">
        <label htmlFor="product-search" className="sr-only">
          Search products or ask in plain English
        </label>
        <div className="flex flex-col gap-2 sm:flex-row">
          <div className="relative flex-1">
            <Search className="pointer-events-none absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-slate-400" aria-hidden />
            <input
              id="product-search"
              ref={inputRef}
              value={input}
              onChange={(event) => setInput(event.target.value)}
              placeholder="Search by name, brand or seller — or ask “warranties expiring in 90 days”"
              className="h-11 w-full rounded-xl border border-slate-300 bg-white pr-10 pl-10 text-sm shadow-xs placeholder:text-slate-400 focus:border-brand-500 focus:ring-4 focus:ring-brand-500/15 focus:outline-none"
            />
            {input && (
              <button
                type="button"
                onClick={aiQuery ? clearAiSearch : () => setInput('')}
                aria-label="Clear search"
                className="absolute top-1/2 right-2 flex size-7 -translate-y-1/2 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100 hover:text-slate-600"
              >
                <X className="size-4" aria-hidden />
              </button>
            )}
          </div>
          <Button type="submit" size="lg" className="h-11" leftIcon={<Sparkles className="size-4" aria-hidden />} disabled={!input.trim()}>
            Ask AI
          </Button>
        </div>
        {!aiQuery && (
          <p className="mt-2 flex flex-wrap items-center gap-1.5 text-xs text-slate-500">
            Try:
            {AI_EXAMPLES.map((example) => (
              <button
                key={example}
                type="button"
                onClick={() => {
                  setInput(example);
                  setParams({ ai: example });
                }}
                className="rounded-full border border-slate-200 bg-white px-2.5 py-1 font-medium text-slate-600 hover:border-brand-300 hover:text-brand-700"
              >
                {example}
              </button>
            ))}
          </p>
        )}
      </form>

      {aiQuery ? (
        <AiSearchResults query={aiQuery} onClear={clearAiSearch} categories={categories} />
      ) : (
        <>
          <div className="mb-5 flex flex-col gap-2 sm:flex-row sm:items-center">
            <div className="grid flex-1 grid-cols-2 gap-2 sm:flex sm:flex-none">
              <Select aria-label="Filter by category" value={categoryId} onChange={(event) => setCategoryId(event.target.value)} className="sm:w-52">
                <option value="">All categories</option>
                {categories.map((category) => (
                  <option key={category.id} value={category.id}>
                    {category.name}
                  </option>
                ))}
              </Select>
              <Select
                aria-label="Filter by warranty status"
                value={status}
                onChange={(event) => setStatus(event.target.value as WarrantyStatus | '')}
                className="sm:w-44"
              >
                <option value="">Any warranty</option>
                {WARRANTY_STATUSES.map((value) => (
                  <option key={value} value={value}>
                    {WARRANTY_STATUS_LABELS[value]}
                  </option>
                ))}
              </Select>
            </div>
            <div className="sm:ml-auto sm:w-56">
              <Select aria-label="Sort products" value={sort} onChange={(event) => setSort(event.target.value as ProductSort)}>
                {Object.entries(PRODUCT_SORT_LABELS).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </Select>
            </div>
          </div>

          {products.isPending ? (
            <ProductGridSkeleton />
          ) : products.isError ? (
            <Card>
              <ErrorState title="Could not load your products" error={products.error} onRetry={() => void products.refetch()} />
            </Card>
          ) : products.data.length === 0 ? (
            <Card>
              {hasFilters ? (
                <EmptyState
                  icon={<SearchX aria-hidden />}
                  title="No products match"
                  description={
                    keyword ? `Nothing matches “${keyword}”. Press “Ask AI” to search in plain English.` : 'Try a different filter.'
                  }
                  action={
                    <Button variant="secondary" onClick={clearFilters}>
                      Clear filters
                    </Button>
                  }
                />
              ) : (
                <EmptyState
                  icon={<Package aria-hidden />}
                  title="No products yet"
                  description="Upload your first bill to get started — AI fills in the product and warranty for you."
                  action={
                    <>
                      <Button onClick={() => openUpload()} leftIcon={<Upload className="size-4" aria-hidden />}>
                        Upload bill
                      </Button>
                      <Button variant="secondary" onClick={() => setAddOpen(true)}>
                        Add manually
                      </Button>
                    </>
                  }
                />
              )}
            </Card>
          ) : (
            <ProductGrid products={sortProducts(products.data, sort)} dimmed={products.isFetching && !products.isPending} />
          )}
        </>
      )}

      <ProductFormDialog open={addOpen} onClose={() => setAddOpen(false)} />
    </>
  );
}

function ProductGrid({ products, dimmed = false }: { products: Product[]; dimmed?: boolean }) {
  return (
    <div className={cn('grid gap-4 transition-opacity sm:grid-cols-2 xl:grid-cols-3', dimmed && 'opacity-60')}>
      {products.map((product) => (
        <ProductCard key={product.id} product={product} />
      ))}
    </div>
  );
}

function ProductGridSkeleton() {
  return (
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3" aria-busy="true" aria-label="Loading products">
      {Array.from({ length: 6 }, (_, index) => (
        <Skeleton key={index} className="h-52 rounded-2xl" />
      ))}
    </div>
  );
}

interface AiSearchResultsProps {
  query: string;
  onClear: () => void;
  categories: Parameters<typeof describeSearchFilters>[1];
}

/** Shows how the AI understood the question (transparency) and the database results. */
function AiSearchResults({ query, onClear, categories }: AiSearchResultsProps) {
  const search = useAiSearch(query);

  return (
    <section aria-label="AI search results" className="space-y-4">
      <Card className="border-brand-100 bg-gradient-to-br from-brand-50/80 to-white p-4 sm:p-5">
        <div className="flex items-start gap-3">
          <span className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-brand-600 text-white">
            <Sparkles className="size-[18px]" aria-hidden />
          </span>
          <div className="min-w-0 flex-1">
            <p className="text-xs font-medium tracking-wide text-brand-700 uppercase">AI search</p>
            <p className="mt-0.5 font-semibold text-slate-900">“{query}”</p>
            {search.isPending ? (
              <p className="mt-2 text-sm text-slate-500">Understanding your question…</p>
            ) : search.isSuccess ? (
              <>
                <p className="mt-1 text-sm text-slate-600">{search.data.explanation}</p>
                {describeSearchFilters(search.data.filters, categories).length > 0 && (
                  <ul className="mt-3 flex flex-wrap gap-1.5" aria-label="Interpreted filters">
                    {describeSearchFilters(search.data.filters, categories).map((chip) => (
                      <li key={chip} className="rounded-full bg-white px-2.5 py-1 text-xs font-medium text-slate-700 ring-1 ring-slate-200">
                        {chip}
                      </li>
                    ))}
                  </ul>
                )}
              </>
            ) : null}
          </div>
          <Button variant="ghost" size="sm" onClick={onClear}>
            Clear
          </Button>
        </div>
      </Card>

      {search.isPending ? (
        <ProductGridSkeleton />
      ) : search.isError ? (
        <Card>
          <ErrorState title="AI search failed" error={search.error} onRetry={() => void search.refetch()} />
        </Card>
      ) : search.data.results.length === 0 ? (
        <Card>
          <EmptyState icon={<SearchX aria-hidden />} title="No matching products" description="Nothing in your locker matches that question." />
        </Card>
      ) : (
        <>
          <p className="text-sm text-slate-500">{pluralize(search.data.results.length, 'product')} found</p>
          <ProductGrid products={search.data.results} />
        </>
      )}
    </section>
  );
}
