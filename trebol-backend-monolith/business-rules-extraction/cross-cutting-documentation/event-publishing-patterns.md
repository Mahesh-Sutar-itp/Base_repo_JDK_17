# Event Publishing Patterns

## Finding: there are none

A repository-wide search over `src/main/java` and `src/test/java` for every event
mechanism available to this stack returned **zero matches**:

| Searched for | Matches |
|---|---|
| application event publisher | 0 |
| event listener annotations | 0 |
| transactional event listeners | 0 |
| message broker clients (queue, topic, stream) | 0 |
| outbox or change-data-capture tables | 0 |
| webhook dispatch (outbound) | 0 |

The system publishes no domain events, consumes no messages and has no
asynchronous notification mechanism of any kind. This is an **absence recorded as
a finding**, not an omission in the analysis.

## What takes the place of events today

Every cross-component reaction is a **synchronous, in-process call**, usually
made directly from a controller method after the primary write has already been
committed:

| Reaction | How it happens today | Consequence |
|---|---|---|
| notify the customer of a paid order | the checkout controller calls the mail adapter inline after the status transition | a mail failure is a 500-class response **after** the order status is already committed (`BR-ORDER-055`) |
| notify the customer and owner of a confirmed order | the orders controller calls the mail adapter inline, twice | same; no retry, the notification is simply lost |
| notify the customer of a rejected or completed order | same pattern | same |
| remove a deleted product from every product list | **a database `ON DELETE CASCADE`** — no application code is involved (`BR-PRODLIST-023`) | invisible to the application; cannot survive a database split |
| null the product reference on historical order lines | **a database `ON DELETE SET NULL`** (`BR-PRODUCT-029`) | same |
| create a customer for a newly registered account | the registration service calls the customer service inline, as its third unwrapped write (`BR-USER-012`) | a failure leaves a person and account with no customer row |
| create a customer for a guest session | an authentication filter calls the customer service before issuing a token (`BR-CUSTOMER-017`) | login latency and availability depend on a business domain |

Two of these — the database cascades — are the clearest cases where an event
would be the natural mechanism, and they are precisely the two that **cannot
survive a physical split**. The application-level operations that would replace
them (`ProductListItemsRepository.deleteByProductId` and
`ProductsRepository.orphanizeByCategories`) exist in the codebase and are
**never called**; see `../reference-documentation/dead-code-decomposition-notes.md`.

## Events that would exist in an event-driven version

Derived from the state transitions and cross-domain reactions actually present in
the code. None of these exists today; this table is design input for a rewrite,
clearly marked as such.

| Candidate event | Producer | Would be consumed by | Replaces |
|---|---|---|---|
| order payment started | `order` | — | the token write inside the start transition |
| order paid | `order` | notification | the inline customer mail on the payment callback |
| order confirmed | `order` | notification | the inline customer **and** owner mail |
| order rejected | `order` | notification | the inline customer mail |
| order completed | `order` | notification | the inline customer mail |
| account registered | `user` | `person`, `customer` | the three unwrapped writes in the registration flow |
| guest session started | `user` | `customer` | the customer create inside the authentication filter |
| product deleted | `product` | `product-list`, `order` | the two database cascade/set-null rules |
| category deleted | `product-category` | `product` | the uncalled orphaning operation |
| role grant changed | `user` | every service | nothing — today a revoked grant stays effective until the holder's token expires (`BR-USER-022`) |

## Implications for a rewrite

1. **There is no pattern to follow.** Introducing events is new infrastructure,
   not a port. Nothing in the codebase demonstrates an intended event shape,
   serialisation format, topic naming convention or delivery guarantee.
2. **Notification is the first candidate.** It is already decoupled in intent
   (the mail component is optional and null-checked at every call site,
   `BR-ORDER-052`) but coupled in implementation. Moving it behind an event
   removes the "status committed, mail lost" failure mode with no change to the
   order state machine.
3. **The two database cascades are mandatory work** for any split, and an event
   is the only sane replacement. Note the ordering hazard: the cascade is
   currently atomic with the product delete, so an event-based replacement
   introduces a window in which a deleted product is still listed.
4. **Authority changes need a propagation story.** Today authorities are frozen
   in the issued token, so grant changes already take up to one token lifetime to
   apply. An event does not fix that unless token verification stops trusting the
   claim.
5. **No audit trail exists** (`../detailed-domain-bre/requirements-summary.md`),
   so events would also be the natural place to build one.

## Related documentation

- inline notification behaviour: `../detailed-domain-bre/domain-order/integrations.md`
- the database cascades: `../domain-decomposition-overview/data-ownership.md`
- the uncalled replacement operations: `../reference-documentation/dead-code-decomposition-notes.md`
- the common event catalogue (also empty): `common-events.md`
