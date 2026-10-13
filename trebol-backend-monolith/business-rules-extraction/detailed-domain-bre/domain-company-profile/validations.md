# Validations — Company Profile (`company-profile`)

## Transport-model validation

**None.** `CompanyDetailsPojo` carries no validation annotations and is an
outbound-only model — it is never deserialised from a request body because the
domain has no write route. It is also the only transport model in the `api/models`
package that declares no builder.

Source: `api/models/CompanyDetailsPojo.java`.

## Persistence-layer validation

| Column | Constraint |
|---|---|
| `param_category` | not null, max 25, ORM min 1 |
| `param_name` | not null, max 50, ORM min 1 |
| `param_value` | not null, max 500, ORM min 1 |
| `(param_category, param_name)` | composite unique |

Source: `jpa/entities/Param.java`,
`database-documentation/00-global-schema.sql`.

Because there is no application write path, these constraints only ever apply to
direct database edits and to the bootstrap seed script.

## Service-layer validation

**None.** `CompanyServiceImpl.readDetails` declares no checked exception, performs
no null or emptiness check and has no failure branch. Every outcome — fully
populated, partially populated, completely empty — is a successful `200`.

Source: `api/services/impl/CompanyServiceImpl.java`.

## Authorization validation

**None.** No authentication requirement, no authority requirement, and no
`params:*` or `app_params:*` code in the authority catalogue.

Source: `api/controllers/PublicAboutController.java`,
`database-documentation/00-seed-data.json`.

## Gaps

- **Silent configuration drift.** A mistyped parameter name is indistinguishable
  from a missing one: both yield an absent response field with no diagnostic. A
  rewrite should at minimum log unrecognised names.
- **No URL validation.** `bannerImageURL` and `logoImageURL` are free text; a
  broken or hostile URL is served to every storefront client. This is the one
  place where the absence of validation has a direct client-side impact, since
  the values are rendered as image sources.
- **No length-aware truncation.** A description longer than 500 characters cannot
  be stored at all, and the failure happens at the database rather than being
  reported as a business rule.
- **No write-path validation to design against.** Because no write surface
  exists, a rewrite is adding validation rather than porting it: there is no
  existing behaviour to preserve for creates or updates.
- **The EAV shape defeats typed validation.** Every value is a 500-character
  string regardless of its meaning, so numeric or boolean parameters could not be
  validated without application-level type knowledge that does not exist here.
