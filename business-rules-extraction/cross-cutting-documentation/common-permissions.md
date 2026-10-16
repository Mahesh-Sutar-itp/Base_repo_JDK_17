# Common Permissions

The complete authority catalogue, its role grants, and — critically — whether
each code is actually enforced by any route.

Sources: `../database-documentation/00-seed-data.json` (the 54 seeded codes and
their grants), and the controller annotations enumerated in
`../reference-documentation/api-endpoint-catalog.md`.

## Authority catalogue with grants and enforcement

Legend: granted = yes/no per role; **enforced by** names the route(s) that
check the code, or `NEVER ENFORCED` when no code path checks it.

| # | Authority code | Owning domain | Adm | Mgr | Sls | Cst | Enforced by |
|---|---|---|:-:|:-:|:-:|:-:|---|
| 1 | `product_categories:delete` | `product-category` | yes | yes | - | - | DELETE /data/product_categories |
| 2 | `product_categories:create` | `product-category` | yes | yes | - | - | POST /data/product_categories |
| 3 | `product_categories:update` | `product-category` | yes | yes | yes | - | PUT + PATCH /data/product_categories |
| 4 | `product_categories:read` | `product-category` | yes | yes | yes | - | **NEVER ENFORCED** |
| 5 | `order_statuses:delete` | `order` | yes | - | - | - | **NEVER ENFORCED** |
| 6 | `order_statuses:create` | `order` | yes | - | - | - | **NEVER ENFORCED** |
| 7 | `order_statuses:update` | `order` | yes | - | - | - | **NEVER ENFORCED** |
| 8 | `order_statuses:read` | `order` | yes | - | yes | - | GET /data/order_statuses |
| 9 | `order_types:delete` | `(none - dead)` | yes | - | - | - | **NEVER ENFORCED** |
| 10 | `order_types:create` | `(none - dead)` | yes | - | - | - | **NEVER ENFORCED** |
| 11 | `order_types:update` | `(none - dead)` | yes | - | - | - | **NEVER ENFORCED** |
| 12 | `order_types:read` | `(none - dead)` | yes | yes | yes | - | **NEVER ENFORCED** |
| 13 | `customers:delete` | `customer` | yes | - | - | - | DELETE /data/customers |
| 14 | `customers:create` | `customer` | yes | - | - | - | POST /data/customers |
| 15 | `customers:update` | `customer` | yes | - | - | - | PUT /data/customers |
| 16 | `customers:read` | `customer` | yes | yes | yes | - | GET /data/customers |
| 17 | `products:delete` | `product` | yes | yes | yes | - | DELETE /data/products |
| 18 | `products:create` | `product` | yes | yes | yes | - | POST /data/products |
| 19 | `products:update` | `product` | yes | yes | yes | - | PUT + PATCH /data/products |
| 20 | `products:read` | `product` | yes | yes | yes | - | **NEVER ENFORCED** |
| 21 | `orders:delete` | `order` | yes | - | - | - | DELETE /data/orders |
| 22 | `orders:create` | `order` | yes | - | yes | - | POST /data/orders |
| 23 | `orders:update` | `order` | yes | yes | - | - | PUT + PATCH /data/orders, POST /confirmation /rejection /completion |
| 24 | `orders:read` | `order` | yes | yes | yes | - | GET /data/orders |
| 25 | `salespeople:delete` | `salesperson` | yes | - | - | - | DELETE /data/salespeople |
| 26 | `salespeople:create` | `salesperson` | yes | yes | - | - | POST /data/salespeople |
| 27 | `salespeople:update` | `salesperson` | yes | yes | - | - | PUT /data/salespeople |
| 28 | `salespeople:read` | `salesperson` | yes | yes | yes | - | GET /data/salespeople |
| 29 | `users:delete` | `user` | yes | - | - | - | DELETE /data/users |
| 30 | `users:create` | `user` | yes | - | - | - | POST /data/users |
| 31 | `users:update` | `user` | yes | - | - | - | PUT /data/users |
| 32 | `users:read` | `user` | yes | - | - | - | GET /data/users |
| 33 | `user_roles:delete` | `user` | yes | - | - | - | DELETE /data/user_roles |
| 34 | `user_roles:create` | `user` | yes | - | - | - | POST /data/user_roles |
| 35 | `user_roles:update` | `user` | yes | - | - | - | PUT /data/user_roles |
| 36 | `user_roles:read` | `user` | yes | yes | - | - | GET /data/user_roles |
| 37 | `people:delete` | `person` | yes | - | - | - | **NEVER ENFORCED** |
| 38 | `people:create` | `person` | yes | - | - | - | **NEVER ENFORCED** |
| 39 | `people:update` | `person` | yes | - | - | - | **NEVER ENFORCED** |
| 40 | `people:read` | `person` | yes | yes | yes | - | GET /data/people |
| 41 | `images:delete` | `image` | yes | yes | - | - | DELETE /data/images |
| 42 | `images:create` | `image` | yes | yes | - | - | POST /data/images |
| 43 | `images:update` | `image` | yes | yes | - | - | PUT + PATCH /data/images |
| 44 | `images:read` | `image` | yes | yes | - | - | GET /data/images |
| 45 | `checkout` | `order` | yes | yes | yes | yes | POST /public/checkout |
| 46 | `shippers:delete` | `shipper` | yes | yes | - | - | DELETE /data/shippers |
| 47 | `shippers:create` | `shipper` | yes | yes | - | - | POST /data/shippers |
| 48 | `shippers:update` | `shipper` | yes | yes | - | - | PUT + PATCH /data/shippers |
| 49 | `shippers:read` | `shipper` | yes | yes | - | - | **NEVER ENFORCED** |
| 50 | `product_lists:delete` | `product-list` | yes | yes | - | - | DELETE /data/product_lists |
| 51 | `product_lists:create` | `product-list` | yes | yes | - | - | POST /data/product_lists |
| 52 | `product_lists:update` | `product-list` | yes | yes | - | - | PUT + PATCH /data/product_lists |
| 53 | `product_lists:read` | `product-list` | yes | yes | - | - | **NEVER ENFORCED** |
| 54 | `product_lists:contents` | `product-list` | yes | yes | - | - | POST + PUT + DELETE /data/product_list_contents |

## Grant counts

| Role | Codes granted | Of 54 |
|---|---|---|
| Administrator | 54 | 100% |
| Manager | 31 | 57% |
| Salesperson | 14 | 25% |
| Customer | 1 | 1% |

## Codes that are granted but never enforced (14 of 54)

- `product_categories:read`
- `order_statuses:delete`
- `order_statuses:create`
- `order_statuses:update`
- `order_types:delete`
- `order_types:create`
- `order_types:update`
- `order_types:read`
- `products:read`
- `people:delete`
- `people:create`
- `people:update`
- `shippers:read`
- `product_lists:read`

Two distinct problems are mixed in that list:

1. **Dead codes.** The four `order_types:*` entries are referenced by no code
   anywhere in `src/main/java` — a removed feature left behind in the seed data
   (`BR-USER-036`). The `order_statuses:create|update|delete` and `people:*`
   write codes are similar: the corresponding routes do not exist, because both
   controllers expose a read method only.
2. **Unprotected public reads.** `products:read`, `product_categories:read`,
   `product_lists:read` and `shippers:read` exist and are granted, but their read
   routes carry **no authority annotation at all** and are therefore publicly
   reachable (`BR-USER-037`). Granting or revoking these four changes nothing.

## Resources with NO authority code at all

The inverse gap. These resources are reachable (or would be, if a route
existed) with no possible role-based restriction:

| Resource | Route status | Consequence |
|---|---|---|
| `billing_types` | `GET /data/billing_types` exists and is **public** | billing is outside the role model entirely (`BR-BILLING-019`) |
| `billing_companies` | no route at all | unreachable, so unprotected by accident |
| `addresses` | no route at all | same (`BR-ORDER-058`) |
| `payment_types` | no route at all | same (`BR-ORDER-060`) |
| `app_params` (company profile) | `GET /public/about` exists and is **public**; no write route | the profile cannot be managed or protected (`BR-COMPANY-008`) |
| `app_permissions`, `app_user_role_permissions` | no route at all | the authority catalogue itself has no administration surface (`BR-USER-038`) |

## Access levels across the whole API

| Level | Routes |
|---|---|
| **public, no authority** | `GET /`, `GET /public/about`, `GET /public/receipt/{token}`, `POST /public/register`, `GET` and `POST /public/checkout/validate`, `GET /data/billing_types`, `GET /data/shippers`, `GET /data/products`, `GET /data/product_categories`, `GET /data/product_lists`, `GET /data/product_list_contents` |
| **public filters** | `POST /public/login`, `POST /public/guest` (authentication endpoints) |
| **authority `checkout`** | `POST /public/checkout` |
| **authenticated, no authority** | `GET` and `PUT /account/profile`, `GET /access`, `GET /access/{apiRoute}` |
| **authenticated + a resource authority** | everything else under `/data/` |

There are **no route-level authorization rules** in the security configuration —
authorization is entirely method-level, so an endpoint without an annotation is
public by default (`BR-USER-025`). The six public `/data/` reads above are a
direct consequence.

## Role design summary

| Role | Character | Notable gaps |
|---|---|---|
| Administrator | holds **every** code | — |
| Manager | broad read plus catalogue, image, list, shipper and category writes; order read and update | holds **no** `users:*` code at all, so cannot manage accounts; holds `user_roles:read` only |
| Salesperson | order create and read, product writes, customer/people/salespeople reads, category update and read | can create an order but **cannot** change its status (no `orders:update`) |
| Customer | **exactly one** code: `checkout` | cannot read or edit their own customer record through `/data/customers`; must use `/account/profile` instead (`BR-CUSTOMER-020`) |

The Customer role holding a single authority is the clearest evidence that the
`/data/` surface is operator-facing and the `/public/` plus `/account/` surfaces
are buyer-facing.

## Authority code convention

`resource:action` for 53 of the 54 codes. The one exception is the standalone
`checkout`, which is also the only code declared as a constant in
`config/Constants.java` — every other code exists solely as a **string literal**
duplicated in controller annotations, with no compile-time check
(`BR-USER-031`).

The route-discovery feature (`GET /access`, `GET /access/{apiRoute}`) depends on
this convention: it derives a route prefix by stripping everything from the colon
onward, and matches actions by **substring** rather than equality — so asking
about `product` returns the actions of `products:*`, `product_lists:*` **and**
`product_categories:*` (`BR-USER-032`, `BR-USER-033`).

## Related documentation

- authentication and the enforcement mechanism: `authentication-authorization.md`
- the complete route list: `../reference-documentation/api-endpoint-catalog.md`
- the seeded grants as data: `../database-documentation/00-seed-data.json`
- identity rules in full: `../detailed-domain-bre/domain-user/business-rules.md`
