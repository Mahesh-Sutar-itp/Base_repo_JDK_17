# API Endpoint Catalog

Every HTTP route in the application, extracted from the controller annotations
in `src/main/java/org/trebol/api/controllers/` plus the two authentication
filter URLs declared in `config/SecurityConfig.java`.

**Access column legend**

| Value | Meaning |
|---|---|
| `PUBLIC` | no authentication requirement and no authority requirement |
| `AUTH` | class-level authentication requirement only |
| `AUTH + <code>` | authentication plus the named authority |
| `<code>` | the named authority only (no class-level authentication requirement) |

There are **no route-level authorization rules** in the security configuration,
so `PUBLIC` is the default for any method without an annotation (`BR-USER-025`).

## Complete route table

| # | Route | Verb | Controller | Domain | Access | Success |
|---|---|---|---|---|---|---|
| 1 | `/` | GET | `Root` | `(none - health check)` | **PUBLIC** | 200 |
| 2 | `/access` | GET | `Access` | `user` | AUTH | 200 |
| 3 | `/access/{apiRoute}` | GET | `Access` | `user` | AUTH | 200 |
| 4 | `/account/profile` | GET | `AccountProfile` | `user` | AUTH | 200 |
| 5 | `/account/profile` | PUT | `AccountProfile` | `user` | AUTH | 200 |
| 6 | `/data/billing_types` | GET | `DataBillingTypes` | `billing` | **PUBLIC** | 200 |
| 7 | `/data/customers` | GET | `DataCustomers` | `customer` | AUTH + `customers:read` | 200 |
| 8 | `/data/customers` | POST | `DataCustomers` | `customer` | AUTH + `customers:create` | 201 |
| 9 | `/data/customers` | PUT | `DataCustomers` | `customer` | AUTH + `customers:update` | 204 |
| 10 | `/data/customers` | DELETE | `DataCustomers` | `customer` | AUTH + `customers:delete` | 204 |
| 11 | `/data/images` | GET | `DataImages` | `image` | AUTH + `images:read` | 200 |
| 12 | `/data/images` | POST | `DataImages` | `image` | AUTH + `images:create` | 201 |
| 13 | `/data/images` | PUT | `DataImages` | `image` | AUTH + `images:update` | 204 |
| 14 | `/data/images` | PATCH | `DataImages` | `image` | AUTH + `images:update` | 204 |
| 15 | `/data/images` | DELETE | `DataImages` | `image` | AUTH + `images:delete` | 204 |
| 16 | `/data/order_statuses` | GET | `DataOrderStatuses` | `order` | AUTH + `order_statuses:read` | 200 |
| 17 | `/data/orders` | GET | `DataOrders` | `order` | AUTH + `orders:read` | 200 |
| 18 | `/data/orders` | POST | `DataOrders` | `order` | AUTH + `orders:create` | 201 |
| 19 | `/data/orders` | PUT | `DataOrders` | `order` | AUTH + `orders:update` | 204 |
| 20 | `/data/orders` | PATCH | `DataOrders` | `order` | AUTH + `orders:update` | 204 |
| 21 | `/data/orders` | DELETE | `DataOrders` | `order` | AUTH + `orders:delete` | 204 |
| 22 | `/data/orders/completion` | POST | `DataOrders` | `order` | AUTH + `orders:update` | 200 |
| 23 | `/data/orders/confirmation` | POST | `DataOrders` | `order` | AUTH + `orders:update` | 204 |
| 24 | `/data/orders/rejection` | POST | `DataOrders` | `order` | AUTH + `orders:update` | 200 |
| 25 | `/data/people` | GET | `DataPeople` | `person` | AUTH + `people:read` | 200 |
| 26 | `/data/product_categories` | GET | `DataProductCategories` | `product-category` | **PUBLIC** | 200 |
| 27 | `/data/product_categories` | POST | `DataProductCategories` | `product-category` | `product_categories:create` | 201 |
| 28 | `/data/product_categories` | PUT | `DataProductCategories` | `product-category` | `product_categories:update` | 204 |
| 29 | `/data/product_categories` | PATCH | `DataProductCategories` | `product-category` | `product_categories:update` | 204 |
| 30 | `/data/product_categories` | DELETE | `DataProductCategories` | `product-category` | `product_categories:delete` | 204 |
| 31 | `/data/product_list_contents` | GET | `DataProductListContents` | `product-list` | **PUBLIC** | 200 |
| 32 | `/data/product_list_contents` | POST | `DataProductListContents` | `product-list` | `product_lists:contents` | 201 |
| 33 | `/data/product_list_contents` | PUT | `DataProductListContents` | `product-list` | `product_lists:contents` | 204 |
| 34 | `/data/product_list_contents` | DELETE | `DataProductListContents` | `product-list` | `product_lists:contents` | 204 |
| 35 | `/data/product_lists` | GET | `DataProductLists` | `product-list` | **PUBLIC** | 200 |
| 36 | `/data/product_lists` | POST | `DataProductLists` | `product-list` | `product_lists:create` | 201 |
| 37 | `/data/product_lists` | PUT | `DataProductLists` | `product-list` | `product_lists:update` | 204 |
| 38 | `/data/product_lists` | PATCH | `DataProductLists` | `product-list` | `product_lists:update` | 204 |
| 39 | `/data/product_lists` | DELETE | `DataProductLists` | `product-list` | `product_lists:delete` | 204 |
| 40 | `/data/products` | GET | `DataProducts` | `product` | **PUBLIC** | 200 |
| 41 | `/data/products` | POST | `DataProducts` | `product` | `products:create` | 201 |
| 42 | `/data/products` | PUT | `DataProducts` | `product` | `products:update` | 204 |
| 43 | `/data/products` | PATCH | `DataProducts` | `product` | `products:update` | 204 |
| 44 | `/data/products` | DELETE | `DataProducts` | `product` | `products:delete` | 204 |
| 45 | `/data/salespeople` | GET | `DataSalespeople` | `salesperson` | AUTH + `salespeople:read` | 200 |
| 46 | `/data/salespeople` | POST | `DataSalespeople` | `salesperson` | AUTH + `salespeople:create` | 201 |
| 47 | `/data/salespeople` | PUT | `DataSalespeople` | `salesperson` | AUTH + `salespeople:update` | 204 |
| 48 | `/data/salespeople` | DELETE | `DataSalespeople` | `salesperson` | AUTH + `salespeople:delete` | 204 |
| 49 | `/data/shippers` | GET | `DataShippers` | `shipper` | **PUBLIC** | 200 |
| 50 | `/data/shippers` | POST | `DataShippers` | `shipper` | `shippers:create` | 201 |
| 51 | `/data/shippers` | PUT | `DataShippers` | `shipper` | `shippers:update` | 204 |
| 52 | `/data/shippers` | PATCH | `DataShippers` | `shipper` | `shippers:update` | 204 |
| 53 | `/data/shippers` | DELETE | `DataShippers` | `shipper` | `shippers:delete` | 204 |
| 54 | `/data/user_roles` | GET | `DataUserRoles` | `user` | AUTH + `user_roles:read` | 200 |
| 55 | `/data/user_roles` | POST | `DataUserRoles` | `user` | AUTH + `user_roles:create` | 201 |
| 56 | `/data/user_roles` | PUT | `DataUserRoles` | `user` | AUTH + `user_roles:update` | 204 |
| 57 | `/data/user_roles` | DELETE | `DataUserRoles` | `user` | AUTH + `user_roles:delete` | 204 |
| 58 | `/data/users` | GET | `DataUsers` | `user` | AUTH + `users:read` | 200 |
| 59 | `/data/users` | POST | `DataUsers` | `user` | AUTH + `users:create` | 201 |
| 60 | `/data/users` | PUT | `DataUsers` | `user` | AUTH + `users:update` | 204 |
| 61 | `/data/users` | DELETE | `DataUsers` | `user` | AUTH + `users:delete` | 204 |
| 62 | `/public/about` | GET | `PublicAbout` | `company-profile` | **PUBLIC** | 200 |
| 63 | `/public/checkout` | POST | `PublicCheckout` | `order` | `" + AUTHORITY_CHECKOUT + "` | 200 |
| 64 | `/public/checkout/validate` | GET | `PublicCheckout` | `order` | **PUBLIC** | 200 |
| 65 | `/public/checkout/validate` | POST | `PublicCheckout` | `order` | **PUBLIC** | 200 |
| 66 | `/public/receipt/{token}` | GET | `PublicReceipt` | `order` | **PUBLIC** | 200 |
| 67 | `/public/register` | POST | `PublicRegister` | `user` | **PUBLIC** | 200 |

## Filter-implemented routes (not in any controller)

These two are processed by authentication filters registered in the security
configuration. They have no controller method, no bean validation on their
request bodies, and are invisible to any controller-derived inventory.

| Route | Verb | Filter | Access | Notes |
|---|---|---|---|---|
| `/public/login` | POST | `JwtLoginAuthenticationFilter` | **PUBLIC** | a non-POST request attempts no authentication; the body is deserialised with a directly constructed object mapper, so `LoginPojo` constraints never run |
| `/public/guest` | POST | `JwtGuestAuthenticationFilter` | **PUBLIC** | only functional when `trebol.security.guest-user-enabled` is true; persists a person and customer row **before** authenticating |

Both write the issued token to the `Authorization` response header **and** to the
response body (`BR-USER-016`).

## Access summary

| Access level | Controller routes |
|---|---|
| **PUBLIC** (no authentication, no authority) | 12 |
| authentication only | 4 |
| an authority (with or without class-level authentication) | 51 |
| **total controller routes** | 67 |
| plus filter-implemented routes | 2 |
| **total HTTP routes** | 69 |

## The public surface, examined

Twelve controller routes are reachable with no credentials at all. Nine are
intentional storefront endpoints; three deserve attention:

| Route | Assessment |
|---|---|
| `GET /` | health check, returns an empty body |
| `GET /public/about` | storefront branding — intentional |
| `POST /public/register` | self-registration — intentional |
| `GET /public/receipt/{token}` | **token-addressed with no status restriction**, so anyone holding a token can read an order's totals, date, status and line descriptions (`BR-ORDER-044`, `BR-ORDER-045`) |
| `GET /public/checkout/validate` | **payment provider callback** — public by necessity, but with no signature check, no origin check and no idempotency key; replay protection is incidental (`BR-ORDER-040`, `BR-ORDER-041`) |
| `POST /public/checkout/validate` | same |
| `GET /data/billing_types` | reference data; note **no `billing_types:*` authority exists at all** (`BR-BILLING-019`) |
| `GET /data/shippers` | reference data; `shippers:read` exists, is granted, and is **never checked** (`BR-SHIPPER-009`) |
| `GET /data/products` | storefront catalogue — but it exposes **price and stock levels** anonymously (`BR-PRODUCT-025`) |
| `GET /data/product_categories` | storefront navigation; `product_categories:read` exists and is never checked |
| `GET /data/product_lists` | storefront merchandising; `product_lists:read` exists and is never checked |
| `GET /data/product_list_contents` | storefront merchandising; requires a `listCode` parameter |

## Query-parameter conventions

Every `/data/` listing route accepts one flat parameter map that serves three
purposes simultaneously:

| Purpose | Keys |
|---|---|
| pagination | `pageIndex` (default 0), `pageSize` (default `trebol.api.items-per-page` = 10, capped at `trebol.api.max-allowed-page-size` = 1000) |
| sorting | `sortBy` (must be in the domain's whitelist), `order` (`asc` / `desc`) |
| filtering | domain-specific keys — see each domain's `features.md` |

Hazards that apply to every listing route:

- a non-numeric `pageIndex` or `pageSize` produces an **unhandled** number-format
  failure (a 500-class response);
- an unrecognised `sortBy`, or a `sortBy` with no `order`, produces an
  **unhandled null-dereference** in the sort parser;
- a filter value that will not parse is **logged and dropped**, so the response
  is unfiltered rather than rejected.

## Response-status conventions

| Verb | Status | Exceptions |
|---|---|---|
| GET | 200 | — |
| POST (create) | 201 | `POST /public/register` returns 200; `POST /public/checkout` returns 200 |
| PUT | 204 | `PUT /account/profile` returns 200 |
| PATCH | 204 | — |
| DELETE | 204 | — |
| order actions | 204 for `/confirmation`, **200** for `/rejection` and `/completion` | an inconsistency in the source |
| payment callbacks | **303** with a `Location` header | both validation routes |

## Routes that do NOT exist (commonly assumed)

| Expected route | Status |
|---|---|
| `PATCH /data/customers`, `/data/salespeople`, `/data/users`, `/data/user_roles` | **absent** — the patch services exist and are wired but unreachable |
| any write route on `/data/people` or `/data/order_statuses` | **absent** — read-only controllers |
| any route for billing companies, addresses, payment types, permissions or role grants | **absent** — no controller exists |
| any write route for the company profile | **absent** (`BR-COMPANY-008`) |
| logout, token refresh, password change or reset | **absent** (`BR-USER-045`) |

## Related documentation

- authority grants and enforcement: `../cross-cutting-documentation/common-permissions.md`
- the error envelope returned by failures: `../cross-cutting-documentation/common-error-codes.md`
- per-domain endpoint detail: each `../detailed-domain-bre/domain-*/features.md`

