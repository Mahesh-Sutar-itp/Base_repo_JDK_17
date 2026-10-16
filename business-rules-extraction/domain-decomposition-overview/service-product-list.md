# Service: Product List (`product-list`)

**Proposed service name:** `merchandising-service`  
**Extraction position:** 11 of 12  
**Complexity:** medium  
**Size:** 21 source files, 1,286 lines

## Responsibility

Curated, named collections of products for storefront merchandising, plus the membership rows the list aggregate governs.

## Owned data

| Table | Entity |
|---|---|
| `product_lists` | `ProductList` |
| `product_list_items` | `ProductListItem` |

No other domain writes these tables. See `data-ownership.md` for the ownership basis.

## Component breakdown

| Role | Count |
|---|---|
| entity | 2 |
| controller | 2 |
| service | 12 |
| repository | 2 |
| helper | 2 |
| other | 1 |

## API surface

- GET /data/product_lists — **public**
- POST PUT PATCH DELETE /data/product_lists — `product_lists:create|update|delete`
- GET /data/product_list_contents — **public**
- POST PUT DELETE /data/product_list_contents — all three behind the single authority `product_lists:contents`

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
| `product` | resolve a submitted product by barcode before creating a membership row | **service**, but called directly from a controller — move it behind a service first |

## Risks and decisions required

1. `product_list_items.product_id` carries **`ON DELETE CASCADE`** — the only cascade in the schema — so product deletion silently shrinks every list (`BR-PRODLIST-023`). It cannot survive a split; the unused `deleteByProductId` operation is the intended replacement but needs a product-deleted notification, and the system publishes no events.
2. The contents controller **bypasses the generic framework entirely** and inverts the delete convention: an empty filter empties the whole list rather than doing nothing (`BR-PRODLIST-017`, `BR-PRODLIST-030`).
3. Contents replacement is **destructive before constructive** and silently skips unresolvable products, so a replace can empty a list and report success (`BR-PRODLIST-015`, `BR-PRODLIST-016`).
4. The `disabled` flag is mandatory in the database and blocked from working by three independent layers (`BR-PRODLIST-008` … `BR-PRODLIST-010`): implement it or drop it.
5. The contents API publishes `ProductPojo` as its own contract (`BR-PRODLIST-013`).

## Extraction readiness

**High.** Zero inbound coupling — nothing reads lists. The cascade replacement is the main work item.

## Cross-references

- behavioural detail: `../detailed-domain-bre/domain-product-list/` (30 rules, 16 features)
- boundary justification: `bounded-contexts.md`
- table ownership: `data-ownership.md`
- translation contracts: `anti-corruption-layers.md`
- extraction sequencing: `execution-order.md`
