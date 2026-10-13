# Business Rules — Person Registry (`person`)

Rule IDs use the prefix `BR-PERSON-`. Every rule is stated as a testable
assertion and cites the file that enforces it.

## BR-PERSON-001 A national ID number identifies at most one person

`people.person_id_number` is UNIQUE (DDL) and `Person.idNumber` is
`@Column(unique=true)`. Asserting: creating a second person row with an existing
`person_id_number` must be rejected by the database.
Source: `jpa/entities/Person.java`, `database-documentation/00-global-schema.sql`.

## BR-PERSON-002 A person submitted without an ID number is rejected

`PeopleCrudServiceImpl.getExisting` throws a bad-input error with the literal
message `Customer does not have ID card` when the submitted `idNumber` is blank.
Asserting: a create/lookup call with a null or whitespace ID number fails with a
400-class error, not a 500.
Source: `jpa/services/crud/impl/PeopleCrudServiceImpl.java`.
Note: the message says "Customer" inside the person service — wording defect, the
behaviour is correct.

## BR-PERSON-003 Creating a person that already exists is a conflict

The inherited `validateInputPojoBeforeCreation` checks `getExisting(input)`; when
present it throws an already-exists error with the literal message
`The item already exists`. Asserting: a second create with the same ID number
yields the conflict error and no second row.
Source: `jpa/services/crud/CrudGenericService.java`.

## BR-PERSON-004 First name, last name, ID number and e-mail are mandatory

`PersonPojo` annotates all four with `@NotBlank`; the entity columns are
`nullable=false`. Asserting: a payload missing any of the four is rejected with
the validation error envelope before any service code runs.
Source: `api/models/PersonPojo.java`, `jpa/entities/Person.java`.

## BR-PERSON-005 E-mail is length-constrained but never format-validated

`Person.email` is `@Size(min=5,max=100)`. There is no `@Email` annotation and no
regular expression check anywhere in the codebase. Asserting: the string
`a@b.c` (5 characters) is accepted and so is `not-an-email-at-all`.
Source: `jpa/entities/Person.java`, `api/models/PersonPojo.java`.

## BR-PERSON-006 Phone numbers are stored unvalidated and default to empty

`phone1`/`phone2` are `nullable=false` with `@Builder.Default = ""`. A
`@PhoneNumber` constraint annotation and its validator exist
(`common/annotations/PhoneNumber.java`,
`common/annotations/validator/PhoneNumberValidator.java`) and a phone regular
expression is configured (`trebol.validation.phonenumber-regexp`), but the
annotation is applied to **zero fields** and the `PersonPojo` phone patterns are
commented out. Asserting: any string, including an empty one, is an acceptable
phone number.
Source: repo-wide absence of `@PhoneNumber` usages; `api/models/PersonPojo.java`.

## BR-PERSON-007 Blank values never clear a stored name, ID number or e-mail

`PeoplePatchServiceImpl` applies `idNumber`, `firstName`, `lastName` and `email`
only when the incoming value is non-blank. Asserting: patching `firstName` to `""`
leaves the stored first name unchanged.
Source: `jpa/services/patch/impl/PeoplePatchServiceImpl.java`.

## BR-PERSON-008 Blank values DO clear a stored phone number

The same patch service sets `phone1` and `phone2` unconditionally. Asserting:
patching `phone1` to `""` overwrites the stored value with an empty string — a
deliberate asymmetry against BR-PERSON-007.
Source: `jpa/services/patch/impl/PeoplePatchServiceImpl.java`.

## BR-PERSON-009 A patch that changes nothing performs no write

The inherited `flushPartialChanges` compares the patched copy with the original
and returns the original untouched when they are equal. Asserting: a no-op patch
issues no UPDATE statement.
Source: `jpa/services/crud/CrudGenericService.java`.

## BR-PERSON-010 Person identity filters discard all other filters

In `PeoplePredicateServiceImpl` the `id` and `idNumber` branches return
immediately. Asserting: `?idNumber=111111111&email=other@example.com` ignores the
e-mail condition entirely and resolves on ID number alone.
Source: `jpa/services/predicates/impl/PeoplePredicateServiceImpl.java`.

## BR-PERSON-011 A non-numeric value for a numeric filter is ignored, not rejected

The predicate service catches the number-parse failure and logs it at info level,
leaving that one condition out of the filter. Asserting: `?id=abc` returns an
unfiltered page rather than a 400.
Source: `jpa/services/predicates/impl/PeoplePredicateServiceImpl.java`.

## BR-PERSON-012 Reading the registry requires an explicit authority

`GET /data/people` requires authentication **and** the `people:read` authority.
Asserting: an authenticated principal without that authority receives an
authorization failure. Seeded roles holding it: Administrator, Manager,
Salesperson (see `00-seed-data.json`).
Source: `api/controllers/DataPeopleController.java`.

## BR-PERSON-013 The registry has no delete or write route

`DataPeopleController` implements only the read contract. Asserting: `POST`,
`PUT`, `PATCH` and `DELETE` on `/data/people` are not routed. Person rows are
therefore never removed through the API; deleting a user or customer does not
delete the underlying person.
Source: `api/controllers/DataPeopleController.java`.
