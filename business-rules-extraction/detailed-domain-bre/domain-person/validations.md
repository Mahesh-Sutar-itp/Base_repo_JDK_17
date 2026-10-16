# Validations — Person Registry (`person`)

## Transport-model validation (runs before any service code)

| Field | Constraint | Violation outcome |
|---|---|---|
| `firstName` | `@NotBlank` | validation error envelope, code `REJECTED_02` |
| `lastName` | `@NotBlank` | validation error envelope, code `REJECTED_02` |
| `idNumber` | `@NotBlank` | validation error envelope, code `REJECTED_02` |
| `email` | `@NotBlank` | validation error envelope, code `REJECTED_02` |
| `phone1` | none | accepted as-is |
| `phone2` | none | accepted as-is |

Source: `api/models/PersonPojo.java`. The envelope lists each offending field as
`field: message`, newline-separated, in the detail message.

Important: `PersonPojo` is validated only where the enclosing endpoint applies the
validation marker. `PUT /account/profile` accepts a `PersonPojo` **without** it,
so the four mandatory-field checks are bypassed on that route and only the
persistence-layer `nullable=false` constraints remain.

## Persistence-layer validation

| Field | Constraint |
|---|---|
| `firstName` | not null, `@Size(min=1,max=200)` |
| `lastName` | not null, `@Size(min=1,max=200)` |
| `idNumber` | not null, unique, `@Size(min=1,max=20)` |
| `email` | not null, `@Size(min=5,max=100)` |
| `phone1` | not null, defaults to empty string |
| `phone2` | not null, defaults to empty string |

Source: `jpa/entities/Person.java`.

## Service-layer validation

| Check | Component | Failure |
|---|---|---|
| ID number must be present for lookup | `PeopleCrudServiceImpl.getExisting` | bad input, `Customer does not have ID card` |
| person must not already exist on create | `CrudGenericService.validateInputPojoBeforeCreation` | already exists, `The item already exists` |
| ID number must not already belong to another account | `ProfileServiceImpl.updateProfileForUserWithName` | bad input, `Person profile is associated to another account. Cannot use it.` |
| ID number must be unregistered at registration | `RegistrationServiceImpl.register` | already exists, `That ID number is already registered and associated to an account.` |

## Validation that exists but is never applied

- `@PhoneNumber` + `PhoneNumberValidator` with
  `trebol.validation.phonenumber-regexp` = `^\+(?:[0-9] ?){6,14}[0-9]$`:
  declared, compiled, never annotated onto a field.
- `trebol.validation.id-number-regexp` = `^\d{7,9}[\dk]$` is exposed through
  the shared regular-expression adapter service (`isAValidIdNumber`), but no
  person create/patch path calls it. ID numbers are therefore only length-checked.
- The commented-out phone pattern annotations in `PersonPojo`.

A rewrite must decide deliberately whether to activate these; reproducing current
behaviour means leaving phone and ID-number format unvalidated.
