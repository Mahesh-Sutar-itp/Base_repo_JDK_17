# Integrations — Customer (`customer`)

## External systems

**None.** No customer-relationship-management integration, no marketing platform,
no address-verification service, no identity-verification service and no
analytics export. Customer notification does happen, but it is driven by the
`order` domain (order-status mails to the customer's e-mail address) and
implemented in the mail provider adapter, not here.

## Inbound integrations

| Caller domain | Entry point | Purpose |
|---|---|---|
| `order` | `CustomersCrudService.getExisting` / create via `OrdersConverterServiceImpl` | resolve or create the buyer for a new order |
| `user` | `CustomersCrudService.create` via `RegistrationServiceImpl` | create a customer for a newly registered account |
| `user` | `CustomersCrudService.create` via `JwtGuestAuthenticationFilter` | create a customer for a guest session before issuing a token |

All three go through this domain's **crud service interface** rather than its
repository. Together with the `product` → `product-category` tree-resolver edge,
these are the cleanest inbound boundaries in the decomposition: a rewrite can
replace them with remote calls without touching the callers' logic shape.

## Outbound integrations

| Target domain | Entry point | Purpose |
|---|---|---|
| `person` | cascading one-to-one association, `PeoplePatchService` via the customer patch service | create or reuse the person behind a customer; patch nested person fields |

| Shared-kernel target | Nature |
|---|---|
| generic CRUD base | create/read/update/patch/delete behaviour |
| generic controller bases | the data controller |
| pagination service, sort-spec parser | listing behaviour |
| prefix-stripping utility | nested `person.` key handling |
| persistence abstraction | paged + predicate query execution |

## Events

**None.** Creating a customer — whether by direct administration, by
self-registration or by guest checkout — publishes nothing. No welcome mail, no
downstream provisioning, no audit record. See
`cross-cutting-documentation/event-publishing-patterns.md`.

## Scheduled work

**None.** No inactivity sweep, no orphaned-person reclamation, no duplicate
detection.

## Caching

**None.**

## Notification path (for completeness)

Although this domain publishes nothing, the customer's contact details are the
destination for the system's only outbound customer communication:

```text
order status change -> MailingService -> Mailgun HTTP API
   recipient: "<firstName> <lastName> <email>" built from the order's customer
   triggers:  Paid Unconfirmed, Paid Confirmed, Rejected, Delivery Complete
```

That recipient string is assembled in the mail adapter from the order's customer
block, which in turn came from this domain's person data. Asserting: a blank or
malformed e-mail on a person row silently breaks customer notification, and
nothing in this domain validates it.

See `reference-documentation/external-integrations-map.md`.

## Anti-corruption surface

Weak by design: the domain has no transport model of its own and re-uses the
person model, so there is no translation layer isolating the customer concept
from the person concept. The converter and patch service exist but map
person-to-person, with the customer acting as a pass-through wrapper. A rewrite
that wants an independent customer contract must introduce one; extracting the
domain as-is would export the person model as part of the customer API.

See `domain-decomposition-overview/anti-corruption-layers.md`.
