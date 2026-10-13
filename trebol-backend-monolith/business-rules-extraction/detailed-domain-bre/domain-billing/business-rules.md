# Business Rules — Billing (`billing`)

## BR-BILLING-001 Billing-type names are unique

`billing_types.billing_type_name` carries UNIQUE KEY `UK_BILL_TYP_NAME`.
Asserting: two billing types cannot share a name.
Source: `jpa/entities/BillingType.java`,
`database-documentation/00-global-schema.sql`.

## BR-BILLING-002 Exactly two billing types are meaningful to the system

The order converter branches on the literal names `Bill` and
`Enterprise Invoice`, declared as constants. Asserting: adding a third billing
type row would be selectable on an order but would fall through the enterprise
branch and be treated as an individual bill — no company and no billing address
would be recorded.
Source: `config/Constants.java`,
`jpa/services/conversion/impl/OrdersConverterServiceImpl.java`.

## BR-BILLING-003 An order must declare a billing type that exists

`convertBillingInformationForEntity` rejects an unknown billing type with the
bad-input message `Specified billing type does not exist`. Asserting: an order
payload naming a non-existent billing type is a 400, not a 500, and no order row
is written.
Source: `jpa/services/conversion/impl/OrdersConverterServiceImpl.java`.

## BR-BILLING-004 The billing type on an order is immutable after creation

`orders.billing_type_id` is not null and `Order.billingType` is mapped
`updatable=false` with `optional=false`. Asserting: no API path can switch an
existing order between individual and enterprise billing. The order patch service
exposes no `billingType` key.
Source: `jpa/entities/Order.java`,
`jpa/services/patch/impl/OrdersPatchServiceImpl.java`.

## BR-BILLING-005 A blank billing-type name is rejected at the service layer

`BillingTypesCrudServiceImpl.getExisting` throws a bad-input error with the
literal message `Billing type has no name`. Asserting: the rejection is a
400-class error. (The same literal is duplicated in the shipper service — see
BR-SHIPPER-003 — so this message is not a reliable discriminator for callers.)
Source: `jpa/services/crud/impl/BillingTypesCrudServiceImpl.java`.

## BR-BILLING-006 The billing-type name is mandatory on the transport model

`BillingTypePojo.name` is `@NotBlank`.
Source: `api/models/BillingTypePojo.java`.

## BR-BILLING-007 Enterprise invoicing requires a billing company

In the enterprise branch the order converter resolves or creates a
`BillingCompany` from the submitted company data. Asserting: an order declaring
`Enterprise Invoice` without usable company data cannot complete the enterprise
branch.
Source: `jpa/services/conversion/impl/OrdersConverterServiceImpl.java`.

## BR-BILLING-008 Enterprise invoicing also requires a billing address

The same branch resolves or creates the company's billing address and attaches it
to the order (`orders.billing_address_id`). Asserting: individual bills leave
`billing_address_id` null while enterprise invoices populate it.
Source: `jpa/services/conversion/impl/OrdersConverterServiceImpl.java`,
`jpa/entities/Order.java`.

## BR-BILLING-009 Company ID number and company name are each globally unique

`billing_companies` carries independent UNIQUE KEYs on both columns. Asserting:
two different companies cannot share a tax/ID number, and cannot share a name
either — so two legitimately identically-named entities with different ID numbers
cannot both be registered.
Source: `database-documentation/00-global-schema.sql`.

## BR-BILLING-010 The company ID number is the business key for de-duplication

`BillingCompaniesCrudServiceImpl.getExisting` resolves exclusively by ID number.
Asserting: submitting an existing ID number with a different company name reuses
the stored company and silently discards the submitted name; submitting a new ID
number with an existing name fails at the database name constraint.
Source: `jpa/services/crud/impl/BillingCompaniesCrudServiceImpl.java`.

## BR-BILLING-011 A blank company ID number is rejected

`getExisting` throws a bad-input error with the literal message
`Billing company has no id number`.
Source: `jpa/services/crud/impl/BillingCompaniesCrudServiceImpl.java`.

## BR-BILLING-012 Company ID numbers are never format-validated

`BillingCompany.idNumber` is only length-constrained (1–20). The configured
ID-number regular expression (`trebol.validation.id-number-regexp` =
`^\d{7,9}[\dk]$`) and the shared regular-expression adapter service exist, but no
billing code path calls them. Asserting: any non-blank string up to 20 characters
is an acceptable company ID number.
Source: `jpa/entities/BillingCompany.java`,
`common/services/impl/RegexMatcherAdapterServiceImpl.java` (no billing caller).

## BR-BILLING-013 `BillingCompanyPojo` carries no validation constraints at all

Neither `idNumber` nor `name` is annotated. Asserting: the only protection on
company data is the service-level blank check on the ID number plus the column
constraints. The company name may be blank at the transport layer and is then
rejected only by the not-null column.
Source: `api/models/BillingCompanyPojo.java`.

## BR-BILLING-014 Billing companies have no API surface

No controller injects any billing-company bean. Asserting: companies cannot be
listed, searched, corrected or deleted through the API; a typo in a company name
captured at checkout is unreachable and permanent.
Source: absence of any billing-company reference in `api/controllers/`.

## BR-BILLING-015 Blank values never clear stored billing data

`BillingTypesPatchServiceImpl` applies `name`, and
`BillingCompaniesPatchServiceImpl` applies `idNumber` and `name`, only when the
incoming value is non-blank.
Source: both patch service implementations.

## BR-BILLING-016 The billing-type list is world-readable and no billing authority exists

`DataBillingTypesController` declares no authentication requirement and no
authority requirement on its read method, and the security configuration defines
no route-matching rules, so the endpoint is reachable anonymously. Correspondingly
there is **no** `billing_types:*` code anywhere in the seeded authority catalogue —
billing is the only reference-data concept in the system with no authority codes
at all. Asserting: an unauthenticated caller can enumerate every billing type, and
no role grant can restrict that.
Source: `api/controllers/DataBillingTypesController.java`,
`config/SecurityConfig.java`, `database-documentation/00-seed-data.json`.

## BR-BILLING-019 Billing data has no authority codes, so it cannot be governed by role

The authority catalogue covers product categories, order statuses, customers,
products, orders, salespeople, users, user roles, people, images, shippers,
product lists and the standalone checkout authority — plus four unused
`order_types:*` codes. Nothing covers billing types or billing companies.
Asserting: billing is outside the role-based access model entirely; the only
protection on billing company data is that no route exposes it (BR-BILLING-014).
Source: `database-documentation/00-seed-data.json`.

## BR-BILLING-017 Deleting a billing type referenced by an order is blocked

`orders.billing_type_id` has foreign key `FK_ORDER_BILL_TYP_ID` with no cascade.
Asserting: the (unreachable) delete path would abort on the constraint for any
referenced type.
Source: `database-documentation/00-global-schema.sql`.

## BR-BILLING-018 No tax or document generation happens here

The 19% tax rate and all monetary arithmetic live in the order converter, not in
this domain; no invoice document, PDF or electronic tax filing is produced
anywhere in the codebase. Asserting: "invoicing" in this system means recording a
classification, not issuing a legal document.
Source: `jpa/services/conversion/impl/OrdersConverterServiceImpl.java`
(`TAX_PERCENT = 0.19`), repo-wide absence of any document generator.
