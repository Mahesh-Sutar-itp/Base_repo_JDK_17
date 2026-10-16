# Integrations — Company Profile (`company-profile`)

## External systems

**None.** The domain holds two image URLs that point at externally hosted assets
(the seed data uses a placeholder image service), but it never dereferences them.
No HTTP client, no content-delivery integration, no asset validation.

## Inbound integrations

**None from other domains.** No business domain reads or writes `app_params`.
The only inbound caller is the public storefront client over HTTP.

This complete isolation makes `company-profile` the single cleanest extraction
candidate in the system: it shares no table, no entity and no service with any
other domain.

## Outbound integrations

| Target | Nature |
|---|---|
| shared persistence abstraction | the category query via `ParamsRepository` |

That is the entire outbound surface. The domain does **not** use the shared
pagination service, the shared sort-spec parser, the generic CRUD base class, the
generic data controller base class or any converter/patch/predicate component —
it is the only domain that opts out of the whole generic framework.

## Events

**None.** No event is published when the profile is read, and since there is no
write path there is nothing to announce.

## Scheduled work

**None.**

## Caching

**None**, and notably so: this is a read-only, rarely-changing, high-traffic
endpoint re-queried on every request. There is no caching abstraction anywhere in
the codebase to extend. See
`cross-cutting-documentation/caching-patterns.md`.

## Relationship to external configuration

It is important not to confuse this domain with the application's configuration
mechanism. Every tunable behaviour in the system — pagination limits, token
lifetime, BCrypt strength, guest access, order-edit locking, payment endpoints,
mail subjects — comes from the external properties file through typed
configuration holders (`ApiProperties`, `SecurityProperties`,
`ValidationProperties`, `CorsProperties`, `MailingProperties`, the payment and
mail provider holders). The `app_params` table holds **only** storefront
branding and is read by exactly one service. A rewrite should not merge the two
concepts.

## Anti-corruption surface

None exists, and none is needed: the domain has a single outbound-only transport
model and no consumer to protect. The mapping from EAV rows to a flat response
object happens inside the service via a name switch — the simplest translation
layer in the codebase, and the only one not implemented as a converter component.
