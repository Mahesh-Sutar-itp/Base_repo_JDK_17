# Validations — Shipper (`shipper`)

## Transport-model validation

| Field | Constraint | Violation outcome |
|---|---|---|
| `name` | `@NotBlank` | validation envelope, code `REJECTED_02` |

Source: `api/models/ShipperPojo.java`. Applied on `POST` and `PUT`. `PATCH`
accepts a raw change map and is therefore uncovered.

## Persistence-layer validation

| Column | Constraint |
|---|---|
| `shipper_name` | not null, unique, `varchar(255)` |

There is **no** ORM length annotation, so the maximum length is a database-only
rule. This is unique among the reference-data entities in this codebase —
`BillingType`, `PaymentType`, `OrderStatus`, `UserRole` and `Image` all declare
`@Size(min=1,max=...)`.

## Service-layer validation

| Check | Component | Failure |
|---|---|---|
| name present | `ShippersCrudServiceImpl.getExisting` | bad input, literal message `Billing type has no name` (defect) |
| carrier must not already exist on create | `CrudGenericService.validateInputPojoBeforeCreation` | already exists, `The item already exists` |
| selecting parameters present on update/patch | `DataCrudGenericController` | bad input, `Missing request params` |
| at most one row selected on update/patch | `CrudGenericService` | runtime failure, `Cannot update more than one item at a time` |
| at least one row selected on update/patch | `DataCrudGenericController` | not found, `No element was found to update` |
| at least one row selected on delete | `CrudGenericService.delete` | not found, `Requested item(s) not found` |

## Cross-domain validation performed elsewhere on this domain's data

| Check | Where | Failure |
|---|---|---|
| carrier name must resolve during order creation | `OrdersConverterServiceImpl.convertShippingInformationForEntity` | bad input, `Specified shipper does not exist` |
| carrier name on an order patch | `OrdersPatchServiceImpl` | **none** — unknown names are discarded silently |

## Gaps

- **Mis-worded rejection message.** The blank-name error reports a billing-type
  problem. Functionally harmless, user-visibly wrong.
- **No length validation in application code.** A too-long name produces an
  infrastructure error instead of the clean validation envelope.
- **No referential guard before delete.** Deleting a referenced carrier yields a
  constraint violation rather than a business error.
- **Read route is unauthenticated** while the authority intended to protect it
  (`shippers:read`) exists and is granted but never checked.
- **No retirement mechanism.** Without a disabled flag, operators cannot withdraw
  a carrier from selection while preserving order history.
