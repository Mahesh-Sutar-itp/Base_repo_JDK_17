# Service: Customer (`customer`)

**Proposed service name:** `customer-service`  
**Extraction position:** 8 of 12  
**Complexity:** small  
**Size:** 12 source files, 657 lines

## Responsibility

The buyer role: a thin record marking a person as someone who can place orders. It carries no attributes of its own.

## Owned data

| Table | Entity |
|---|---|
| `customers` | `Customer` |

No other domain writes these tables. See `data-ownership.md` for the ownership basis.

## Component breakdown

| Role | Count |
|---|---|
| entity | 1 |
| controller | 1 |
| service | 8 |
| repository | 1 |
| helper | 1 |

## API surface

- GET /data/customers — `customers:read`
- POST /data/customers — `customers:create`, 201
- PUT /data/customers — `customers:update`, 204
- DELETE /data/customers — `customers:delete`, 204
- _(no PATCH route; the patch service is wired but unreachable)_

## Inbound contracts (who would call this service)

| Caller | Purpose | Current mechanism |
|---|---|---|
| `order` | resolve or create the buyer during checkout | **service** — survives a split unchanged |
| `user` | create a customer during registration and guest authentication | **service** — survives a split unchanged |

### Contract this service must expose

```text
getExisting(personData) -> customer or empty   (resolution by person idNumber;
                                               blank idNumber is a client error)
create(personData)      -> customer            (cascades a person create)
```

## Outbound contracts (what this service would call)

| Target | Purpose | Current mechanism |
|---|---|---|
| `person` | cascading one-to-one create of the person behind a customer, and nested `person.` patch delegation | ORM cascade + patch internals — **distributed write** |

## Risks and decisions required

1. The cascading one-to-one means a customer and its person are written **atomically** today (`BR-CUSTOMER-003`); a split turns that into a distributed create.
2. The domain publishes `PersonPojo` as its own contract (`BR-CUSTOMER-008`), so extracting it as-is exports the person schema. Introducing a customer model is a **wire-contract change**.
3. `orders.customer_id` is mandatory with no cascade, so **any buyer with order history is permanently undeletable** (`BR-CUSTOMER-015`); after a split this becomes a cross-service deletion check.
4. Guest checkout creates a permanent person and customer row indistinguishable from a registered buyer's (`BR-CUSTOMER-017`).

## Extraction readiness

**High on the inbound side, medium overall.** All three inbound edges are already service-level; the outbound cascade to `person` is the only real obstacle.

## Cross-references

- behavioural detail: `../detailed-domain-bre/domain-customer/` (22 rules, 11 features)
- boundary justification: `bounded-contexts.md`
- table ownership: `data-ownership.md`
- translation contracts: `anti-corruption-layers.md`
- extraction sequencing: `execution-order.md`
