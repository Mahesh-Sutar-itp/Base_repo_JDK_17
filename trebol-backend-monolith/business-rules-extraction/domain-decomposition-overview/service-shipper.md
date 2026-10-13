# Service: Shipper (`shipper`)

**Proposed service name:** `carrier-service`  
**Extraction position:** 5 of 12  
**Complexity:** small  
**Size:** 13 source files, 648 lines

## Responsibility

Catalogue of delivery carriers. A carrier is a unique name and nothing else — no contact detail, rate card, service area or carrier API.

## Owned data

| Table | Entity |
|---|---|
| `shippers` | `Shipper` |

No other domain writes these tables. See `data-ownership.md` for the ownership basis.

## Component breakdown

| Role | Count |
|---|---|
| entity | 1 |
| controller | 1 |
| service | 8 |
| repository | 1 |
| helper | 1 |
| other | 1 |

## API surface

- GET /data/shippers — **public**
- POST /data/shippers — `shippers:create`, 201
- PUT /data/shippers — `shippers:update`, 204
- PATCH /data/shippers — `shippers:update`, 204
- DELETE /data/shippers — `shippers:delete`, 204

## Inbound contracts (who would call this service)

| Caller | Purpose | Current mechanism |
|---|---|---|
| `order` | resolve the carrier by name during checkout and on an order patch | repository-level (both call sites) — **contract needed** |

### Contract this service must expose

```text
resolveShipper(name) -> { id, name } or empty
```

## Outbound contracts (what this service would call)

**None.** This service has no dependency on any other business domain.

## Risks and decisions required

1. The lookup asymmetry must be preserved exactly: an unknown carrier **rejects** order creation but is **silently ignored** on an order patch (`BR-SHIPPER-015`).
2. `orders.shipper_id` blocks carrier deletion with no cascade and there is no disabled flag, so a carrier referenced by any historical order can never be removed or withdrawn (`BR-SHIPPER-013`).
3. The blank-name rejection message literally reads `Billing type has no name` — a copy-paste defect that is user-visible (`BR-SHIPPER-003`).
4. No `@Size` annotation exists on the carrier name, so a too-long value fails at the database rather than as a validation error (`BR-SHIPPER-005`).

## Extraction readiness

**High.** One table, one consumer, no outbound dependency. Only the two repository call sites need replacing.

## Cross-references

- behavioural detail: `../detailed-domain-bre/domain-shipper/` (15 rules, 9 features)
- boundary justification: `bounded-contexts.md`
- table ownership: `data-ownership.md`
- translation contracts: `anti-corruption-layers.md`
- extraction sequencing: `execution-order.md`
