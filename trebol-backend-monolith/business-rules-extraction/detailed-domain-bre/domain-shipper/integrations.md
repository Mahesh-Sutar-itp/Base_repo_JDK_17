# Integrations — Shipper (`shipper`)

## External systems

**None.** Despite representing delivery carriers, this domain integrates with no
carrier API. There is no rate lookup, no label generation, no tracking-number
exchange and no webhook receiver. A shipper is a local name only.

This absence is a material finding for a modernisation effort: shipping is
currently a bookkeeping field, so any real carrier integration is new
functionality rather than a port.

## Inbound integrations

| Caller domain | Entry point | Purpose |
|---|---|---|
| `order` | `ShippersRepository.findByName` via `OrdersConverterServiceImpl` | resolve the carrier while assembling a new order |
| `order` | `ShippersRepository.findByName` via `OrdersPatchServiceImpl` | re-point an existing order at another carrier |

Both callers reach the repository directly rather than through this domain's crud
service — a boundary leak recorded in
`reference-documentation/coupling-hotspots.md`. A rewrite that splits these into
separate services must replace the repository call with a service call or a
carrier-lookup contract.

## Outbound integrations

| Target | Nature |
|---|---|
| shared pagination service | page index/size resolution |
| shared sort-spec parser | `sortBy` / `order` resolution |
| shared generic CRUD base | create/read/update/patch/delete behaviour |
| shared persistence abstraction | paged + predicate query execution |

No business-domain dependency.

## Events

**None.** Registering, renaming or deleting a carrier publishes nothing. No other
component is notified, and orders already pointing at a renamed carrier silently
reflect the new name because they hold a foreign key, not a copied string.

## Scheduled work

**None.**

## Anti-corruption surface

`ShipperPojo` ↔ `Shipper` translation lives in a dedicated converter, and
patching goes through a dedicated patch service. However, the `order` domain
bypasses both and queries the repository directly, so there is currently **no**
anti-corruption layer on the inbound side — the consuming domain depends on this
domain's persistence interface. See
`domain-decomposition-overview/anti-corruption-layers.md`.
