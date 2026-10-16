# Validations — Image Registry (`image`)

## Transport-model validation

| Field | Constraint | Violation outcome |
|---|---|---|
| `code` | `@NotBlank` | validation envelope, code `REJECTED_02` |
| `filename` | `@NotBlank` | validation envelope, code `REJECTED_02` |
| `url` | `@NotBlank` | validation envelope, code `REJECTED_02` |

Source: `api/models/ImagePojo.java`. Applied on `POST` and `PUT` (both carry the
validation marker). `PATCH` takes a raw change map and is therefore **not**
covered by these constraints.

## Persistence-layer validation

| Column | Constraint |
|---|---|
| `image_code` | not null, unique, max 50 |
| `image_filename` | not null, unique, max 100 |
| `image_url` | not null, unique, max 500 |

Each also has an ORM-declared minimum length of 1, which the database cannot
express.

## Service-layer validation

| Check | Component | Failure |
|---|---|---|
| filename present | `ImagesCrudServiceImpl.getExisting` | bad input, `Invalid filename` |
| image must not already exist on create | `CrudGenericService.validateInputPojoBeforeCreation` | already exists, `The item already exists` |
| selecting parameters present on update/patch | `DataCrudGenericController` | bad input, `Missing request params` |
| at most one row selected on update/patch | `CrudGenericService` | runtime failure, `Cannot update more than one item at a time` |
| at least one row selected on update/patch | `DataCrudGenericController` | not found, `No element was found to update` |
| at least one row selected on delete | `CrudGenericService.delete` | not found, `Requested item(s) not found` |

## Gaps

- **No URL format validation.** Any non-blank string up to 500 characters is
  accepted as an image URL.
- **No filename extension or MIME check.**
- **No referential guard before delete.** The domain does not check
  `product_images` before removing an image, so deletion of a linked image
  surfaces as an infrastructure error rather than a business rule.
- **Existence check and uniqueness constraints disagree.** Only `filename` is
  checked in Java; `code` and `url` uniqueness is enforced solely by the database,
  producing an infrastructure-level error instead of the clean already-exists
  envelope.
- **`PATCH` bypasses transport validation entirely**, so the only protection on a
  patched value is the non-blank guard inside the patch service plus the column
  length limits.
