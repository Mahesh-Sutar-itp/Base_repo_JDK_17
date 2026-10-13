# Requirements Summary

Consolidated view of what the Trébol backend monolith does, derived from the
twelve per-domain extractions in this directory. All counts in this file are
verifiable against the filesystem with the commands shown.

## Verified counts

| Metric | Value | How it was obtained |
|---|---|---|
| main source files (Java) | 294 | `find src/main/java -name "*.java" \| wc -l` |
| main lines of code | 17,800 | `find src/main/java -name "*.java" \| xargs wc -l` |
| ORM entity classes | 22 | `grep -rl "@Entity" src/main/java` minus 3 non-entity files |
| database tables | 22 | `grep -c "^CREATE TABLE" database-documentation/00-global-schema.sql` |
| controllers | 22 | files in `api/controllers/` plus 4 base classes in `api/` |
| repositories | 23 | files in `jpa/repositories/` |
| transport models | 25 | files in `api/models/` |
| crud service implementations | 14 | files in `jpa/services/crud/impl/` |
| converter implementations | 15 | files in `jpa/services/conversion/impl/` |
| patch implementations | 14 | files in `jpa/services/patch/impl/` |
| predicate implementations | 15 | files in `jpa/services/predicates/impl/` |
| sort specifications | 14 | files in `jpa/sortspecs/` |
| test classes | 108 | `find src/test -name "*Test.java" \| wc -l` |
| domains identified | 12 | `find detailed-domain-bre -name overview.md \| wc -l` |
| business rules extracted | 327 | `grep -Ec "^##+ BR-"` summed over all domains |
| features extracted | 139 | `grep -c "^## F-"` summed over all domains |
| seeded authority codes | 54 | `database-documentation/00-seed-data.json` |
| seeded roles | 4 | Administrator, Manager, Salesperson, Customer |
| seeded order statuses | 12 | codes -6 to 6, excluding 0 |

## Rules and features by domain

| Domain | Rules | Features | Owned tables | Complexity |
|---|---|---|---|---|
| `order` | 70 | 23 | 5 | large |
| `user` | 55 | 15 | 4 | large |
| `product` | 32 | 13 | 2 | medium |
| `product-list` | 30 | 16 | 2 | medium |
| `salesperson` | 23 | 8 | 1 | small |
| `customer` | 22 | 11 | 1 | small |
| `product-category` | 20 | 14 | 1 | medium |
| `billing` | 19 | 10 | 2 | small |
| `image` | 15 | 8 | 1 | small |
| `shipper` | 15 | 9 | 1 | small |
| `person` | 13 | 7 | 1 | small |
| `company-profile` | 13 | 5 | 1 | small |
| **total** | **327** | **139** | **22** | — |

`order` and `user` together account for 38% of the rules, which matches their
identification as the two orchestrating domains in
`cross-domain-features.md`.

## What the system is

A single-tenant e-commerce backend exposing a REST API for:

- a **public storefront surface** — company profile, product catalogue, category
  tree, product lists, billing types, shippers, checkout, payment callbacks and
  receipt lookup;
- an **authenticated operator surface** — CRUD over products, categories, lists,
  images, customers, salespeople, people, accounts, roles, orders and order
  statuses, plus three order-progression actions;
- an **account surface** — self-registration, login, guest sessions, own-profile
  read and replace, and authority discovery.

Payment is delegated to Transbank Webpay Plus. Notification is delegated to
Mailgun and is optional. There is no other external integration.

## Where the real business logic lives

Of 17,800 lines, the overwhelming majority is generic CRUD plumbing —
controllers, converters, patch services, predicate services and sort
specifications that follow one repeated shape. The genuine business logic is
concentrated in four places:

| Logic | Component | Rules |
|---|---|---|
| the order status machine | `OrdersProcessServiceImpl` | `BR-ORDER-020` … `BR-ORDER-027` |
| monetary arithmetic (19% extracted tax, integer accumulation) | `OrdersConverterServiceImpl` | `BR-ORDER-014` … `BR-ORDER-019` |
| the processed-order edit lock | `OrdersCrudServiceImpl` | `BR-ORDER-029` … `BR-ORDER-031` |
| authority resolution and token issuance | `UserDetailsServiceImpl`, `UserPermissionsServiceImpl`, the JWT filters | `BR-USER-005`, `BR-USER-014` … `BR-USER-033` |

Two secondary pieces deserve mention: the recursive category tree resolver
(`BR-PRODCAT-010`, `BR-PRODCAT-011`) and the product image-link rebuild
(`BR-PRODUCT-011`).

Everything else is mechanical. A rewrite that reproduces these four pieces
faithfully and regenerates the rest from the schema will be behaviourally
equivalent.

## System-wide absences (documented findings, not omissions)

| Capability | Status | Evidence |
|---|---|---|
| domain events / messaging | **absent** | no publisher, no listener, no broker client anywhere |
| caching | **absent** | no caching abstraction; the only memoisation is a lazy regular-expression cache |
| scheduled / asynchronous work | **absent** | no scheduled annotation, no task executor, no async method |
| stock control | **absent** | stock is stored and never read; an out-of-stock product can be ordered and paid for (`BR-PRODUCT-022`, `BR-ORDER-012`) |
| transport / shipping cost | **absent** | the column is mandatory, sortable, excluded from the total and set by no code (`BR-ORDER-017`) |
| discounts, coupons, promotions | **absent** | no such concept in any entity or service |
| refunds, returns, delivery tracking | **absent** | four order statuses are seeded and unreachable (`BR-ORDER-025`) |
| password change or reset | **absent** | the password is settable only at creation or full replacement (`BR-USER-045`) |
| logout, token revocation, token refresh | **absent** | tokens are stateless and valid until expiry (`BR-USER-024`) |
| account lockout or status | **absent** | all four account-status flags are hardcoded permissive (`BR-USER-023`) |
| authority catalogue administration | **absent** | no route exposes permissions or grants (`BR-USER-038`) |
| company-profile administration | **absent** | no write route for `app_params` (`BR-COMPANY-008`) |
| billing company administration | **absent** | the full service stack exists with no controller (`BR-BILLING-014`) |
| address or payment-type administration | **absent** | no controller or crud service for either (`BR-ORDER-058`, `BR-ORDER-060`) |
| invoice or receipt document generation | **absent** | the receipt is a JSON projection (`BR-BILLING-018`) |
| audit trail | **absent** | only ad-hoc log statements |
| feature-flag framework | **absent** | four boolean configuration toggles plus one profile act as the only flags |

## Unreachable-but-implemented capabilities

These are **wired beans with no route**, so they are porting opportunities rather
than missing work:

| Capability | Component | Rule |
|---|---|---|
| customer partial update | `CustomersPatchServiceImpl` | `BR-CUSTOMER-011` |
| salesperson partial update | `SalespeoplePatchServiceImpl` | `BR-SALESPERSON-011` |
| account partial update (never the password) | `UsersPatchServiceImpl` | `BR-USER-045` |
| role partial update | `UserRolesPatchServiceImpl` | `BR-USER-044` |
| billing-type create / replace / patch / delete | `BillingTypesCrudServiceImpl` | `BR-BILLING-014` |
| billing-company full CRUD | `BillingCompaniesCrudServiceImpl` | `BR-BILLING-014` |
| order-status create / replace / patch / delete | `OrderStatusesCrudServiceImpl` | `BR-ORDER-065` |
| product orphaning by category | `ProductsRepository.orphanizeByCategories` | `BR-PRODCAT-016` |
| list-item cleanup by product | `ProductListItemsRepository.deleteByProductId` | `BR-PRODLIST-024` |

## Defects recorded as observed behaviour

| Defect | Rule |
|---|---|
| the order status machine is bypassable by patching | `BR-ORDER-028` |
| the edit lock does not cover full replacement | `BR-ORDER-031` |
| the protected-account guard misses multi-row deletes | `BR-USER-042` |
| full token values are written to the application log | `BR-USER-020` |
| an expired token proceeds anonymously instead of failing | `BR-USER-019` |
| registration is non-transactional across three writes | `BR-USER-012` |
| a payment-commit failure is treated as a decline | `BR-ORDER-036` |
| the payment callbacks have no authenticity or idempotency check | `BR-ORDER-040` |
| the order patch type-failure message is truncated to `The ` | `BR-ORDER-033` |
| the shipper blank-name message reads `Billing type has no name` | `BR-SHIPPER-003` |
| the filtered deep product query ignores its predicate | `BR-PRODUCT-032` |
| the product-list contents total reports list size, not filtered size | `BR-PRODLIST-030` |
| product-list contents replacement is destructive before constructive | `BR-PRODLIST-016` |
| unresolvable products are silently skipped on list operations | `BR-PRODLIST-015` |
| dropped product images are reported as success | `BR-PRODUCT-013` |
| the status-code transport constraint is invalid for its type | `OrderStatusPojo`, `domain-order/validations.md` |
| category cycles are creatable and undetected | `BR-PRODCAT-009` |
| duplicate salespeople for one person are possible | `BR-SALESPERSON-002` |
| the configured CORS mappings are never wired into the filter chain | `../reference-documentation/dead-code-decomposition-notes.md` |
| the phone-number constraint is declared and applied nowhere | `BR-PERSON-006` |
| four `order_types:*` authorities are granted and referenced by no code | `BR-USER-036` |
| four seeded read authorities are never enforced | `BR-USER-037` |
| bootstrap credentials are committed to source control | `BR-USER-055` |

## Access-control summary

| Exposure | Routes |
|---|---|
| **public, no authority** | health check, company profile, product catalogue read, category tree read, product list read, list contents read, billing types read, shipper read, receipt lookup, self-registration, both payment callbacks |
| **authority `checkout`** | cart submission |
| **authenticated, no authority** | own-profile read and replace, authority discovery |
| **authenticated + a resource authority** | everything under `/data/` except the five public reads above |

There are **no route-level authorization rules** — authorization is entirely
method-level, so any endpoint without an annotation is public by default
(`BR-USER-025`).
