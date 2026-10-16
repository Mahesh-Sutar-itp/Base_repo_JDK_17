# Image Registry — Entity-Relationship Diagram

The domain owns one table and declares **no** relationships. The only association involving
`images` is held by the `product` domain's join table.

## Owned table in context

```mermaid
erDiagram
    IMAGES {
        bigint image_id PK "AUTO_INCREMENT"
        varchar_50 image_code "NOT NULL, UNIQUE"
        varchar_100 image_filename "NOT NULL, UNIQUE - resolution key"
        varchar_500 image_url "NOT NULL, UNIQUE - never dereferenced"
    }

    PRODUCT_IMAGES {
        bigint product_image_id PK "external, domain product"
        bigint product_id FK "external, domain product"
        bigint image_id FK "external, domain product"
    }

    PRODUCTS {
        bigint product_id PK "external, domain product"
    }

    IMAGES ||--o{ PRODUCT_IMAGES : "referenced by"
    PRODUCTS ||--o{ PRODUCT_IMAGES : "owns link"
```

`PRODUCT_IMAGES` and `PRODUCTS` are **external reference nodes** — not owned by this domain,
shown only with the columns needed to express the relationship. Their authoritative
definitions are in the `product` domain specification.

## Ownership and cardinality

| Relationship | Cardinality | Owned by | Declared in this domain |
|---|---|---|---|
| `images` → `product_images` | one image to many links | `product` | **no** |
| `products` → `product_images` | one product to many links | `product` | n/a |

An image may be linked to many products; a product may hold many images. The many-to-many
is resolved by the join table, entirely outside this domain.

## The three-way uniqueness problem

```mermaid
erDiagram
    IMAGES_CONSTRAINTS {
        string PRIMARY "image_id - unique"
        string UK_IMG_CODE "image_code - UNIQUE, NOT checked by the service"
        string UK_IMG_FILENAME "image_filename - UNIQUE, the ONLY field checked"
        string UK_IMG_URL "image_url - UNIQUE, NOT checked by the service"
    }
```

All three business columns are independently unique, but
`ImagesCrudServiceImpl.getExisting` resolves by **filename alone**. A collision on `code` or
`url` therefore passes the application guard and fails at the database as a raw constraint
violation — **HTTP 500 instead of 409**. This is BR-IMAGE-002, the defining incoherence of
the domain.

There are **no non-unique indexes**, so the two substring filters (`codeLike`,
`filenameLike`) cannot use an index for a leading-wildcard pattern and will scan.

## Integrity notes

- **No cascade from the product side protects images.** This domain's `delete` is unbounded
  (BR-IMAGE-013); deleting an image still referenced by a product either fails on the
  join-table foreign key or orphans the relationship, depending on that table's constraint
  definition. This domain applies no protection of its own.
- **`url` is unique, sortable, and unfilterable.** No filter parameter addresses it
  (BR-IMAGE-019), so an image cannot be found, updated or deleted by URL.
- **Images are created by the `product` domain's cascade**, bypassing this domain's guard
  entirely (BR-IMAGE-024). The database constraints are the only enforcement on that path.
