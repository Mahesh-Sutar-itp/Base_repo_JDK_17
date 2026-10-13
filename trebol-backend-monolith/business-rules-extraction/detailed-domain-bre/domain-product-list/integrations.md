# Integrations — Product List (`product-list`)

## External systems

**None.** No merchandising platform, no recommendation engine, no campaign
management system and no content-management integration. Lists are curated
manually through this domain's own REST surface.

## Inbound integrations

**None from other domains.** No business domain reads lists or membership —
notably the `order` domain does not consult lists at checkout, so list membership
has no effect on pricing, availability or promotion.

The only consumers are storefront clients calling the two public read endpoints.

## Outbound integrations

| Target domain | Entry point | Purpose |
|---|---|---|
| `product` | `ProductsCrudService.getExisting(ProductPojo)` via `DataProductListContentsController` | resolve a submitted product by barcode before creating a membership row |

This is a **service-level** call made directly from a controller — the only place
in the codebase where a controller calls another domain's crud service rather
than going through its own service layer. It is clean from a coupling standpoint
but unusual from a layering one.

| Shared-kernel target | Nature |
|---|---|
| generic CRUD base | list create/read/update/patch, with an overridden delete |
| generic controller base | the list controller only |
| pagination service | used by **both** controllers |
| sort-spec parser | used by both controllers |
| persistence abstraction | paged + predicate query execution |

The contents controller extends **no** generic controller base class: it builds
its own page request, applies sorting conditionally and queries the list and
membership repositories directly. It is the least framework-aligned controller in
the system.

## Events

**None.** Creating a list, changing its membership or deleting it publishes
nothing. No cache is invalidated (there is no cache) and no storefront is
notified, so a merchandising change takes effect only on the next client request.

## Scheduled work

**None.** No campaign activation or expiry job, no empty-list pruning, no
reconciliation against the catalogue. The disabled flag looks like the remnant of
an intended scheduled or campaign feature that was never built.

## Caching

**None.** The public contents endpoint re-queries the membership table and
eagerly loads every product on every call.

## Data-level coupling to `product`

`product_list_items.product_id` is declared with `ON DELETE CASCADE`, which makes
product deletion silently shrink every list. This is the only cascade-on-delete
rule in the whole schema, and it means the `product` domain can mutate this
domain's data without any application code being involved.

A parallel application-level mechanism exists —
`ProductListItemsRepository.deleteByProductId` — and is never called. A rewrite
that splits the databases must choose one of the two and implement it explicitly;
the cascade will not survive a boundary.

## Anti-corruption surface

The list half of the domain has a converter and a patch service isolating the
transport model from the entity. The contents half has **no anti-corruption
layer at all**: the controller operates on membership entities directly, reuses
the `product` domain's transport model as its own request and response shape, and
exposes products rather than membership rows.

Extracting this domain therefore means publishing the product schema as part of
the contents contract, exactly as `customer` and `salesperson` publish the person
schema. See `domain-decomposition-overview/anti-corruption-layers.md`.
