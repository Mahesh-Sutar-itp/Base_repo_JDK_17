# Validations — Salesperson (`salesperson`)

## Transport-model validation

The salesperson surface uses the **person** model, so the person constraints
apply:

| Field | Constraint | Violation outcome |
|---|---|---|
| `firstName` | `@NotBlank` | validation envelope, code `REJECTED_02` |
| `lastName` | `@NotBlank` | validation envelope, code `REJECTED_02` |
| `idNumber` | `@NotBlank` | validation envelope, code `REJECTED_02` |
| `email` | `@NotBlank` | validation envelope, code `REJECTED_02` |
| `phone1` / `phone2` | none | accepted as-is |

Source: `api/models/PersonPojo.java`. Applied on `POST` and `PUT` — and, since
there is no partial-update route, **every** write to this domain is covered by
transport validation. That is unique among the role-style domains and is a
side effect of the missing `PATCH` route rather than a deliberate choice.

## Persistence-layer validation

| Column | Constraint |
|---|---|
| `salesperson_id` | not null, identity-generated |
| `salespeople.person_id` | not null, foreign key to `people`, **not unique** |

Plus every person-level column constraint, applied through the cascade.

The missing unique constraint is the single most significant validation gap in
the domain. Its customer-domain counterpart is present, so the omission appears
accidental.

## Service-layer validation

| Check | Component | Failure |
|---|---|---|
| identification number present | `SalespeopleCrudServiceImpl.getExisting` | bad input, `Salesperson does not have an ID card` |
| salesperson must not already exist on create | `CrudGenericService.validateInputPojoBeforeCreation` | already exists, `The item already exists` |
| selecting parameters present on update | `DataCrudGenericController` | bad input, `Missing request params` |
| at most one row selected on update | `CrudGenericService` | runtime failure, `Cannot update more than one item at a time` |
| at least one row selected on update | `DataCrudGenericController` | not found, `No element was found to update` |
| at least one row selected on delete | `CrudGenericService.delete` | not found, `Requested item(s) not found` |

Asserting: the already-exists check is the **only** protection against duplicate
salespeople for one person, and it is a read-then-write sequence with no
constraint behind it, so it is not atomic.

## Cross-domain validation on this domain's data

**None.** No other domain validates salesperson data. The order entity can
cascade-persist a salesperson reference, which would write a salesperson row
**without** passing any of the checks above — no blank-identification-number
rejection, no duplicate detection. Since no route populates that reference today
the path is unreachable, but the cascade is a latent validation bypass that a
rewrite should close.

Source: `jpa/entities/Order.java`.

## Gaps

- **No unique constraint on the person reference**, so duplicate salespeople for
  one person are possible under concurrency or any path that skips the service
  check.
- **No partial-update route**, forcing full replacements for single-field
  corrections and making every correction a chance to lose data by omission.
- **The cascade from `orders` is a validation bypass** for a path that is
  currently unreachable.
- **No identification-number format validation.** The configured
  identification-number regular expression and its adapter service exist but no
  salesperson path calls them.
- **No e-mail format validation.** Inherited from the person domain.
- **No referential guard before delete**, and no soft-delete, so a departing
  salesperson with attribution history could not be retired without breaking the
  foreign key.
- **No link to a login account**, so there is no validation that a salesperson
  record corresponds to an actual staff member with access — and conversely no
  check that an account holding the Salesperson role has a salesperson record.
- **No orphan cleanup.** Deleting a salesperson leaves its person row behind.
