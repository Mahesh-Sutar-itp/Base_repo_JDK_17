# Order — Permissions

Authorisation is **entirely method-level**, driven by `@PreAuthorize`. `SecurityConfig`
declares **no request-matcher rules at all** — the Gate D finding recorded in `ADR-002` — so
any endpoint without an annotation is publicly reachable, and there is **no second layer** to
fall back on.

This domain has the most varied authorisation surface in the system: two guard layers on its
admin controller, a standalone authority for checkout, and **three deliberately anonymous
routes**.

---

## 1. The authorities this domain uses

All resolve through `AuthorityCodes` in the **shared-contracts module**, never as string
literals. Gate B requires zero `hasAuthority('...')` literals; exit criterion **X6** pins it.

| Constant | Value | Guards |
|---|---|---|
| `ORDERS_READ` | `orders:read` | `GET /data/orders` |
| `ORDERS_CREATE` | `orders:create` | `POST /data/orders` |
| `ORDERS_UPDATE` | `orders:update` | `PUT`, `PATCH`, **and all three process routes** |
| `ORDERS_DELETE` | `orders:delete` | `DELETE /data/orders` |
| **`CHECKOUT`** | `checkout` | `POST /public/checkout` |

**`orders:update` guards five routes** — `PUT`, `PATCH`, `/confirmation`, `/rejection` and
`/completion`. So a principal who may patch an order may also confirm, reject and complete
one. There is no finer distinction between editing an order and advancing its lifecycle, and
introducing one would be new behaviour.

**`checkout` is the standalone authority** (**BR-ORDER-062**) — the only one that was already
a constant before Gate B, and the only one the seeded `Customer` role holds. The seeded grant
matrix gives `Customer` exactly **one** permission, and this is it.

---

## 2. `DataOrdersController` — both guard layers

```java
@RestController
@RequestMapping("/data/orders")
@Tag(name = "Orders management")
@PreAuthorize("isAuthenticated()")      // <-- class level
public class DataOrdersController extends DataCrudGenericController<OrderPojo, Order> {
```

**BR-ORDER-061** — all order administration requires authentication **plus** an authority.

| Verb | Path | Authority |
|---|---|---|
| GET | `/data/orders` | `orders:read` |
| POST | `/data/orders` | `orders:create` |
| PUT | `/data/orders` | `orders:update` |
| PATCH | `/data/orders` | `orders:update` |
| DELETE | `/data/orders` | `orders:delete` |
| POST | `/data/orders/confirmation` | `orders:update` |
| POST | `/data/orders/rejection` | `orders:update` |
| POST | `/data/orders/completion` | `orders:update` |

**This is the safer of the two shapes in the codebase**, and the contrast is instructive:
`DataShippersController` has **only** the per-method layer, so a future method added there
without an annotation would be public. Here, the class-level `isAuthenticated()` would still
catch it — authenticated callers only, but not anonymous ones.

`DataOrderStatusesController` carries the same class-level guard and exposes **`GET` only**
(**BR-ORDER-065**).

---

## 3. The three anonymous routes

| Route | Verb | Guard | Why anonymous |
|---|---|---|---|
| `/public/checkout/validate` | GET | **none** | the payment provider cannot authenticate (**BR-ORDER-040**) |
| `/public/checkout/validate` | POST | **none** | same |
| `/public/receipt/{token}` | GET | **none** | the token *is* the credential (**BR-ORDER-044**) |

### 3.1 The callbacks, and what actually protects them

**Transbank sends no callback signature.** There is nothing to verify cryptographically, so no
HMAC check is possible, and the mappings carry no `@PreAuthorize` by design.

Before register item **D4**, that meant anyone who could guess or observe a token could drive
a payment transition. **Since D4, the callback must present the per-order nonce** that was
embedded in the `returnUrl` handed to the provider — a value a third party never saw.

> **These two mappings stay anonymous, and the nonce *is* the access control.**

The security property is origin control rather than identity: the caller proves it received
the URL we gave the provider. See `04` §5 for the mechanism and `errors.md` E15 for the
rejection.

**This must not be "improved" during the port by adding an authority check.** Doing so would
break every real payment callback, and it would break them only in production, where a real
provider calls back.

### 3.2 The public receipt

`/public/receipt/{token}` has no guard at either level. The token is a **64-character**
provider-generated value and is unguessable, so knowing it is sufficient.

**BR-ORDER-045** — the lookup is **not status-restricted**, so a receipt is readable from the
moment `markAsStarted` runs, not only "once complete or rejected" as the route's `@Operation`
summary claims (`06` §5.1).

The genuine consequence, recorded rather than fixed: **a receipt is readable forever by anyone
who obtains the token**, and the token reaches the customer's browser through a redirect and
appears in the provider's referrer chain. That is a real if narrow exposure, and it is
BR-ORDER-044's intent.

---

## 4. The one authority with transitive write reach

`checkout` looks like a narrow permission. It is not.

A caller holding `checkout` can, through `POST /public/checkout`:

| Side effect | Table | Mechanism |
|---|---|---|
| create an order | `orders` | the request's purpose |
| create its lines | `order_details` | `details` cascades `PERSIST` |
| **create a customer** | `customers` + `people` | `CustomerProvisioningContract.resolveOrCreateCustomer`, cascaded `PERSIST` |
| **create a billing company** | `billing_companies` | `BillingContract.resolveOrCreateBillingCompany`, cascaded `PERSIST` |
| **create up to two addresses** | `addresses` | cascaded `PERSIST` |

So **one authority named for a read-shaped action writes to six tables across three domains**,
two of which have no administrative API at all (**BR-BILLING-014**). Worth knowing before
anyone reasons about blast radius from the catalogue alone — and worth re-reading after
extraction, when two of those writes become **remote** writes inside a checkout
(`ADR-001`).

---

## 5. How a principal acquires these authorities

Authorities are **not** roles. The chain, assembled at login by the Identity service:

```
app_users → app_user_roles → app_user_role_permissions → app_permissions
```

A user has exactly one role; the role is granted permissions; the permission **codes** become
granted authorities, and those are what `hasAuthority` matches. **Nothing in this system ever
checks a role name.**

Two consequences specific to this domain:

- **The grant matrix lives in the Identity service's `data.sql`** after Phase 2. The seeded
  distribution is Admin 54, Manager 31, Salesperson 14, Customer 1 — and the Customer's single
  permission is `checkout`.
- **Authorities travel inside the JWT and are never re-checked against the database**
  (BR-USER-022). The Orders service verifies tokens locally against the shared signing key per
  `ADR-002`; it never calls Identity to authorise an order write. **So a revoked
  `orders:delete` grant stays effective until the token expires.**

That last point is the most operationally significant fact in this document, and it is a
property of the approved target rather than of this domain.

---

## 6. Guest checkout

A `Guest` principal is produced by `JwtGuestAuthenticationFilter` and holds the authorities of
the seeded guest account — which includes `checkout`. That is what lets an unauthenticated
visitor complete a purchase, and it is why:

- `GET /data/billing_types` is anonymous (BR-BILLING-016),
- `GET /data/shippers` is anonymous (BR-SHIPPER-008),
- and the five catalogue storefront reads are anonymous.

**A guest can therefore place an order and create a customer record**, through the transitive
reach in §4. `JwtGuestAuthenticationFilterOrderingTest` (4 tests) pins the filter ordering
that makes this work; it belongs to the identity domain but the checkout path depends on it.

---

## 7. Porting checklist

| Item | Requirement |
|---|---|
| Authority source | `AuthorityCodes` consumed from `trebol-shared-contracts`; **zero** `hasAuthority('...')` literals in orders-service source (Gate B, X6) |
| Annotations | all five constants on their exact verbs — including `orders:update` guarding **five** routes |
| Class-level guard | `DataOrdersController` and `DataOrderStatusesController` keep `@PreAuthorize("isAuthenticated()")`. **Both layers**, not one |
| The anonymous routes | both `/public/checkout/validate` mappings and `/public/receipt/{token}` keep **no** `@PreAuthorize`, and `PublicCheckoutController` / `PublicReceiptController` keep **no** class-level guard |
| The nonce | D4's callback authentication is the control on the callbacks; it is **not** a substitute for, and not replaced by, an authority check |
| Token verification | local verification against the shared signing key; the Orders service **never calls Identity to authorise** (`ADR-002`) |
| Pinning test | `OrdersRouteParityTest` asserts all 14 routes: the exact authority **value** per guarded verb, the class-level guard on both data controllers, and that the **three anonymous routes demand nothing** |
| Gate B | verified by grep returning zero across all four modules (X6) |
