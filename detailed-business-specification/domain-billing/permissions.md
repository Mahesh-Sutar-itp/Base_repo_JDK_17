# Billing — Permissions

**This domain has no authorities.** That is not an omission in this document; it is the
finding.

---

## 1. There is no billing authority in the catalogue

The generated `AuthorityCodes` catalogue in `trebol-shared-contracts` holds **54** codes,
seeded from `00-seed-data.json`. **None of them governs billing data.** There is no
`billing_types:read`, no `billing_companies:*`, no `addresses:*` — not unenforced ones, not
dead ones. They do not exist.

**BR-BILLING-019** states the consequence directly: billing data **cannot be governed by
role**, because there is no code for a role to be granted.

Contrast the shipper domain, which *does* have four codes and merely fails to enforce one of
them (`shippers:read`, BR-SHIPPER-009). Billing is a step further: nothing to enforce.

---

## 2. The entire HTTP surface, and its enforcement

| Route | Method | `@PreAuthorize` | Reachable by |
|---|---|---|---|
| `/data/billing_types` | GET | **none, at class or method level** | **anyone, unauthenticated** |

That is the whole table. One route, no guard.

**This is deliberate, and the storefront depends on it** (BR-BILLING-016). A customer
choosing between an individual receipt and an enterprise invoice must be able to read the
available billing types *before* authenticating — guest checkout exists precisely so that an
unauthenticated customer can complete a purchase.

`DataBillingTypesController` carries **no class-level `@PreAuthorize`**, and because
`SecurityConfig` declares **no request-matcher rules at all** (the Gate D finding in
`ADR-002`), there is no second layer that would catch it. The route is public by design and
by construction.

**`IdentityRouteParityTest`'s equivalent for Orders — `OrdersRouteParityTest` — must assert
that this route stays unguarded.** An accidentally added authority check would break
anonymous checkout, and it would break it *silently* from the server's point of view.

---

## 3. Everything else in this domain is unreachable over HTTP

| Entity | HTTP surface | How data is written |
|---|---|---|
| `BillingType` | **one anonymous read** | seed data only — no create, update, patch or delete route exists |
| `BillingCompany` | **none at all** | as a side effect of checkout, via `BillingContract.resolveOrCreateBillingCompany` |
| `Address` | **none at all** | as a side effect of checkout, via `AddressesConverterService` |
| `PaymentType` | **none at all** | seed data only |

**BR-BILLING-014** — billing companies have no API surface. Addresses have none either.

This is the security-relevant shape of the domain, and it is worth stating plainly:

> **Two tables in this domain are written by anonymous callers and cannot be administered by
> anyone.** A guest completing a checkout creates `billing_companies` and `addresses` rows,
> through a route (`POST /public/checkout`) guarded by the `checkout` authority that the
> seeded `Customer` role holds — and the guest account holds. Yet no authority exists to
> read, correct or delete what was created.

Nothing about that is fixed in Phase 3. It is current, tested, client-facing behaviour, and
changing it would be new product work rather than conversion work (G1). It is recorded so the
asymmetry is a decision rather than a surprise.

---

## 4. The one authority that touches billing data indirectly

`checkout` — the single authority that was already a constant before Gate B, and the only one
the seeded `Customer` role holds.

```
POST /public/checkout   @PreAuthorize("hasAuthority('" + CHECKOUT + "')")
```

A caller with `checkout` can, as a side effect of placing an order:

- create a `billing_companies` row (through `resolveOrCreateBillingCompany`);
- create up to two `addresses` rows — one billing, one shipping;
- bind an existing `billing_types` row and an existing `payment_types` row to their order.

So `checkout` is transitively a **write** authority over two of this domain's four tables,
which no reading of the authority name would suggest. Worth knowing before anyone reasons
about blast radius from the catalogue alone.

---

## 5. Porting checklist

| Item | Requirement |
|---|---|
| Authorities | **None to port** — this domain references `AuthorityCodes` nowhere. Do not invent codes for it during the extraction; inventing one would imply governance that does not exist |
| The anonymous read | `GET /data/billing_types` stays unguarded, with no class-level `@PreAuthorize` on the controller (BR-BILLING-016) |
| Absent routes | no controller is added for billing companies or addresses (BR-BILLING-014). Their absence is behaviour |
| Read-only base | `DataBillingTypesController` keeps extending **`DataGenericController`**, not `DataCrudGenericController` — that is the structural reason no write route exists |
| Pinning test | `OrdersRouteParityTest` asserts the route prefix, that **no authority is demanded**, and that no write verb is mapped |
| Gate B | zero `hasAuthority('...')` literals, which is trivially true here and must stay true (X6) |
