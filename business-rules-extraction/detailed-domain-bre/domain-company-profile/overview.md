# Domain: Company Profile (`company-profile`)

## Responsibility

Owns the application's own key/value parameter store and the single business
feature built on it: the public "about" profile of the shop (name, description,
banner image URL, logo image URL). The parameter table is a generic
entity-attribute-value store scoped by a category string; the only category the
application reads is `company`.

## Bounded context summary

Application-configuration context. One aggregate root (`Param`) whose rows are
addressed by the composite business key `(category, name)`. It is a separate
context because it is the only read-only, non-transactional, storefront-facing
surface in the system and because its data model is deliberately schemaless.

## Owned data

| Table | Entity | Source file |
|---|---|---|
| `app_params` | `Param` | `jpa/entities/Param.java` |

### `Param` fields

| Field | Column | Nullable | Size constraint |
|---|---|---|---|
| `id` | `param_id` | no | — |
| `category` | `param_category` | no | `@Size(min=1,max=25)` |
| `name` | `param_name` | no | `@Size(min=1,max=50)` |
| `value` | `param_value` | no | `@Size(min=1,max=500)` |

Composite uniqueness constraint `UK_PARAM` over `(param_category, param_name)`.

### Phantom-table warning

`app_params` is an entity-attribute-value table. The seeded rows
`company/name`, `company/description`, `company/bannerImageURL` and
`company/logoImageURL` are **rows**, not tables and not columns. Any
reverse-engineering of this system must not promote them to schema objects. See
the phantom-table note in `database-documentation/00-global-schema.sql`.

## Components

| Role | Component | File |
|---|---|---|
| controller | `PublicAboutController` | `api/controllers/PublicAboutController.java` |
| service | `CompanyServiceImpl` | `api/services/impl/CompanyServiceImpl.java` |
| service interface | `CompanyService` | `api/services/CompanyService.java` |
| repository | `ParamsRepository` | `jpa/repositories/ParamsRepository.java` |
| transport model | `CompanyDetailsPojo` | `api/models/CompanyDetailsPojo.java` |

This is the only domain in the system with **no** converter, **no** patch
service, **no** predicate service, **no** sort specification and **no** generic
CRUD service. It does not participate in the generic CRUD framework at all.

## Dependencies

- **Depends on**: nothing.
- **Depended on by**: nothing. No other domain reads `app_params`.

## Shared entity status

`Param` is **not** shared: `app_params` is referenced by no other table and read
by no other domain.

## Exposure

`GET /public/about` is fully public (no authentication, no authority) and returns
the four profile values. There is **no** write surface of any kind: the company
profile can only be changed by direct database access.
