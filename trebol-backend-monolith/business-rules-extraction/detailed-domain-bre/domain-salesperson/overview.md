# Domain: Salesperson (`salesperson`)

## Responsibility

Owns the seller role: the thin record that marks a person as a member of staff
who can be credited on an order. Like `customer`, a salesperson row carries **no
attributes of its own** — only a surrogate key and a mandatory reference to a
person. Unlike `customer`, the reference is **not unique**.

## Bounded context summary

Seller-role context. Single aggregate root (`Salesperson`) governing its person
reference through a full-cascade association. Separate from `customer` because
the two roles have different authority sets, different uniqueness invariants and
different relationships to an order (buyer mandatory, seller optional), and
separate from `user` because a salesperson record is not a login account —
nothing links the two.

## Owned data

| Table | Entity | Source file |
|---|---|---|
| `salespeople` | `Salesperson` | `jpa/entities/Salesperson.java` |

### `Salesperson` fields

| Field | Column | Nullable | Unique | Mapping |
|---|---|---|---|---|
| `id` | `salesperson_id` | no | — | identity-generated |
| `person` | `person_id` | no | **no** | many-to-one, mandatory, full cascade, eager |

### The asymmetry with `customer` — a material finding

| Aspect | `customer` | `salesperson` |
|---|---|---|
| association type | one-to-one | **many-to-one** |
| unique constraint on `person_id` | `UK_CUSTOM_PERSON_ID` | **none** |
| consequence | one customer per person, database-enforced | **several salesperson rows may share one person** |
| reference from `orders` | mandatory | optional (nullable) |

Nothing in the application code compensates for the missing constraint: the
service-level existence check resolves by the person's identification number and
would normally prevent a duplicate, but any path that bypasses it — or two
concurrent creates — can produce duplicate salesperson rows for the same person.
A rewrite must decide deliberately whether this is intended.

## Components

| Role | Component | File |
|---|---|---|
| controller | `DataSalespeopleController` | `api/controllers/DataSalespeopleController.java` |
| crud service | `SalespeopleCrudServiceImpl` | `jpa/services/crud/impl/SalespeopleCrudServiceImpl.java` |
| converter | `SalespeopleConverterServiceImpl` | `jpa/services/conversion/impl/SalespeopleConverterServiceImpl.java` |
| patch service | `SalespeoplePatchServiceImpl` | `jpa/services/patch/impl/SalespeoplePatchServiceImpl.java` |
| predicate service | `SalespeoplePredicateServiceImpl` | `jpa/services/predicates/impl/SalespeoplePredicateServiceImpl.java` |
| sort spec | `SalespeopleSortSpec` | `jpa/sortspecs/SalespeopleSortSpec.java` |
| repository | `SalespeopleRepository` | `jpa/repositories/SalespeopleRepository.java` |
| transport model | `PersonPojo` (shared with `person`) | `api/models/PersonPojo.java` |

As with `customer`, the whole stack is typed on **`PersonPojo`** — there is no
salesperson-specific transport model anywhere in the codebase, so the salesperson
surrogate key is never exposed in a response.

## Dependencies

- **Depends on**: `person` — every salesperson row requires a person row, created
  or reused through the cascading association.
- **Depended on by**: `order` — an order may optionally credit a salesperson.

## Shared entity status

`Salesperson` is a **shared entity**: owned by `salesperson`, referenced by
`order` through the nullable `orders.salesperson_id`. `Person` (owned by
`person`) is used here.
See `cross-cutting-documentation/shared-entities.md`.

## Exposure

`/data/salespeople` exposes read, create, replace and delete behind class-level
authentication and the `salespeople:*` authorities. There is **no
partial-update route**, even though `SalespeoplePatchServiceImpl` exists and is
wired as a bean. The same omission applies to `customers`, `users` and
`user_roles`: the four person-and-account domains are exactly the ones without
`PATCH`, while `images`, `products`, `product_lists`, `product_categories`,
`shippers` and `orders` all expose it.

Seeded authority holders: Administrator holds all four; Manager holds
`salespeople:create`, `salespeople:update` and `salespeople:read`;
Salesperson holds `salespeople:read`; the Customer role holds none.
