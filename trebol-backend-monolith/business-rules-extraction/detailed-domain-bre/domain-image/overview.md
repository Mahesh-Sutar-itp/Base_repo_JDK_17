# Domain: Image Registry (`image`)

## Responsibility

Owns the catalogue of image assets referenced by the storefront. An image row is
pure metadata: a short business code, a filename and an absolute URL. The system
stores **no binary data** — no upload endpoint, no filesystem or object-store
integration exists. Operators register URLs that point at images hosted
elsewhere.

## Bounded context summary

Reference-data context for media metadata. Its only invariant is the triple
uniqueness of code, filename and URL. It is consumed by the `product` domain,
which links images to products through a join table it owns.

## Owned data

| Table | Entity | Source file |
|---|---|---|
| `images` | `Image` | `src/main/java/org/trebol/jpa/entities/Image.java` |

### `Image` fields

| Field | Column | Nullable | Unique | Size constraint |
|---|---|---|---|---|
| `id` | `image_id` | no | — | — |
| `code` | `image_code` | no | yes | `@Size(min=1,max=50)` |
| `filename` | `image_filename` | no | yes | `@Size(min=1,max=100)` |
| `url` | `image_url` | no | yes | `@Size(min=1,max=500)` |

All three business columns carry their own UNIQUE KEY in the DDL
(`UK_IMG_CODE`, `UK_IMG_FILENAME`, `UK_IMG_URL`).

## Components

| Role | Component | File |
|---|---|---|
| controller | `DataImagesController` | `api/controllers/DataImagesController.java` |
| crud service | `ImagesCrudServiceImpl` | `jpa/services/crud/impl/ImagesCrudServiceImpl.java` |
| converter | `ImagesConverterServiceImpl` | `jpa/services/conversion/impl/ImagesConverterServiceImpl.java` |
| patch service | `ImagesPatchServiceImpl` | `jpa/services/patch/impl/ImagesPatchServiceImpl.java` |
| predicate service | `ImagesPredicateServiceImpl` | `jpa/services/predicates/impl/ImagesPredicateServiceImpl.java` |
| sort spec | `ImagesSortSpec` | `jpa/sortspecs/ImagesSortSpec.java` |
| repository | `ImagesRepository` | `jpa/repositories/ImagesRepository.java` |
| transport model | `ImagePojo` | `api/models/ImagePojo.java` |

## Dependencies

- **Depends on**: nothing.
- **Depended on by**: `product` — the product CRUD service calls this domain's
  `getExisting` when linking images to a product, and owns the `product_images`
  join table.

## Shared entity status

`Image` is a **shared entity**: used by `image` (owner) and `product`.
See `cross-cutting-documentation/shared-entities.md`.

## Full CRUD surface

Unlike `person`, this domain exposes the complete generic CRUD contract —
read, create, replace, patch and delete — each behind its own authority
(`images:read`, `images:create`, `images:update`, `images:delete`).
