# Integrations — Product Catalog (`product`)

## External systems

**None.** No product-information-management system, no supplier feed, no
inventory service, no pricing engine, no search index and no catalogue
export/import. The catalogue is maintained exclusively through this domain's own
REST surface.

The absence of an inventory integration is consistent with BR-PRODUCT-021: stock
is a local, inert attribute rather than a synchronised value.

## Inbound integrations

| Caller domain | Entry point | Purpose |
|---|---|---|
| `order` | `ProductsRepository.findByBarcode` via `OrdersConverterServiceImpl` | resolve each order line's product and copy its current price |
| `product-list` | `ProductsCrudService.getExisting` via `DataProductListContentsController` | resolve a product before adding it to a list |

The product-list edge is **service-level** (clean); the order edge reaches the
repository directly (a boundary leak recorded in
`reference-documentation/coupling-hotspots.md`).

Note also that the `order` domain copies the product's price onto the order line
at creation time, so order history is price-stable: later price changes do not
retroactively alter past orders. This is the one place where the catalogue's data
is deliberately denormalised into another aggregate.

## Outbound integrations

| Target domain | Entry point | Purpose |
|---|---|---|
| `image` | `ImagesCrudService.getExisting` | resolve an existing image by filename while building link rows |
| `product-category` | `ProductCategoryTreeResolverService.getBranchIdsFromRootCode` | expand a category filter into the whole descendant branch |

Both are **service-level** calls through interfaces — this domain is the cleanest
consumer in the decomposition. It does, however, also touch the
`product-category` entity through its own repository helpers
(`setProductCategoryById`, `orphanizeByCategories`), both of which are dead code.

| Shared-kernel target | Nature |
|---|---|
| generic CRUD base | create/read/update/patch/delete behaviour, with four overridden extension points |
| generic controller bases | the data controller |
| pagination service, sort-spec parser | listing behaviour |
| persistence abstraction | paged + predicate query execution |

## Events

**None.** Creating, repricing, restocking or deleting a product publishes
nothing. No cache is invalidated (there is no cache), no search index is
refreshed (there is no index) and no downstream system is notified. See
`cross-cutting-documentation/event-publishing-patterns.md`.

## Scheduled work

**None.** No low-stock sweep comparing current stock to the critical threshold,
no reorder job, no price-feed refresh, no orphaned-image-link cleanup.

The critical-stock column is the clearest evidence of an intended but unbuilt
scheduled feature: the threshold is stored on every product and never read.

## Caching

**None.** The public, high-traffic product search re-queries the database on
every call, including the recursive category-branch expansion.

## Anti-corruption surface

Strong on the outbound side: both consumed domains are reached through service
interfaces, so a rewrite can turn either into a remote call without reshaping the
caller.

Weak on the inbound side in one place: the `order` converter binds to this
domain's repository rather than its service, and copies the price directly off
the entity. Splitting `product` out requires giving `order` a product-lookup
contract that returns at least the barcode, name and current price.

See `domain-decomposition-overview/anti-corruption-layers.md`.
