# ADR-001 — Gate C: consistency model for registration and checkout

**Status:** accepted · **Gate:** C (transformation definition §5.3) · **Phase:** 0

## Context — verified, not assumed

An earlier draft of the conversion plan asserted that registration and checkout
were each a single transaction. That is **wrong**. Verified against source:

- `org.trebol.api.services.impl.RegistrationServiceImpl` — **no** `@Transactional`
  on the class or on `register(...)`.
- `org.trebol.api.services.impl.CheckoutServiceImpl` — **no** `@Transactional` on
  the class or on `requestTransactionStart(...)`.
- `org.trebol.jpa.services.crud.CrudGenericService` **is** annotated, so **each
  individual write is its own transaction**.

Consequences of the baseline:

| Flow | Writes | Failure mode that already exists today |
|---|---|---|
| Registration | insert `people` → insert `app_users` → insert `customers`, three separate transactions | Failure after write 1 leaves an **orphaned person**; after write 2, a **user with no customer row**. `person` has **no delete path anywhere in the system** (BR-PERSON-013), so compensation is impossible. |
| Checkout | create order (resolving customer, products, billing, shipper, addresses) → **synchronous** Transbank call → write token + `Payment Started` status | Failure after order creation leaves an order with no payment token and no started status. **No stock check occurs at any step.** |

So **partial registration and partial checkout are already possible** (BR-USER-012).
Extraction does not introduce the inconsistency; it would make an existing latent
failure mode routine.

## Options considered

| Option | Implication |
|---|---|
| **Keep co-located** | Identity owns `people`, `app_users`, `customers` in one database, so registration can become **genuinely transactional** — an improvement on today. Orders owns everything checkout writes except the product and customer lookups. |
| Saga with compensation | Requires a `person` delete path (does not exist), an order-cancellation path (exists only as statuses **no code reaches** — register D10), and an outbox (no event infrastructure exists anywhere). Substantial new infrastructure. |
| Accept partial state | Formalises today's behaviour. Requires a reconciliation job; none exists anywhere in the system. |

## Decision

**Keep co-located.** No distributed transaction is introduced for either flow.

1. All three registration writes stay inside **Identity**. In Phase 2 `register(...)`
   becomes genuinely `@Transactional` — recorded as a deliberate improvement on the
   baseline, not a silent fix.
2. All checkout writes stay inside **Orders**. The only cross-service calls in
   checkout are **reads**: product-by-barcode and customer resolution.
3. The one exception is the **billing-company resolve-or-create** (§5.1). Across a
   service boundary it becomes a remote write inside checkout. Ruling: keep
   resolve-or-create semantics in Phase 0 (Gate A hides it behind a contract with
   identical behaviour); decide pre-registration vs remote write in Phase 3, when
   Orders actually crosses the network. Recorded as an open item, not deferred
   silently.
4. The synchronous Transbank call stays synchronous. D3 (commit failure recorded as
   a decline) and D9 (non-stable session id) are fixed; the call pattern is not
   changed in Phase 0.

## Consequences

- Phase 2 must verify no residual **outward** write from the identity kernel
  remains (coupling hotspot HS-02).
- Checkout atomicity is unchanged by the split, which is the main reason the
  three-service grouping was chosen over one-service-per-bounded-context.
- X8 ("registration and checkout have a defined, tested consistency model") is
  satisfied by this document plus the Phase 2 transactional-registration test.
