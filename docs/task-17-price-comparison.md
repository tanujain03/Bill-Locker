# Task 17 — Compare prices before buying

A signed-in user searches for a product name and optional exact model number. The backend makes
one SerpApi Google Shopping request for India (`gl=in`, `hl=en`, `engine=google_shopping`). The
frontend compares returned INR listings, with store filters, model labels and price sorting.
No new dependency: Spring RestClient and the JDK HTTP client handle the external request.

## 1. Setup

Create a **SerpApi** account at https://serpapi.com/ and keep it on the free plan. This integration
uses **serpapi.com**, not the separate SearchAPI.io provider from an earlier example.
Add to the git-ignored `backend/.env` (or backend hosting environment), then restart:

```properties
SERPAPI_API_KEY=your_key_here
SERPAPI_REQUEST_LIMIT=200
```

The key never goes in frontend code, API responses or Git. SerpApi requires `api_key` on its
request URL; our client catches HTTP errors and never logs/returns provider request URLs, bodies
or exceptions. Redirects and automatic application retries are disabled. No live searches are
made by tests, page loading, typing, filters, sorting or background jobs.

The current provider free plan advertises 250 searches/month and 50/hour. Account limits can
change; check https://serpapi.com/pricing and keep paid/automatic renewal options disabled.
The app does not purchase credits or change subscriptions. Usage from other apps/playground calls
also consumes the same provider account allowance and is outside Bill Locker's local counter.

## 2. Backend and rules

`pricecompare`: Controller → PriceComparisonService → repositories. Requests/responses are records.
Only signed-in users can call:

| Endpoint | Job |
|---|---|
| `GET /api/price-comparison/status` | Configured flag, shared remaining budget, cap and daily per-user limit. No provider call. |
| `POST /api/price-comparison/search` | `{query, model}` returns `{query, model, checkedAt, cached, offers}`. |

Query is required, 3–160 characters, and must be a product name/model rather than a URL. Optional
model is at most 80 characters. Search text goes to SerpApi; private locker documents are never
sent. The JWT is the only source of user identity. All errors use the existing ApiError format,
with inline `fieldErrors` for input validation. A missing key gives 503; provider failures give
502 (including provider authentication failures, avoiding signing users out); quota errors give
429. Provider error text and credentials are never exposed.

Hibernate creates unique new tables `price_compare_attempts` and `price_compare_cache`; legacy
tables are untouched. Cache entries belong to one user and normalized query/model. Thirty minutes
is the freshness period, using the Clock bean; cache hits keep the original timestamp and consume
no request. Only normalized response DTOs are stored, not raw metadata/provider credentials.
Empty successful searches are cached too. No shared search history or cross-user cache access.

New requests reserve a persisted attempt **before** the network call. The default cap is 200 in
any rolling 31-day window across the whole app (configuration clamped to 0–250), plus a shared
40-attempt rolling-hour cap and five attempts per user per calendar day using the Clock. This is
a conservative local budget, not a copy of the provider's billing-cycle counter. PostgreSQL's
transaction advisory lock serializes budget check + insert across backend instances. Failed,
timed-out and empty requests all count locally. Restarts do not reset counters. Cache lookup
happens first, so cached queries still work when new searches are capped. No pagination or extra
product-detail/retailer calls are made automatically; explicit card clicks can resolve a retailer link.

The parser reads standard, categorized and inline shopping results; prices must be positive INR
amounts with at most two decimals. Foreign currency, missing/unreadable prices and unsafe links
are omitted. Equal listings are deduplicated. Link destinations are restricted to HTTPS Amazon
India, Flipkart, Myntra, AJIO and Google product/search listings. A Google link is labelled as a
Google listing, rather than pretending it goes directly to a retailer. Links open with noopener
and noreferrer. No arbitrary URL is fetched by the backend.

`MODEL_MATCH` means the supplied model appears in the listing title, allowing punctuation
variation and checking alphanumeric boundaries. It is **not** proof of equal size/colour/strap
or authentic seller. Other results are `POSSIBLE_MATCH`. Instalments, apparent conditional
prices and used/refurbished conditions are flagged. Sponsored inline results are labelled.
Default ordering preserves provider relevance; users may explicitly sort by price. No invented price, guaranteed cheapest claim,
checkout verification, bank-discount calculation or guessed missing retailer price.

## 3. Frontend

`/compare-prices` and the sidebar/mobile drawer entry **Compare prices** open the page.
`lib/price-comparison.ts` contains types and calls the sole `api.ts` fetch wrapper. Explicit submit
searches, with in-flight guards, visible errors, labelled form fields and no automatic retries.
Results include timestamps and cached labels, store filters, sorting,
copy comparison, model/conditional/sponsored labels and links. Status uses icons and words.
Layouts stack on mobile. Missing retailer results say “No offer found in this search”, without
claiming the product is unavailable. No price tracking or alerts in this version.

## 4. Verification

MockMvc tests use the existing PostgreSQL Testcontainers and Gemini/Google fakes plus a primary
fake shopping provider. Tests cover login, validation, missing configuration, user-scoped cache,
30-minute expiry, persisted failed requests, budget/hour/day limits, parser currency/paise/model
boundaries/link filtering, error sanitization and the fixed SerpApi request. Frontend Node tests
cover API methods, errors and retailer/link labels. Run backend `mvnw test`, frontend typecheck,
production build and `node --test` with the files in `frontend/tests/`.

Live retailer coverage requires a configured key and real user searches. Results are observations
from Google Shopping and may omit any of the four retailers or differ from checkout prices.

A manual development check on 19 October 2026 confirmed the configured account was free and made
one successful live Casio watch search. Amazon and Myntra appeared; Flipkart and AJIO did not.
Representative public listings are saved as a test fixture without provider/account metadata or
credentials. The response contains Google product URLs with unescaped spaces; these are normalized
before validation. Automated tests never call the real provider.

## 5. Open the retailer's product page

Minimal cards show a store badge, product title, prominent price and delivery details. Only the Visit store button opens the retailer; clicking the card itself does nothing.
Google listing fallback links appear as buttons beside Visit store on eligible cards, including
after a store URL has been resolved. Both actions are keyboard-accessible and wrap on narrow screens. Cards stack on mobile.
When a listing already includes a direct retailer URL, it opens immediately without another search.
Google-only listings keep their SerpApi immersive-product token (not an API key). On an explicit
click, `POST /api/price-comparison/store-link` takes `{search:{query,model},url,seller}`. The service
finds that user's unexpired cached offer, reserves one request through the same quotas, and calls
SerpApi's fixed `google_immersive_product` endpoint with the cached token. Caller-provided tokens
and provider URLs are never fetched. Old cached search formats are invalidated by a cache-key version.

Only a product URL for the selected retailer is returned; the resolver never substitutes another
retailer. Amazon/Flipkart/Myntra/AJIO require matching URL domains, while other stores require a
matching provider store name or seller domain. Links must be HTTPS, without credentials, custom
ports, IP literals or local hostnames. The backend does not fetch these merchant URLs. Direct
URLs and successful lookups with no matching retailer are cached for the original search's
remaining 30-minute lifetime, avoiding repeated quota spending. Provider errors remain retryable.
The price/timestamp stays the original search observation and may differ at checkout.

A click opens a temporary tab during the user gesture to avoid popup blockers, with no opener and
no referrer. It navigates to the resolved store; failures close it and show a message. An explicit
Google listing link remains as a fallback. One extra lookup may consume a free-plan search, but
there are no eager lookups for every result, automatic pagination or purchased credits.

Mocked tests cover fixed provider endpoints, owned-search lookup, forged/cross-user offers,
cache reuse, matching retailer domains and unsafe destinations. A manual free-plan check on
20 October 2026 successfully obtained Amazon, Flipkart and Myntra product URLs for one watch.

## 6. Product relevance

Matching happens locally after the existing Shopping request, without extra provider calls.
The leading meaningful query word anchors the brand/product; at least 60% of meaningful query
words must occur as whole title words (or the supplied exact model must match), otherwise the offer is omitted. This is a conservative
text heuristic, not brand detection. Search filler words are ignored, and case/Unicode are normalized.
A typo is not silently corrected: use the exact spelling and a specific product name.

All meaningful words, the optional model, and requested SPF/quantity must match for a matching
product label. Otherwise retained related listings are classified as alternatives internally. SPF and g/ml/kg/l quantities are parsed locally;
equivalent units are normalized, but grams and millilitres are never treated as equivalent.
Absent specifications are not assumed to match. Other attributes, such as colour, are title words;
pack counts and product identity are not independently verified. Matching products still require
verification at the store. Unrelated results are omitted.

The existing minimal-card UI, labels and filters remain unchanged at the user’s request.
Backend classifications do not add sections or alter card styling. Provider order is preserved
unless the user explicitly sorts by price. Price, conditional and sponsored warnings,
link validation and request quotas remain unchanged. The cache-key version invalidates old relevance
results. Unit tests cover unrelated brands/products, misspellings, SPF/size differences, equivalent
units, model boundaries and provider order. This does not broaden allowed merchant links or change
the separate explicit store-link lookup behavior.

## 7. Information panel

The info icon beside Compare prices opens a native, keyboard-accessible popover (Escape,
outside click or Close dismiss it). Request/cache explanations, provider attribution and the
checkout disclaimer live here instead of separate page text or a banner.

The authenticated status endpoint now includes `dailyRemaining`, `hourlyRemaining` and
`dailyResetsAt`, using only the JWT user and the Clock. It does not call SerpApi. The panel shows
the app’s shared rolling budget, the caller’s daily allowance and the minimum currently available
under shared/day/hour caps. These are local counters, not a live provider-account balance.
Daily reset is displayed in the browser’s local time from the server’s reset instant.
Searches and store lookups refresh status; cached results and Google links use no additional quota.
