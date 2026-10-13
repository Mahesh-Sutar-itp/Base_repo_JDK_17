# Service: Product Catalog (`product`)

**Proposed service name:** `catalog-service`  
**Extraction position:** 10 of 12  
**Complexity:** medium  
**Size:** 16 source files, 1,120 lines

## Responsibility

The sellable item catalogue — barcode, name, description, price, stock levels — plus the product-to-image link rows it governs.

## Owned data

| Table | Entity |
|---|---|
| `products` | `Product` |
| `product_images` | `ProductImage` |

No other domain writes these tables. See `data-ownership.md` for the ownership basis.

## Component breakdown

| Role | Count |
|---|---|
| entity | 2 |
| controller | 1 |
| service | 8 |
| repository | 3 |
| helper | 1 |
| other | 1 |

## API surface

- GET /data/products — **public**
- POST /data/products — `products:create`, 201
- PUT /data/products — `products:update`, 204
- PATCH /data/products — `products:update`, 204
- DELETE /data/products — `products:delete`, 204

## Inbound contracts (who would call this service)

| Caller | Purpose | Current mechanism |
|---|---|---|
| `order` | resolve each order line's product by barcode and copy its current price | repository-level — **contract needed** |
| `product-list` | resolve a product before creating a membership row | **service** — survives a split unchanged |

### Contract this service must expose

```text
findByBarcode(barcode) -> { barcode, name, currentPrice } or empty
   (blank barcode and unresolvable barcode are both client errors;
    the CURRENT PRICE is authoritative - the client's unit value is
    always discarded: BR-ORDER-009)
```

## Outbound contracts (what this service would call)

| Target | Purpose | Current mechanism |
|---|---|---|
| `image` | resolve an existing image by filename while building link rows | **service** — survives unchanged |
| `product-category` | expand a category filter into the whole descendant branch | **service** — survives unchanged |

## Risks and decisions required

1. **Stock is stored and never read.** No availability check, no reservation, no decrement on order placement, so an out-of-stock product can be ordered, paid for and completed (`BR-PRODUCT-021`, `BR-PRODUCT-022`). A rewrite introducing stock control is adding behaviour, and `order` would need a reservation contract.
2. Three different delete rules cross this boundary: image links **block** deletion, order lines are **set null**, list membership **cascades** (`BR-PRODUCT-028` … `BR-PRODUCT-030`). A split must re-express all three explicitly and must not unify them.
3. The public read exposes price **and stock levels** anonymously (`BR-PRODUCT-025`).
4. Image links are rebuilt wholesale on replacement, so a `PUT` body with no images silently removes every link (`BR-PRODUCT-011`).
5. `deepReadAll(Predicate, Pageable)` ignores its predicate and its count query selects entities (`BR-PRODUCT-032`) — latent, since no service calls it.

## Extraction readiness

**High.** Both outbound edges are already service-level; only the inbound order edge needs a contract.

## Cross-references

- behavioural detail: `../detailed-domain-bre/domain-product/` (32 rules, 13 features)
- boundary justification: `bounded-contexts.md`
- table ownership: `data-ownership.md`
- translation contracts: `anti-corruption-layers.md`
- extraction sequencing: `execution-order.md`
