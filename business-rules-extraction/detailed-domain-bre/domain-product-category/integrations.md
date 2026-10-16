# Integrations — Product Category Tree (`product-category`)

## External systems

**None.** No taxonomy service, no catalogue feed, no search-engine indexing and
no import/export path. The hierarchy is maintained exclusively through this
domain's own REST surface.

## Inbound integrations

| Caller domain | Entry point | Purpose |
|---|---|---|
| `product` | `ProductCategoryTreeResolverService.getBranchIdsFromRootCode` via `ProductsPredicateServiceImpl` | expand a product search's `categoryCode` filter into the whole branch |
| `product` | `Product.productCategory` association | classify a product (optional) |
| `product` | `ProductsRepository.setProductCategoryById` | re-point a product at a category |
| `product` | `ProductsRepository.orphanizeByCategories` | null the category reference for products in a set of categories — **defined but never called** |

The first of these is the only service-mediated inbound edge in the whole
decomposition: `product` consumes the tree resolver through its interface rather
than through a repository. The remaining three are data-level edges owned by the
`product` domain.

## Outbound integrations

| Target | Nature |
|---|---|
| shared pagination service | page index/size resolution |
| shared sort-spec parser | `sortBy` / `order` resolution |
| shared generic CRUD base | create/read/update/patch/delete behaviour |
| shared persistence abstraction | paged + predicate query execution |
| shared configuration holder | recursion-depth ceiling |

No business-domain dependency. Note that this domain's **own** predicate service
also consumes its **own** tree resolver (for the `rootId` / `rootCode` keys), so
the resolver is used both internally and by `product`.

## Events

**None.** Creating, renaming, moving or deleting a category publishes nothing. No
cache is invalidated (there is no cache) and no search index is refreshed (there
is no search index).

## Scheduled work

**None.** No job validates tree integrity, detects cycles, prunes empty
categories or reconciles depth against the configured limit.

## Anti-corruption surface

The transport model, converter and patch service isolate the REST contract from
the entity, and the tree resolver is a clean, interface-backed capability that a
rewrite can lift out as a standalone service. The two leaks are the direct
repository usage from `product` (`setProductCategoryById`,
`orphanizeByCategories`) and the fact that `products.product_category_id` is a
physical foreign key across the proposed boundary. See
`domain-decomposition-overview/anti-corruption-layers.md`.
