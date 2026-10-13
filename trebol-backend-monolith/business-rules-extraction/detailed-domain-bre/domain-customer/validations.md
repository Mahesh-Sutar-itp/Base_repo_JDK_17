# Validations — Customer (`customer`)

## Transport-model validation

The customer surface uses the **person** model, so the person constraints apply:

| Field | Constraint | Violation outcome |
|---|---|---|
| `firstName` | `@NotBlank` | validation envelope, code `REJECTED_02` |
| `lastName` | `@NotBlank` | validation envelope, code `REJECTED_02` |
| `idNumber` | `@NotBlank` | validation envelope, code `REJECTED_02` |
| `email` | `@NotBlank` | validation envelope, code `REJECTED_02` |
| `phone1` / `phone2` | none | accepted as-is |

Source: `api/models/PersonPojo.java`. Applied on `POST` and `PUT` — and, since
there is no partial-update route, **every** write to this domain is covered by
transport validation.

There is **no customer-specific validation model**, so no constraint can express
a customer-level rule. A rewrite introducing, say, a mandatory acceptance flag
has nowhere to put it in the current contract.

## Persistence-layer validation

| Column | Constraint |
|---|---|
| `customer_id` | not null, identity-generated |
| `customers.person_id` | not null, **unique**, foreign key to `people` |

Plus every person-level column constraint, applied through the cascade.

## Service-layer validation

| Check | Component | Failure |
|---|---|---|
| identification number present | `CustomersCrudServiceImpl.getExisting` | bad input, `Customer does not have an ID card` |
| customer must not already exist on create | `CrudGenericService.validateInputPojoBeforeCreation` | already exists, `The item already exists` |
| selecting parameters present on update | `DataCrudGenericController` | bad input, `Missing request params` |
| at most one row selected on update | `CrudGenericService` | runtime failure, `Cannot update more than one item at a time` |
| at least one row selected on update | `DataCrudGenericController` | not found, `No element was found to update` |
| at least one row selected on delete | `CrudGenericService.delete` | not found, `Requested item(s) not found` |

## Cross-domain validation on this domain's data

| Check | Where | Behaviour |
|---|---|---|
| customer resolution at checkout | `OrdersConverterServiceImpl` | reuse if found, create otherwise — **never fails on an existing customer** |
| guest profile sufficiency | `JwtGuestAuthenticationFilter` | a bad-input condition becomes bad credentials, `Insufficient or invalid profile data for guest` |
| repeat guest | `JwtGuestAuthenticationFilter` | already-exists is caught and logged, not propagated |

## Gaps

- **No identification-number format validation.** The configured
  identification-number regular expression and its adapter service exist but no
  customer path calls them, so any non-blank string up to 20 characters is
  accepted as a national identification number.
- **No e-mail format validation.** Inherited from the person domain — only a
  length range is enforced.
- **No referential guard before delete.** Deleting a customer with order history
  produces an infrastructure constraint error rather than a business message, and
  because every order requires a customer this affects every buyer who has ever
  ordered.
- **No orphan cleanup.** Deleting a customer leaves its person row behind with no
  reference and no process to reclaim it.
- **The one-customer-per-person rule is the only database-backed uniqueness
  invariant in the person-role area** — the equivalent rule for salespeople is
  absent, and the one-person-per-account rule in the `user` domain is enforced
  only in application code. The inconsistency is worth resolving explicitly in a
  rewrite.
- **No partial-update route**, forcing full replacements for single-field
  corrections and making every correction a chance to lose data by omission. The
  patch service that would support it is implemented and wired but unreachable.
- **The prefix-stripping utility over-replaces** (BR-CUSTOMER-012), so nested
  keys containing the prefix more than once would be corrupted rather than
  rejected if the patch path were ever exposed.
