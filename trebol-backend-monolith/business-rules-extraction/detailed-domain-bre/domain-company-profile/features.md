# Features — Company Profile (`company-profile`)

## F-001 Public company profile retrieval

`GET /public/about` — no authentication, no authority. Returns a flat object with
four fields: `name`, `description`, `bannerImageURL` and `logoImageURL`. This is
the storefront's branding source and is typically the first call a client makes.

## F-002 Category-scoped parameter loading

`ParamsRepository.findParamsByCategory` loads every parameter row whose category
matches a given string. The service calls it with the literal `company`. The
query returns an iterable, not a page — there is no pagination on parameters.

## F-003 Name-driven field mapping

`CompanyServiceImpl.readDetails` iterates the loaded rows and switches on each
row's `name`, assigning the row's `value` to the matching field of the response
object. Four names are recognised: `name`, `description`, `bannerImageURL` and
`logoImageURL`.

## F-004 Tolerance of unknown and missing parameters

The switch has a default branch that does nothing, so unrecognised parameter
names are silently skipped. Conversely, a recognised field whose row is absent
simply stays unset in the response. The feature never fails on configuration
drift.

## F-005 Serialisation-driven response shaping

`spring.jackson.default-property-inclusion=non_default` is configured globally,
so unset profile fields are **omitted** from the response body rather than
serialised as null. A completely unconfigured profile therefore returns an empty
object with status `200`, not an error.

## Features deliberately absent

| Expected capability | Status |
|---|---|
| update the company profile | **absent** — no write route, no patch service, no CRUD service |
| list or search parameters generically | **absent** — no controller exposes `app_params` |
| read any category other than `company` | **absent** — the category literal is hardcoded in the service |
| validate the image URLs | **absent** — values are opaque strings |
| cache the profile | **absent** — every call re-queries the database |
