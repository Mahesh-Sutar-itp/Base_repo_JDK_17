# Service: Salesperson (`salesperson`)

**Proposed service name:** `salesperson-service`  
**Extraction position:** 9 of 12  
**Complexity:** small  
**Size:** 12 source files, 664 lines

## Responsibility

The seller role: a thin record marking a person as staff who can be credited on an order. It carries no attributes of its own.

## Owned data

| Table | Entity |
|---|---|
| `salespeople` | `Salesperson` |

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

- GET /data/salespeople — `salespeople:read`
- POST /data/salespeople — `salespeople:create`, 201
- PUT /data/salespeople — `salespeople:update`, 204
- DELETE /data/salespeople — `salespeople:delete`, 204
- _(no PATCH route; the patch service is wired but unreachable)_

## Inbound contracts (who would call this service)

| Caller | Purpose | Current mechanism |
|---|---|---|
| `order` | order attribution through `orders.salesperson_id` | **modelled but never populated** — no route sets the column |

### Contract this service must expose

```text
_(none live today; if attribution is implemented: `resolveSalesperson(idNumber) -> salesperson or empty`)_
```

## Outbound contracts (what this service would call)

| Target | Purpose | Current mechanism |
|---|---|---|
| `person` | cascading many-to-one create of the person behind a salesperson | ORM cascade — **distributed write** |

## Risks and decisions required

1. **No component calls into this domain in a shipped deployment.** Order attribution is modelled in the data and absent from the API (`BR-SALESPERSON-015`), so the feature it exists to serve is new work rather than a port.
2. `salespeople.person_id` has **no unique constraint**, unlike the customer equivalent, so duplicate salespeople for one person are possible under concurrency (`BR-SALESPERSON-002`). Adding the constraint is a data-migration question — duplicates may already exist.
3. The order entity's persist/merge cascade on the salesperson association is a **latent validation bypass**: were a route to populate it, order writes would create salesperson rows without passing this domain's checks (`BR-SALESPERSON-016`).
4. Nothing links a salesperson record to a login account (`BR-SALESPERSON-020`), so an account holding `salespeople:read` cannot identify its own record.

## Extraction readiness

**Highest coupling-wise** (zero live consumers) but the outbound person cascade is the same obstacle as `customer`.

## Cross-references

- behavioural detail: `../detailed-domain-bre/domain-salesperson/` (23 rules, 8 features)
- boundary justification: `bounded-contexts.md`
- table ownership: `data-ownership.md`
- translation contracts: `anti-corruption-layers.md`
- extraction sequencing: `execution-order.md`
