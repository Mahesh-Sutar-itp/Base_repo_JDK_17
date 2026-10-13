# Common Events

## Finding: the system publishes and consumes no events

There is **no event catalogue** because there are no events. A repository-wide
search for publishers, listeners, broker clients, outbox tables and webhook
dispatch returned zero matches across both `src/main/java` and `src/test/java`.

This file exists to record the absence explicitly, as required by the
absence-is-evidence standard. The full analysis, including the inline
synchronous calls that take the place of events and the candidate event set a
rewrite would need, is in `event-publishing-patterns.md`.

## Events produced

**None.**

## Events consumed

**None.**

## The closest things to events in the system

| Mechanism | What it is | Why it is not an event |
|---|---|---|
| inline mail notification after an order transition | a synchronous method call from a controller to the mail adapter | in-process, blocking, non-transactional, no retry, no subscriber model (`BR-ORDER-055`) |
| the two inbound payment callbacks | HTTP requests from Transbank to `/public/checkout/validate` | **inbound integration**, not a domain event; public, unauthenticated, no idempotency key (`BR-ORDER-040`) |
| `ON DELETE CASCADE` on product-list membership | a database rule | invisible to application code; cannot cross a service boundary (`BR-PRODLIST-023`) |
| `ON DELETE SET NULL` on order lines | a database rule | same (`BR-PRODUCT-029`) |

## Where a rewrite would need events first

In priority order, derived from the actual failure modes in the code:

1. **order status changed** — removes the "status committed, notification lost"
   failure mode (`BR-ORDER-055`);
2. **product deleted** — the only viable replacement for the two database rules
   above once data is split;
3. **account registered** — replaces three unwrapped sequential writes that can
   already leave a partial registration today (`BR-USER-012`);
4. **role grant changed** — nothing propagates grant changes today; authorities
   are frozen in the issued token for up to one lifetime (`BR-USER-022`);
5. **category deleted** — would drive the orphaning operation that exists and is
   never called (`BR-PRODCAT-016`).

## Related documentation

- full analysis and the candidate event table: `event-publishing-patterns.md`
- the order notification flow: `../detailed-domain-bre/domain-order/integrations.md`
- the outbound integrations that do exist: `../reference-documentation/external-integrations-map.md`
