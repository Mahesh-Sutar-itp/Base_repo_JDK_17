# ADR-003 — Target architecture: three services vs modular monolith

**Status:** proposed — decision point at the end of Phase 0
**Reference:** transformation definition §2.3 · **Phase:** 0

## Why this document exists

The transformation definition requires that the modular monolith be **rejected
consciously rather than by default**. Recording "three services, as approved" with
no counter-analysis would not satisfy that requirement. This ADR states the case
for both, and fixes the point at which the choice is made.

## The approved three-service target

| Service | Absorbs | ~LOC | Owned tables |
|---|---|---|---|
| Identity | `person`, `user`, `customer`, `salesperson` | ~5,500 | `people`, `app_users`, `app_user_roles`, `app_permissions`, `app_user_role_permissions`, `customers`, `salespeople` |
| Catalogue | `product`, `product-category`, `product-list`, `image` (+ the `company-profile` read) | ~4,000 | `products`, `product_categories`, `images`, `product_images`, `product_lists`, `product_list_items`, `app_params` |
| Orders | `order`, `billing`, `shipper` | ~6,000 | `orders`, `order_details`, `order_statuses`, `addresses`, `payment_types`, `billing_types`, `billing_companies`, `shippers` |

Twelve services (one per bounded context) is already rejected: ~1,500 LOC each,
and four domains are too thin to deploy alone (`customer` has no model of its own,
`shipper` is a unique name and nothing else).

## The case for a modular monolith

| Factor | Observation |
|---|---|
| Size | 17,800 LOC and 22 tables is small. Three deployables for that is a high operational ratio. |
| Checkout | Stays atomic in-process. ADR-001 keeps it co-located anyway, so a split buys nothing here and costs a network hop for product lookups (**per order line** — see Gate A). |
| Consistency | Registration becomes genuinely transactional either way. |
| Infrastructure that must be built from nothing | No broker, queue or stream exists anywhere. The D11 outbox, service discovery, a gateway, per-service secret distribution and three CI/CD pipelines are all new. |
| Test coverage of the riskiest component | The payment adapter has **no genuine unit coverage** (register D18). |
| What a modular monolith still delivers | Enforced module boundaries, exclusive table ownership, the Gate A contracts, the Gate B catalogue — i.e. most of the ownership and testability benefit. |

## The case for three services

- Independent deployability and scaling of the storefront read path (Catalogue)
  separately from checkout (Orders).
- Hard enforcement of boundaries by process, not by convention — module boundaries
  in one deployable erode under schedule pressure.
- It is the **owner-approved** target.

## Decision

**Deferred to the end of Phase 0 — deliberately, not by omission.**

Justification: §2.3 states that Phase 0 is **identical for both targets**. Gate A
(contracts replacing cross-domain repository reads), Gate B (generated authority
catalogue), Gates C and D, and the seven defect fixes are all required either way.
Deferring therefore costs nothing and buys a decision made against concrete rather
than estimated remaining cost.

At the Phase 0 exit review the following must be on the table:

1. Measured Gate A surface: how many call sites crossed a domain boundary and how
   many of those are **per-order-line** reads that would become network calls.
2. Whether the billing-company **resolve-or-create** stays a remote write inside
   checkout (ADR-001 item 3).
3. The state of D18 — the payment adapter cannot be safely ported without real
   unit coverage, and Phase 3 must port it with three fixes applied.
4. Operational readiness for the from-nothing infrastructure listed above.

**If the three-service target is confirmed, this ADR is updated with the reasons
the modular monolith was rejected, by name.** If it is not confirmed, Phase 0 is
still complete and valuable, and Phases 1–3 become module-boundary enforcement
instead of extraction.
