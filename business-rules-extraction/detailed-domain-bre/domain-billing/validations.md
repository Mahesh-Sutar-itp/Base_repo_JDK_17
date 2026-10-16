# Validations — Billing (`billing`)

## Transport-model validation

| Model | Field | Constraint |
|---|---|---|
| `BillingTypePojo` | `name` | `@NotBlank` |
| `BillingCompanyPojo` | `idNumber` | **none** |
| `BillingCompanyPojo` | `name` | **none** |

Source: `api/models/BillingTypePojo.java`,
`api/models/BillingCompanyPojo.java`.

`BillingCompanyPojo` is one of only a handful of transport models in the codebase
with no validation annotations at all. It travels nested inside the order payload,
and the order payload does not cascade validation into it (only `details` and
`customer` carry the nested-validation marker), so company data arrives entirely
unvalidated.

## Persistence-layer validation

| Column | Constraint |
|---|---|
| `billing_type_name` | not null, unique, max 100, ORM min 1 |
| `billing_company_id_number` | not null, unique, max 20, ORM min 1 |
| `billing_company_name` | not null, unique, max 100, ORM min 1 |

## Service-layer validation

| Check | Component | Failure |
|---|---|---|
| billing-type name present | `BillingTypesCrudServiceImpl.getExisting` | bad input, `Billing type has no name` |
| company ID number present | `BillingCompaniesCrudServiceImpl.getExisting` | bad input, `Billing company has no id number` |
| billing type must exist for an order | `OrdersConverterServiceImpl.convertBillingInformationForEntity` | bad input, `Specified billing type does not exist` |
| billing type / company must not already exist on create | `CrudGenericService.validateInputPojoBeforeCreation` | already exists, `The item already exists` |

## Cross-domain validation on this domain's data

The order converter is the only live validator of billing data. It enforces
billing-type existence (hard failure) but enforces **nothing** about company data
beyond the service-level blank-ID-number check: company name length, format and
plausibility are unchecked, and the enterprise branch will happily create a
company row from whatever the checkout payload contained.

## Gaps

- **Company data is unvalidated end to end.** No transport constraints, no format
  check on the tax/ID number, no cross-check that the name matches the stored
  company when an existing ID number is reused.
- **Configured ID-number regular expression is never applied.** The
  `trebol.validation.id-number-regexp` value and its adapter service exist but no
  billing caller invokes them.
- **Company name uniqueness is over-strict.** Two distinct legal entities with the
  same trading name cannot coexist, and the failure surfaces as an infrastructure
  constraint error during checkout rather than a business message.
- **Reusing an existing company silently discards the submitted name**, so a
  corrected company name captured at checkout is lost without notice.
- **No access control on the only exposed route.** The billing-type list is
  anonymously readable and no `billing_types:*` authority code exists, so billing
  cannot be brought under the role model without adding both the codes and the
  annotations.
- **No referential guard before (unreachable) deletes** of either entity.
