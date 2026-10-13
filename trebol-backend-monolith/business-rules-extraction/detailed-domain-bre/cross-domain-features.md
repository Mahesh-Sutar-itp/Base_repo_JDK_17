# Cross-Domain Features

A cross-domain feature is one whose behaviour cannot be delivered by a single
domain. Each entry below names the participating domains, the orchestrating
component, and what would have to change if the domains were split into
independently deployable services.

---

## CDF-01 Checkout

**Domains:** `order` (orchestrator), `customer`, `product`, `billing`,
`shipper`, `person` (transitively through `customer`)

**Entry point:** `POST /public/checkout`, authority `checkout`

**Flow:** the order converter resolves, in one transaction, the payment type, the
customer (reused or created, cascading a person row), the billing type and — on
the enterprise branch — a billing company and billing address, the shipper and
shipping address, and every order line's product. It then computes the totals and
persists the order as `Pending`, after which the checkout service obtains a
payment page and advances the order to `Payment Started`.

**Why it is cross-domain:** five domains are written or read inside a single
transaction, and four of the five are reached through repositories rather than
services.

**Split impact:** the highest in the system. Requires a product-lookup contract
(barcode → name and current price), a billing-classification contract, a
billing-company registration contract, a shipper-lookup contract, and a
distributed-transaction or compensation strategy for the customer-and-person
write. The server-side price authority (`BR-ORDER-009`) must survive the split or
price integrity is lost.

---

## CDF-02 Self-registration

**Domains:** `user` (orchestrator), `person`, `customer`

**Entry point:** `POST /public/register`, public

**Flow:** validate account-name uniqueness, convert the submitted profile to a
person, validate identification-number uniqueness, persist the person, create the
account with a hashed password and the hardcoded `Customer` role, then create a
customer row for that person.

**Why it is cross-domain:** three domains are written in sequence.

**Split impact:** significant, and already fragile. The flow is **not**
transactional today (`BR-USER-012`), so a partial registration is already
possible within the monolith. Splitting makes it the normal case rather than the
exception, so a rewrite must add an explicit saga or outbox regardless of whether
it splits.

---

## CDF-03 Guest session

**Domains:** `user` (orchestrator), `customer`, `person`

**Entry point:** `POST /public/guest`, handled by an authentication filter

**Flow:** persist the submitted guest profile as a customer (cascading a person),
tolerate an already-registered identification number, then authenticate using the
configured guest credential and issue a token carrying the guest role's
authorities.

**Why it is cross-domain:** an authentication filter performs a business write
before authenticating.

**Split impact:** moderate. The identity service would have to call the customer
service during authentication, which couples login latency and availability to a
business domain. A rewrite should consider moving the customer creation out of
the filter.

---

## CDF-04 Account profile management

**Domains:** `user` (orchestrator), `person`

**Entry points:** `GET` and `PUT /account/profile`, authenticated

**Flow:** resolve the account, then either patch its existing person, attach an
existing unowned person found by identification number, or create and attach a
new one. Attaching a person already claimed by another account is rejected.

**Why it is cross-domain:** the one-person-one-account invariant spans both
domains and is enforced only in application code — there is no unique constraint
behind it (`BR-USER-004`).

**Split impact:** moderate. The uniqueness check becomes a cross-service query
with a race window that does not exist today.

---

## CDF-05 Branch-aware product search

**Domains:** `product` (orchestrator), `product-category`

**Entry point:** `GET /data/products?categoryCode=...`, public

**Flow:** the product predicate service asks the category tree resolver for every
descendant category identifier of the supplied code, then matches products whose
category code equals the value **or** whose category identifier is in that set.

**Why it is cross-domain:** recursive traversal of another domain's hierarchy
drives this domain's query.

**Split impact:** **low — this is the model to copy.** The dependency is already
a service-interface call, so it becomes a remote call to a classification service
with no change in shape. The only consideration is latency: the resolver issues
one query per tree level, bounded by
`trebol.api.max-category-fetching-recursion-depth`.

---

## CDF-06 Product list contents management

**Domains:** `product-list` (orchestrator), `product`

**Entry points:** `GET`, `POST`, `PUT`, `DELETE /data/product_list_contents`

**Flow:** every operation resolves the target list by `listCode`, then membership
operations resolve each submitted product through the product crud service,
silently skipping anything that does not resolve.

**Why it is cross-domain:** list membership is meaningless without the product
catalogue, and the contents controller returns **product** models rather than
membership rows.

**Split impact:** low for the lookup (already a service call) but note two
database-level couplings that cannot survive a split: the
`ON DELETE CASCADE` that silently de-lists deleted products
(`BR-PRODLIST-023`), and the reuse of the product transport model as this
domain's own contract.

---

## CDF-07 Product image linking

**Domains:** `product` (orchestrator), `image`

**Entry points:** `POST` and `PUT /data/products`

**Flow:** for each submitted image the product service asks the image domain for
an existing match by filename, tolerating blank filenames (logged at debug and
skipped) and unknown filenames (an unsaved placeholder is built). Link rows are
then saved, and on replacement they are deleted and rebuilt wholesale.

**Why it is cross-domain:** the join table is owned by `product` while the image
rows are owned by `image`.

**Split impact:** low for the lookup (a service call) but the placeholder
behaviour is a hole: image data enters the system through the product surface
without passing the image domain's own validation (`BR-PRODUCT-014`). A split
should close it.

---

## CDF-08 Order status notification

**Domains:** `order` (orchestrator), `customer` (recipient data), `person`
(transitively, for the e-mail address)

**Entry points:** the three order action routes and the successful payment
callback

**Flow:** after a status transition is committed, the mail adapter builds a
recipient string from the order's customer and sends a templated message for the
four customer-notified statuses and the three owner-notified statuses.

**Why it is cross-domain:** the notification content is assembled from order,
customer and person data.

**Split impact:** moderate. Notification is **synchronous, inline and
non-transactional** today (`BR-ORDER-055`), so a failure after the transition
loses the mail. This is precisely the case an event bus would solve, and the
system has no event infrastructure to extend.

---

## CDF-09 Receipt lookup

**Domains:** `order` (orchestrator), `product` (line projections)

**Entry point:** `GET /public/receipt/{token}`, public

**Flow:** resolve the order by payment token with no status restriction, convert
it to a receipt projection, and build each line with a trimmed product view
(name and barcode only) plus the unit value.

**Why it is cross-domain:** line projections carry product attributes, although
they are read from the stored line rather than re-resolved from the catalogue.

**Split impact:** **none.** Because the product name is copied onto the line at
order creation (`BR-ORDER-010`), the receipt needs no live catalogue access. This
is the cleanest cross-domain read in the system and the pattern a rewrite should
prefer.

---

## CDF-10 Authority enforcement

**Domains:** `user` (authority source), **every** other domain (enforcement
points)

**Entry point:** every protected endpoint

**Flow:** authorities are resolved from the role grants at login, embedded in the
issued token, and checked per request against literal authority strings declared
in each controller.

**Why it is cross-domain:** the authority vocabulary is owned by `user` and
consumed by all eleven other domains.

**Split impact:** moderate, and it is a **latent** risk rather than a visible
one. The coupling is through duplicated string literals that no compiler checks
(`BR-USER-031`), so a renamed authority code silently disables an access check.
A split must publish the authority catalogue as a shared, versioned artefact.
Note also that authorities are frozen in the token for its lifetime, so a revoked
grant stays effective for up to the configured token lifetime
(`BR-USER-022`).

---

## Summary

| Feature | Orchestrator | Participants | Split impact |
|---|---|---|---|
| CDF-01 Checkout | `order` | `customer`, `product`, `billing`, `shipper`, `person` | **highest** |
| CDF-02 Self-registration | `user` | `person`, `customer` | high (already non-transactional) |
| CDF-03 Guest session | `user` | `customer`, `person` | moderate |
| CDF-04 Account profile | `user` | `person` | moderate |
| CDF-05 Branch-aware product search | `product` | `product-category` | **low — model to copy** |
| CDF-06 List contents | `product-list` | `product` | low, plus a cascade to replace |
| CDF-07 Image linking | `product` | `image` | low, plus a validation hole to close |
| CDF-08 Status notification | `order` | `customer`, `person` | moderate |
| CDF-09 Receipt lookup | `order` | `product` (denormalised) | **none** |
| CDF-10 Authority enforcement | `user` | all | moderate, latent |

Two structural observations fall out of this table:

1. **`order` and `user` are the only orchestrators.** Every other domain
   participates but never coordinates, which is why they extract cleanly and
   those two extract last.
2. **The clean cases are already service-level calls** (CDF-05, CDF-06, CDF-07
   and the customer half of CDF-01), while the hard cases are repository-level
   reads or multi-domain writes in one transaction. See
   `../reference-documentation/coupling-hotspots.md` for the full list.
