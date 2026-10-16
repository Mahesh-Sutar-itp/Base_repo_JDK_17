# Service: Product Category Tree (`product-category`)

**Proposed service name:** `classification-service`  
**Extraction position:** 3 of 12  
**Complexity:** medium  
**Size:** 14 source files, 903 lines

## Responsibility

Self-referencing category hierarchy, including recursive descendant resolution bounded by configuration.

## Owned data

| Table | Entity |
|---|---|
| `product_categories` | `ProductCategory` |

No other domain writes these tables. See `data-ownership.md` for the ownership basis.

## Component breakdown

| Role | Count |
|---|---|
| entity | 1 |
| controller | 1 |
| service | 10 |
| helper | 1 |
| other | 1 |

## API surface

- GET /data/product_categories — **public**
- POST /data/product_categories — `product_categories:create`, 201
- PUT /data/product_categories — `product_categories:update`, 204
- PATCH /data/product_categories — `product_categories:update`, 204
- DELETE /data/product_categories — `product_categories:delete`, 204

## Inbound contracts (who would call this service)

| Caller | Purpose | Current mechanism |
|---|---|---|
| `product` | expand a `categoryCode` product filter into the whole descendant branch | **service** — the cleanest boundary in the system; survives a split unchanged |
| `product` | `setProductCategoryById` and `orphanizeByCategories` reach the entity directly | repository-level, but **both are dead code** |

### Contract this service must expose

```text
getBranchIdsFromRootCode(code) -> [categoryId]    (empty list when the code is unknown -
                                                   callers then DROP the filter, so the
                                                   response is UNFILTERED: BR-PRODCAT-011)
getBranchIdsFromRootId(id)     -> [categoryId]
getBranchesFromRoot(category)  -> [category]
```

## Outbound contracts (what this service would call)

**None.** This service has no dependency on any other business domain.

## Risks and decisions required

1. `products.product_category_id` blocks category deletion with no cascade, and the `orphanizeByCategories` operation that would make deletion safe is never called (`BR-PRODCAT-016`). A split must make it the real mechanism.
2. Category **cycles are creatable and undetected** (`BR-PRODCAT-009`); the depth limit masks the corruption instead of reporting it.
3. Recursive resolution issues one query per tree level, so a remote call per level is the latency cost of splitting; the ceiling is `trebol.api.max-category-fetching-recursion-depth` (20).

## Extraction readiness

**High.** The tree resolver is already an interface-backed capability depending only on its own repository and one configuration value.

## Cross-references

- behavioural detail: `../detailed-domain-bre/domain-product-category/` (20 rules, 14 features)
- boundary justification: `bounded-contexts.md`
- table ownership: `data-ownership.md`
- translation contracts: `anti-corruption-layers.md`
- extraction sequencing: `execution-order.md`
