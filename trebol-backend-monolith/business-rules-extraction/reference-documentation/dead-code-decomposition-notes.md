# Dead Code — Decomposition Notes

Scope limited to dead code that **affects boundary or extraction decisions**.
General unused-code cleanup is out of scope.

Dead code matters for a decomposition in three ways: it can make a domain look
more capable than it is, it can hide the intended mechanism for a rule that is
currently enforced by the database, and it can mislead a boundary decision by
implying a dependency that does not execute.

---

## Category 1 — The intended replacement for a database rule (most important)

These two operations are the **application-level equivalents** of database
behaviours that cannot survive a physical split. They exist, they are correct in
shape, and **nothing calls them**.

### DC-01 — `ProductsRepository.orphanizeByCategories`

| Aspect | Detail |
|---|---|
| File | `jpa/repositories/ProductsRepository.java` |
| What it does | sets the category reference to null for every product in a given collection of categories |
| Callers | **none** |
| What happens instead | `FK_PRODUCT_CAT_ID` has no cascade, so deleting a category with any classified product **fails on the constraint** and surfaces as a 500-class error (`BR-PRODCAT-016`) |
| Decomposition impact | this is the mechanism that makes category deletion safe. Any split of `product` from `product-category` **must** activate it, driven by a category-deleted notification — which does not exist, because the system publishes no events |

### DC-02 — `ProductListItemsRepository.deleteByProductId`

| Aspect | Detail |
|---|---|
| File | `jpa/repositories/ProductListItemsRepository.java` |
| What it does | removes every membership row for a given product |
| Callers | **none** (its sibling `deleteByListId` **is** called, by the list delete override and the contents replacement) |
| What happens instead | `product_list_items.product_id` carries **`ON DELETE CASCADE`** — the only cascade in the whole schema — so product deletion silently shrinks every list with no application code involved (`BR-PRODLIST-023`) |
| Decomposition impact | the cascade cannot cross a service boundary. This method is the intended replacement and would have to be driven by a product-deleted notification. Note the behavioural change: the cascade is atomic with the delete, so an event-based replacement introduces a window in which a deleted product is still listed |

---

## Category 2 — Dead configuration that implies a capability the system lacks

### DC-03 — The entire CORS configuration

| Aspect | Detail |
|---|---|
| Files | `config/CorsConfigurationSourceBuilder.java`, `config/CorsProperties.java`, `config/exceptions/CorsMappingParseException.java` |
| Configuration | `trebol.cors.list-delimiter`, `.allowed-headers`, `.allowed-origins`, and a **36-line** `.mappings` value enumerating 36 route-and-method pairs |
| Why it is dead | the builder is referenced by **no bean**; the security configuration declares no CORS source, so none of this is ever evaluated |
| Decomposition impact | **moderate and easy to get wrong.** The mappings list reads like an authoritative route inventory — it is not. It names `POST /public/guest` and `POST /public/login` (filter routes), omits `GET /public/checkout/validate` (a route that exists), and does not reflect the actual access levels. Do **not** treat it as a specification. Cross-check every route against `api-endpoint-catalog.md` instead |
| Also note | the parse exception's message exposes the offending mapping only through a getter, and its `getMessage()` returns a constant — unreachable either way |

### DC-04 — The phone-number constraint

| Aspect | Detail |
|---|---|
| Files | `common/annotations/PhoneNumber.java`, `common/annotations/validator/PhoneNumberValidator.java` |
| Configuration | `trebol.validation.phonenumber-regexp` |
| Why it is dead | the annotation is applied to **zero fields**; `PersonPojo`'s phone patterns are commented out instead |
| Decomposition impact | low, but it changes the `person` domain's validation story: phone numbers are **entirely unvalidated** today (`BR-PERSON-006`). A rewrite that activates the annotation is adding validation, not porting it. The validator also uses field injection and has no null guard, so activating it as-is would fail on a null value |

### DC-05 — The identification-number pattern

| Aspect | Detail |
|---|---|
| Component | `common/services/impl/RegexMatcherAdapterServiceImpl.isAValidIdNumber` |
| Configuration | `trebol.validation.id-number-regexp` |
| Why it is dead | the method exists and compiles the configured pattern, but **no domain calls it** — not `person`, not `customer`, not `salesperson`, not `billing` |
| Decomposition impact | identification numbers are the **business key** for four domains and are only length-checked. A rewrite must decide deliberately whether to activate the pattern, and if so in which domain |

### DC-06 — The authorization-header validator

| Aspect | Detail |
|---|---|
| Component | `common/services/impl/RegexMatcherAdapterServiceImpl.isAValidAuthorizationHeader` |
| Why it is dead | the token verification filter does not call it; it strips the bearer prefix with a replace-all instead (`BR-USER-021`) |
| Decomposition impact | low; but it is the correct implementation sitting unused next to a defective inline one |

---

## Category 3 — Stale metadata that misleads schema analysis

### DC-07 — `META-INF/persistence.xml`

| Aspect | Detail |
|---|---|
| File | `src/main/resources/META-INF/persistence.xml` |
| Why it is dead | the framework's auto-configuration does not use it; entity scanning is declared in `config/JpaConfig.java` instead |
| Why it is **misleading** | it uses a legacy namespace for a newer persistence specification; it names a persistence unit from an unrelated former project; and critically it **lists a `ProductFamily` class that does not exist in `src/main/java`** while **omitting seven entities that do** (`Address`, `BillingCompany`, `PaymentType`, `ProductList`, `ProductListItem`, `Shipper`, `UserRolePermission`) |
| Decomposition impact | **high risk of a phantom entity.** Any tool or person reading this file as the entity inventory would invent a `ProductFamily` domain and miss seven real tables. The authoritative entity list is the 22 classes in `jpa/entities/`; the authoritative table list is the 22 statements in `../database-documentation/00-global-schema.sql` |

### DC-08 — The stale foreign-key target in the schema dump

| Aspect | Detail |
|---|---|
| File | `src/main/resources/schema-mariadb.sql`, the `order_details` block |
| What is stale | `FK_ORDER_DETAIL_PARENT_ID` references a table named `sales`, which **does not exist** — the owning table was renamed to `orders` |
| Decomposition impact | low for boundaries, high for anyone executing the dump. Recorded verbatim with a TODO in the global schema rather than silently corrected, per the transcription rules |

---

## Category 4 — Unreachable service capability (not dead code, but easily mistaken for it)

These components are fully implemented, wired as beans, and **have no route**.
They are **not** dead code in the usual sense: they are capability that exists in
the service layer and is absent from the API surface. Exposing them is a routing
change, not a logic change.

| Component | Missing route | Rule |
|---|---|---|
| `CustomersPatchServiceImpl` | `PATCH /data/customers` | `BR-CUSTOMER-011` |
| `SalespeoplePatchServiceImpl` | `PATCH /data/salespeople` | `BR-SALESPERSON-011` |
| `UsersPatchServiceImpl` | `PATCH /data/users` | `BR-USER-045` |
| `UserRolesPatchServiceImpl` | `PATCH /data/user_roles` | `BR-USER-044` |
| `BillingTypesPatchServiceImpl` | any write route on billing types | `BR-BILLING-014` |
| `BillingCompaniesCrudServiceImpl` + its converter, patch and predicate services | **any** route at all | `BR-BILLING-014` |
| `OrderStatusesPatchServiceImpl` and the status write paths | any write route on order statuses | `BR-ORDER-065` |

**Decomposition impact:** these inflate a domain's apparent surface. The
`billing` domain in particular has a complete CRUD stack for billing companies
and no controller, so its real API surface is a single public read.

---

## Category 5 — Dead data

### DC-09 — The four `order_types:*` authority codes

Seeded in `app_permissions` and granted (all four to Administrator, partially to
Manager and Salesperson), but referenced by **no code anywhere** in
`src/main/java` (`BR-USER-036`).

**Decomposition impact:** they imply an order-type concept that does not exist in
any entity, table or service. Do not create a domain for them.

### DC-10 — Ten authority codes that are granted but never enforced

Beyond the four above: `products:read`, `product_categories:read`,
`product_lists:read`, `shippers:read` (their routes are public and carry no
check), plus `order_statuses:create|update|delete` and `people:create|update|delete`
(no such routes exist). Full list in
`../cross-cutting-documentation/common-permissions.md`.

**Decomposition impact:** a rewrite that generates authorization from the
catalogue would protect routes that are currently public — a behaviour change
that must be chosen deliberately.

---

## Category 6 — Broken-but-latent code

### DC-11 — `ProductsRepository.deepReadAll(Predicate, Pageable)`

Uses the same query text as its unfiltered sibling, so **the predicate has no
effect**, and its count query selects entities instead of a count
(`BR-PRODUCT-032`).

**Decomposition impact:** latent only — no service calls this overload today. But
it is a trap for a rewrite that assumes the repository surface is correct.

### DC-12 — Deprecated overloads that throw

Every patch service's transport-model overload, `CrudService.update(M)`, and
`OrdersConverterServiceImpl.applyChangesToExistingEntity` all raise an
unsupported-operation failure. They are unreachable by design and exist only to
satisfy interfaces.

**Decomposition impact:** none, but they should not be carried into new
interfaces.

---

## Summary table

| Item | Type | Decomposition impact |
|---|---|---|
| DC-01 orphan-by-categories | uncalled method | **mandatory to activate on a split** |
| DC-02 delete-by-product-id | uncalled method | **mandatory to activate on a split** |
| DC-03 CORS configuration | dead configuration + builder | **misleading route inventory** |
| DC-04 phone constraint | dead annotation | changes the validation story |
| DC-05 identification-number pattern | uncalled method | changes the validation story |
| DC-06 header validator | uncalled method | low |
| DC-07 `persistence.xml` | stale metadata | **phantom-entity risk** |
| DC-08 `sales` foreign-key target | stale schema reference | execution risk only |
| DC-09 `order_types:*` codes | dead data | **phantom-domain risk** |
| DC-10 ten unenforced codes | dead grants | behaviour change if generated |
| DC-11 filtered deep query | broken, latent | trap for a rewrite |
| DC-12 deprecated overloads | unreachable by design | none |
| unreachable patch and CRUD services | capability without a route | inflates apparent surface |

## Recommended handling

1. **Activate** DC-01 and DC-02 — they are the intended mechanisms and are
   required for any split.
2. **Delete** DC-03, DC-07, DC-09, DC-11 and DC-12. Each is actively misleading.
3. **Decide explicitly** on DC-04 and DC-05: activate the patterns or remove the
   configuration. Leaving them configured-but-unapplied is the worst option
   because it implies validation that does not happen.
4. **Fix** DC-06 by using the validator in the token filter.
5. **Treat the unreachable services as a backlog**, not as dead code: each is a
   working capability one route away from being available.
