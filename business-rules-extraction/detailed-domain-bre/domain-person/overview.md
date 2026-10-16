# Domain: Person Registry (`person`)

## Responsibility

Owns the canonical record of a natural person: legal name, national identification
number, e-mail and two phone numbers. Every human-facing concept in the system
(login account, customer, salesperson) points at exactly one person row rather
than duplicating the personal data. The domain itself is read-mostly through the
REST surface — it exposes only a paged read endpoint — but it is written to by
three other domains (`user` during registration and profile editing, `customer`
during customer creation and guest checkout, `salesperson` during salesperson
creation).

## Bounded context summary

Master-data context for personal identity. It knows nothing about authentication,
orders or money. Its single invariant — one person per national ID number — is the
reason it must stay a separate context even though it has no write endpoints of its
own: three other contexts depend on that uniqueness guarantee.

## Owned data

| Table | Entity | Source file |
|---|---|---|
| `people` | `Person` | `src/main/java/org/trebol/jpa/entities/Person.java` |

### `Person` fields (ORM-declared)

| Field | Column | Nullable | Size constraint | Notes |
|---|---|---|---|---|
| `id` | `person_id` | no | — | identity-generated |
| `firstName` | `person_first_name` | no | `@Size(min=1,max=200)` | indexed |
| `lastName` | `person_last_name` | no | `@Size(min=1,max=200)` | indexed |
| `idNumber` | `person_id_number` | no, **unique** | `@Size(min=1,max=20)` | indexed; business key |
| `email` | `person_email` | no | `@Size(min=5,max=100)` | indexed; no `@Email` validation anywhere |
| `phone1` | `person_phone1` | no | — | `@Builder.Default = ""` |
| `phone2` | `person_phone2` | no | — | `@Builder.Default = ""` |

`Person` implements the shared `DBEntity` contract and carries a copy-constructor
used by the patch service.

## Components

| Role | Component | File |
|---|---|---|
| controller | `DataPeopleController` | `api/controllers/DataPeopleController.java` |
| crud service | `PeopleCrudServiceImpl` | `jpa/services/crud/impl/PeopleCrudServiceImpl.java` |
| converter | `PeopleConverterServiceImpl` | `jpa/services/conversion/impl/PeopleConverterServiceImpl.java` |
| patch service | `PeoplePatchServiceImpl` | `jpa/services/patch/impl/PeoplePatchServiceImpl.java` |
| predicate service | `PeoplePredicateServiceImpl` | `jpa/services/predicates/impl/PeoplePredicateServiceImpl.java` |
| sort spec | `PeopleSortSpec` | `jpa/sortspecs/PeopleSortSpec.java` |
| repository | `PeopleRepository` | `jpa/repositories/PeopleRepository.java` |
| transport model | `PersonPojo` | `api/models/PersonPojo.java` |

## Dependencies

- **Depends on**: nothing. The domain has no outbound references to other domains.
- **Depended on by**: `user` (account profile, registration), `customer`,
  `salesperson`. All three persist or read `Person` rows directly.

## Shared entity status

`Person` is a **shared entity** (`cross-cutting-documentation/shared-entities.md`).
It is referenced by `app_users.person_id`, `customers.person_id` and
`salespeople.person_id`. `person` is the owning domain.

## Notable asymmetry

`Customer.person` is mapped `@OneToOne(optional=false, cascade=ALL)` and the
`customers.person_id` column is UNIQUE, whereas `Salesperson.person` is mapped
`@ManyToOne(optional=false, cascade=ALL)` with no unique constraint on
`salespeople.person_id`. A single person may therefore back several salesperson
rows but only one customer row.
