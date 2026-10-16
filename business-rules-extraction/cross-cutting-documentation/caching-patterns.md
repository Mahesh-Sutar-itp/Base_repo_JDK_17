# Caching Patterns

## Finding: there is no caching abstraction

A repository-wide search for every caching mechanism available to this stack
returned **zero matches**:

| Searched for | Matches |
|---|---|
| caching enablement annotation | 0 |
| cacheable / cache-evict / cache-put annotations | 0 |
| cache manager beans or configuration | 0 |
| distributed cache clients | 0 |
| second-level ORM cache configuration | 0 |
| HTTP cache headers set by application code | 0 |

Every request re-queries the database. This is an **absence recorded as a
finding**.

## The only memoisation that exists

| Component | What it caches | Scope | Thread safety |
|---|---|---|---|
| `common/services/impl/RegexMatcherAdapterServiceImpl.java` | two compiled regular-expression patterns — the identification-number pattern and the `^Bearer .+$` authorization-header pattern | per bean instance, for the application's lifetime | **lazy initialisation is not thread-safe**; the race is benign because recompiling yields an equivalent pattern |
| `config/SecurityConfig.java` | the authentication manager instance | per bean instance | lazily memoised; note the construction-order caveat recorded in `authentication-authorization.md` |
| `src/test/java/org/trebol/testing/*` | fixture instances, reset between tests | per test class | not production code |

Nothing else in the codebase retains a computed value across requests.

## Where the absence of caching is most visible

Ordered by request frequency against query cost:

| Endpoint | Why it is a caching candidate |
|---|---|
| `GET /public/about` | **the clearest case.** Public, called by every storefront client on load, returns four parameter rows that change essentially never, and re-queries on every call (`BR-COMPANY-011`) |
| `GET /data/products?categoryCode=...` | public, and each call triggers **recursive** category-branch resolution — one query per tree level, up to the configured depth of 20 (`BR-PRODCAT-010`) |
| `GET /data/product_categories?rootCode=...` | same recursive resolution, same cost |
| `GET /data/product_list_contents?listCode=...` | public; eagerly loads the full product for every membership row (`BR-PRODLIST-020`) |
| `GET /data/billing_types`, `GET /data/shippers` | public reads of tiny, near-static reference tables |
| authority resolution at login | role → grants → authority codes, re-queried on every authentication |

## The one implicit cache: authorities in the token

Authorities are resolved from the database **once, at login**, then embedded in
the issued token and trusted for its lifetime (`BR-USER-022`). This is
effectively a client-held cache with a time-to-live equal to
`trebol.security.jwt-expiration-after-*` (30 minutes by default).

Consequences, which a rewrite must decide on deliberately:

- revoking a role grant has **no effect** until the holder's token expires;
- there is no invalidation mechanism, because there is no logout, no revocation
  list and no refresh (`BR-USER-024`);
- a split that re-resolves authorities per request would be correct but chattier.

## Cache-invalidation hazards a rewrite must plan for

If caching is introduced, these are the mutation points that would need to
invalidate it. They are listed because none of them currently publishes anything
(see `event-publishing-patterns.md`), so invalidation would have to be wired by
hand:

| Cached data | Invalidated by |
|---|---|
| company profile | a direct database edit — there is **no write route at all** (`BR-COMPANY-008`), so no application hook exists to invalidate on |
| category tree / descendant sets | category create, replace, patch, delete; also a **cycle** created by replacement would poison a cached descendant set (`BR-PRODCAT-009`) |
| product reads | product create, replace, patch, delete; **also** image-link rebuilds, and category changes that alter branch membership |
| product-list contents | list contents add, replace, remove; **and** any product deletion, which silently shrinks lists through a database cascade with no application involvement (`BR-PRODLIST-023`) |
| reference tables (billing types, shippers, order statuses) | writes are either unreachable or rare; the order-status catalogue is read-only |
| authorities | role-grant changes, which have **no API** at all (`BR-USER-038`) |

The company-profile and authority rows are the awkward cases: both are mutated
outside the application, so a cache over either needs a time-to-live rather than
event-driven invalidation.

## Related documentation

- the recursive resolver's cost and depth limit: `../detailed-domain-bre/domain-product-category/business-rules.md`
- the token-as-cache behaviour: `authentication-authorization.md`
- why invalidation has nothing to hook onto: `event-publishing-patterns.md`
