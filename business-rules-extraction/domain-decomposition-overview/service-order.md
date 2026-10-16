# Service: Order, Checkout & Fulfilment (`order`)

**Proposed service name:** `order-service`  
**Extraction position:** 12 of 12  
**Complexity:** large  
**Size:** 58 source files, 4,243 lines

## Responsibility

The commercial transaction end to end: order header and lines, the status machine, checkout, the Webpay Plus payment integration, public receipt lookup, order-status notifications, and the addresses and payment-type catalogue only orders use.

## Owned data

| Table | Entity |
|---|---|
| `orders` | `Order` |
| `order_details` | `OrderDetail` |
| `order_statuses` | `OrderStatus` |
| `addresses` | `Address` |
| `payment_types` | `PaymentType` |

No other domain writes these tables. See `data-ownership.md` for the ownership basis.

## Component breakdown

| Role | Count |
|---|---|
| entity | 5 |
| controller | 4 |
| service | 30 |
| repository | 5 |
| helper | 2 |
| other | 12 |

## API surface

- POST /public/checkout — authority `checkout`
- GET /public/checkout/validate — **public provider callback** (`token_ws`)
- POST /public/checkout/validate — **public provider callback** (`TBK_TOKEN`)
- GET /public/receipt/{token} — **public**
- GET POST PUT PATCH DELETE /data/orders — `orders:*`
- POST /data/orders/confirmation | /rejection | /completion — `orders:update`
- GET /data/order_statuses — `order_statuses:read`

## Inbound contracts (who would call this service)

**None.** No domain consumes this service, so it can be extracted without
introducing any new contract on the inbound side.

### Contract this service must expose

```text
_(none — nothing consumes this domain)_
```

## Outbound contracts (what this service would call)

| Target | Purpose | Current mechanism |
|---|---|---|
| `customer` | resolve or create the buyer | **service** — survives unchanged |
| `product` | resolve each line's product by barcode and copy its price | repository-level — **contract needed** |
| `billing` | resolve the billing branch and the invoiced company | repository-level — **contract needed** |
| `shipper` | resolve the carrier by name (two call sites) | repository-level — **contract needed** |
| `salesperson` | order attribution | modelled, never populated |

## Risks and decisions required

1. **Four of five domain dependencies are repository-level** — the single largest body of extraction work in the system.
2. The three pieces of genuine business logic live here and must be ported behaviour-for-behaviour: the status machine (`BR-ORDER-020` … `BR-ORDER-027`), the monetary arithmetic with its **load-bearing integer truncation order** (`BR-ORDER-014` … `BR-ORDER-019`), and the processed-order edit lock (`BR-ORDER-029` … `BR-ORDER-031`).
3. **The status machine is bypassable**: a `status` key on a patch moves an order to any status with no transition guard (`BR-ORDER-028`), and full replacement escapes the edit lock entirely (`BR-ORDER-031`). Decide explicitly whether to reproduce these.
4. Both payment callbacks are **public with no signature, origin or idempotency check**; replay protection is incidental (`BR-ORDER-040`, `BR-ORDER-041`).
5. A payment-commit infrastructure failure is silently treated as a decline (`BR-ORDER-036`), and notification is synchronous, inline and non-transactional with the committed transition (`BR-ORDER-055`).
6. Statuses are compared by **name**, so renaming a status row breaks transitions at runtime (`BR-ORDER-020`); the read-only status catalogue is the only thing preventing it.
7. `transportValue` is mandatory, sortable, excluded from the total and set by no code (`BR-ORDER-017`); four seeded statuses are unreachable (`BR-ORDER-025`); addresses and payment types have **no API surface at all** (`BR-ORDER-058`, `BR-ORDER-060`).

## Extraction readiness

**Extract last** — but note it is a **pure consumer**: no domain reads its data, so extracting it carries zero risk to anything else once the four contracts exist.

## Cross-references

- behavioural detail: `../detailed-domain-bre/domain-order/` (70 rules, 23 features)
- boundary justification: `bounded-contexts.md`
- table ownership: `data-ownership.md`
- translation contracts: `anti-corruption-layers.md`
- extraction sequencing: `execution-order.md`
