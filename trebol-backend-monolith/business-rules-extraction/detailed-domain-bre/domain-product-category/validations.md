# Validations — Product Category Tree (`product-category`)

## Transport-model validation

| Field | Constraint | Violation outcome |
|---|---|---|
| `code` | `@NotBlank` | validation envelope, code `REJECTED_02` |
| `name` | `@NotBlank` | validation envelope, code `REJECTED_02` |
| `parent` | **no nested-validation marker** | a submitted parent is never validated |

Source: `api/models/ProductCategoryPojo.java`. Applied on `POST` and `PUT`;
`PATCH` takes a raw change map and is uncovered.

## Persistence-layer validation

| Column | Constraint |
|---|---|
| `product_category_code` | not null, unique, max 50, ORM min 1 |
| `product_category_name` | not null, max 100, ORM min 1, unique **per parent** |
| `parent_product_category_id` | nullable, self-referencing foreign key |

## Service-layer validation

| Check | Component | Failure |
|---|---|---|
| code present | `ProductCategoriesCrudServiceImpl.getExisting` | bad input, `Invalid category code` |
| category must not already exist on create | `CrudGenericService.validateInputPojoBeforeCreation` | already exists, `The item already exists` |
| selecting parameters present on update/patch | `DataCrudGenericController` | bad input, `Missing request params` |
| at most one row selected on update/patch | `CrudGenericService` | runtime failure, `Cannot update more than one item at a time` |
| at least one row selected on update/patch | `DataCrudGenericController` | not found, `No element was found to update` |
| at least one row selected on delete | `CrudGenericService.delete` | not found, `Requested item(s) not found` |

## Structural invariants enforced by behaviour rather than constraints

| Invariant | How it is maintained |
|---|---|
| a patch cannot orphan a category | the crud service re-attaches the stored parent before every patched save |
| a patch cannot re-parent a category | the patch service exposes no `parent` key |
| recursion terminates | hard depth ceiling from `trebol.api.max-category-fetching-recursion-depth` |

## Gaps

- **No cycle or ancestry validation.** Replacement can set a category's parent to
  itself or to one of its own descendants. Nothing detects or rejects this; the
  depth limit merely stops the resolver from looping forever, so a corrupted tree
  degrades silently into truncated search results.
- **No orphan handling on delete.** The repository operation that nulls the
  category reference on affected products exists but is never invoked, so a
  delete of any classified or parent category fails as an infrastructure error.
- **No depth validation on create.** A category can be created at any depth; only
  *reading* the tree is depth-limited, so inserting beyond the limit produces
  categories that branch searches cannot see.
- **Submitted parents are unvalidated.** Because the parent field carries no
  nested-validation marker, a `PUT` body may contain a parent with a blank code
  and name.
- **Service-level existence check and database constraints disagree.** Only the
  code is checked in application code; sibling-name collisions surface as
  constraint violations.
- **Read route is unauthenticated** while `product_categories:read` exists, is
  granted to three roles and is never checked.
