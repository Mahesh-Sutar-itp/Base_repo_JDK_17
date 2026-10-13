# Domain: Billing (`billing`)

## Responsibility

Owns the two concepts that decide how an order is invoiced: the **billing type**
catalogue (`Bill` for an individual, `Enterprise Invoice` for a company) and the
**billing company** register (company name plus company tax/ID number). Nothing
in this domain computes tax, produces a document or talks to a tax authority — it
supplies the classification and the company identity that the `order` domain
stamps onto an order.

## Bounded context summary

Invoicing-classification context. Two entities share one aggregate concern: the
billing branch chosen for an order determines whether a billing company is
required at all, so the two are mutated together within the same order-creation
transaction and are documented as one context.

## Owned data

| Table | Entity | Source file |
|---|---|---|
| `billing_types` | `BillingType` | `jpa/entities/BillingType.java` |
| `billing_companies` | `BillingCompany` | `jpa/entities/BillingCompany.java` |

### `BillingType` fields

| Field | Column | Nullable | Unique | Size constraint |
|---|---|---|---|---|
| `id` | `billing_type_id` | no | — | — |
| `name` | `billing_type_name` | no | yes | `@Size(min=1,max=100)` |

Seeded rows: `Bill` (id 1) and `Enterprise Invoice` (id 2). Both names are
hard-coded as constants in `config/Constants.java`
(`BILLING_TYPE_INDIVIDUAL`, `BILLING_TYPE_ENTERPRISE`).

### `BillingCompany` fields

| Field | Column | Nullable | Unique | Size constraint |
|---|---|---|---|---|
| `id` | `billing_company_id` | no | — | — |
| `idNumber` | `billing_company_id_number` | no | yes | `@Size(min=1,max=20)` |
| `name` | `billing_company_name` | no | yes | `@Size(min=1,max=100)` |

Both business columns have independent UNIQUE KEYs
(`UK_BILL_CY_ID_NO`, `UK_BILL_CY_NAME`).

## Components

| Role | Component | File |
|---|---|---|
| controller | `DataBillingTypesController` | `api/controllers/DataBillingTypesController.java` |
| crud service | `BillingTypesCrudServiceImpl` | `jpa/services/crud/impl/BillingTypesCrudServiceImpl.java` |
| crud service | `BillingCompaniesCrudServiceImpl` | `jpa/services/crud/impl/BillingCompaniesCrudServiceImpl.java` |
| converter | `BillingTypesConverterServiceImpl` | `jpa/services/conversion/impl/BillingTypesConverterServiceImpl.java` |
| converter | `BillingCompaniesConverterServiceImpl` | `jpa/services/conversion/impl/BillingCompaniesConverterServiceImpl.java` |
| patch service | `BillingTypesPatchServiceImpl` | `jpa/services/patch/impl/BillingTypesPatchServiceImpl.java` |
| patch service | `BillingCompaniesPatchServiceImpl` | `jpa/services/patch/impl/BillingCompaniesPatchServiceImpl.java` |
| predicate service | `BillingTypesPredicateServiceImpl` | `jpa/services/predicates/impl/BillingTypesPredicateServiceImpl.java` |
| predicate service | `BillingCompaniesPredicateServiceImpl` | `jpa/services/predicates/impl/BillingCompaniesPredicateServiceImpl.java` |
| sort spec | `BillingTypesSortSpec` | `jpa/sortspecs/BillingTypesSortSpec.java` |
| repository | `BillingTypesRepository` | `jpa/repositories/BillingTypesRepository.java` |
| repository | `BillingCompaniesRepository` | `jpa/repositories/BillingCompaniesRepository.java` |
| transport model | `BillingTypePojo` | `api/models/BillingTypePojo.java` |
| transport model | `BillingCompanyPojo` | `api/models/BillingCompanyPojo.java` |

## REST exposure imbalance

Billing types expose a single **read-only, fully public** route
(`GET /data/billing_types`): `DataBillingTypesController` declares no
class-level authentication requirement and no method-level authority
requirement, and the security configuration contains no route-matching rules, so
any unauthenticated caller can list billing types. Billing companies expose
**no route at all**: the full CRUD
service, converter, patch service and predicate service exist and are wired as
beans, but no controller references them. Billing company rows are created only
as a side effect of enterprise-invoice checkout.

## Dependencies

- **Depends on**: nothing.
- **Depended on by**: `order` — the order converter resolves the billing type by
  name and resolves-or-creates the billing company and its billing address.

## Shared entity status

Both `BillingType` and `BillingCompany` are **shared entities**: owned by
`billing`, referenced by `order` through `orders.billing_type_id` (not null) and
`orders.billing_company_id` (nullable).
See `cross-cutting-documentation/shared-entities.md`.
