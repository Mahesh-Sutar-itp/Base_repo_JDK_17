# Business Rules — Company Profile (`company-profile`)

## BR-COMPANY-001 A parameter is identified by the pair (category, name)

`app_params` carries composite UNIQUE KEY `UK_PARAM` over
`(param_category, param_name)`. Asserting: the same parameter name may exist in
two different categories, but not twice within one category.
Source: `jpa/entities/Param.java`,
`database-documentation/00-global-schema.sql`.

## BR-COMPANY-002 Only the `company` category is read by the application

`CompanyServiceImpl.readDetails` passes the literal `company` to the repository.
Asserting: parameter rows in any other category are inert — no code path reads
them. The table is a general-purpose store with exactly one consumer.
Source: `api/services/impl/CompanyServiceImpl.java`.

## BR-COMPANY-003 Exactly four parameter names are meaningful

The service recognises `name`, `description`, `bannerImageURL` and
`logoImageURL`. Asserting: adding a fifth `company` row has no observable effect
on the API response.
Source: `api/services/impl/CompanyServiceImpl.java`,
`database-documentation/00-seed-data.json`.

## BR-COMPANY-004 Unknown parameter names are silently ignored

The name switch has a do-nothing default branch. Asserting: a typo in a
parameter name (for example `logoImageUrl` instead of `logoImageURL`) produces no
error, no log entry and no value — the field is simply missing from the response.
Source: `api/services/impl/CompanyServiceImpl.java`.

## BR-COMPANY-005 A missing parameter never fails the request

Absent rows leave the corresponding response field unset. Asserting: with an
empty `app_params` table, `GET /public/about` returns `200` with an empty body
object, not a `404` or a `500`.
Source: `api/services/impl/CompanyServiceImpl.java` (no emptiness check, no
exception path).

## BR-COMPANY-006 Unset profile fields are omitted from the response, not nulled

The global serialisation setting
`spring.jackson.default-property-inclusion=non_default` suppresses default-valued
properties. Asserting: a client cannot distinguish "configured as empty" from
"not configured" — both yield an absent key.
Source: `src/main/resources/application.properties`.

## BR-COMPANY-007 The company profile is world-readable

`PublicAboutController` declares no authentication requirement and no authority
requirement, and the security configuration defines no route-matching rules.
Asserting: anonymous callers receive the profile. This is intended for storefront
branding.
Source: `api/controllers/PublicAboutController.java`,
`config/SecurityConfig.java`.

## BR-COMPANY-008 The company profile has no write path

No controller, service, converter or patch service writes `app_params`.
Asserting: changing the shop name requires a direct database statement; there is
no API, no admin screen contract and no authority code for it. No
`app_params:*` or `params:*` authority exists in the catalogue.
Source: absence of any `app_params` write in `src/main/java`,
`database-documentation/00-seed-data.json`.

## BR-COMPANY-009 Parameter values are opaque strings up to 500 characters

`Param.value` is `@Size(min=1,max=500)` and not null. Asserting: the two image
URL parameters are never validated as URLs, and the description is never
validated as text of any particular shape or encoding.
Source: `jpa/entities/Param.java`.

## BR-COMPANY-010 Parameter values cannot be empty

`param_value` is not null with an ORM minimum length of 1. Asserting: clearing a
parameter requires deleting its row, not blanking its value — though since there
is no write path (BR-COMPANY-008) this only constrains direct database edits.
Source: `jpa/entities/Param.java`.

## BR-COMPANY-011 The profile is re-queried on every request

There is no caching abstraction anywhere in the codebase and no in-memory
memoisation in this service. Asserting: every `GET /public/about` executes the
category query, making this the hottest trivially cacheable endpoint in the
system.
Source: `api/services/impl/CompanyServiceImpl.java`,
`cross-cutting-documentation/caching-patterns.md`.

## BR-COMPANY-012 The identity generator must be advanced past seeded rows

`data.sql` ends with a statement restarting the `app_params` identity generator
at 5 because four rows were inserted with explicit identifiers. Asserting: a
rewrite that seeds explicit identifiers on any database must perform the
equivalent sequence fix-up or the first inserted parameter collides on the
primary key.
Source: `src/main/resources/data.sql`,
`database-documentation/00-seed-data.json`.

## BR-COMPANY-013 Parameter rows are not an extension point for other domains

No other domain reads or writes `app_params`; every other configurable value in
the system comes from the external configuration file (`trebol.*` keys), not from
this table. Asserting: the EAV store is **not** the system's settings mechanism —
it holds only storefront branding.
Source: repo-wide search for `ParamsRepository` usage (single consumer),
`config/ApiProperties.java` and the other configuration holders.
