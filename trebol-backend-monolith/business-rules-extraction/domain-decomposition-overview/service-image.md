# Service: Image Registry (`image`)

**Proposed service name:** `image-service`  
**Extraction position:** 2 of 12  
**Complexity:** small  
**Size:** 13 source files, 691 lines

## Responsibility

Catalogue of image asset metadata — business code, filename and absolute URL. No binary content is ever stored or served.

## Owned data

| Table | Entity |
|---|---|
| `images` | `Image` |

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

- GET /data/images — authority `images:read`
- POST /data/images — `images:create`, 201
- PUT /data/images — `images:update`, 204
- PATCH /data/images — `images:update`, 204
- DELETE /data/images — `images:delete`, 204

## Inbound contracts (who would call this service)

| Caller | Purpose | Current mechanism |
|---|---|---|
| `product` | resolve an existing image by filename while building product image-link rows | **service** — `ImagesCrudService.getExisting`, survives a split unchanged |

### Contract this service must expose

```text
getExisting(imageMetadata) -> image or empty   (resolution by filename; blank filename is a client error)
```

## Outbound contracts (what this service would call)

**None.** This service has no dependency on any other business domain.

## Risks and decisions required

1. `product_images` holds a foreign key with no cascade, so deleting a linked image fails as an infrastructure error (`BR-IMAGE-014`); after a split this needs an explicit cross-service reference check.
2. The product domain fabricates an **unsaved placeholder image** when a filename does not resolve (`BR-PRODUCT-014`), so image data can enter the system without passing this domain's validation. A split should close that hole.
3. The service-level existence check uses filename only while the database enforces three independent unique constraints, so code and URL collisions surface as infrastructure errors (`BR-IMAGE-002`).

## Extraction readiness

**High.** One table, one clean service-level consumer, no outbound dependency.

## Cross-references

- behavioural detail: `../detailed-domain-bre/domain-image/` (15 rules, 8 features)
- boundary justification: `bounded-contexts.md`
- table ownership: `data-ownership.md`
- translation contracts: `anti-corruption-layers.md`
- extraction sequencing: `execution-order.md`
