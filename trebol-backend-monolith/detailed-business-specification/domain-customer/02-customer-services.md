# Customer — Services, Controller and Guest Provisioning

Porting-depth behavioural specification. Companion to `00-domain-index.md` and
`01-customer-entity-and-transport.md`. Paths relative to `src/main/java/org/trebol/`.

---

## 1. Service contracts

| Contract | Extends | Declares |
|---|---|---|
| `jpa/services/crud/CustomersCrudService` | `CrudService<PersonPojo, Customer>` | nothing of its own |
| `jpa/services/conversion/CustomersConverterService` | `ConverterService<PersonPojo, Customer>` | nothing of its own |
| `jpa/services/patch/CustomersPatchService` | `PatchService<PersonPojo, Customer>` | nothing of its own |
| `jpa/services/predicates/CustomersPredicateService` | `PredicateService<Customer>` | `QCustomer basePath = QCustomer.customer;` |

All four are empty marker interfaces over the generic kernel, except the predicate
service's static `basePath`. That matters for the port: **the behaviour of this domain
lives almost entirely in `CrudGenericService`**, which is shared-kernel code the Identity
service must carry a copy of (`00` §3.4).

---

## 2. `jpa/repositories/CustomersRepository.java`

```java
@Repository
public interface CustomersRepository
    extends Repository<Customer> {

    @Query(value = "SELECT c FROM Customer c JOIN FETCH c.person p WHERE p.idNumber = :idNumber")
    Optional<Customer> findByPersonIdNumber(@Param("idNumber") String idNumber);
}
```

**One declared method. Everything else is inherited** from `org.trebol.jpa.Repository<E>`
(itself `JpaRepository` + `QuerydslPredicateExecutor`). No `@Modifying`, no native query,
no `@EntityGraph`.

The `JOIN FETCH` is redundant in practice — `Customer.person` is already EAGER
(`01` §1.3) — but it is explicit and must be ported verbatim: removing it would change
the emitted SQL.

**Porting note (G8 relevance).** `org.trebol.jpa.Repository` is **not** annotated
`@NoRepositoryBean`. That is why no `@DataJpaTest` slice can bootstrap this project —
scanning fails with *"Not a managed type: interface org.trebol.jpa.DBEntity"*. Identity
inherits that limitation, so its integration-style tests must be `@SpringBootTest`.
Recorded on `UserRolePermissionUniquenessTest` and on
`RegistrationServiceImplTransactionalTest`.

---

## 3. `jpa/services/crud/impl/CustomersCrudServiceImpl.java`

### 3.1 Declaration

```java
@Transactional
@Service
public class CustomersCrudServiceImpl
    extends CrudGenericService<PersonPojo, Customer>
    implements CustomersCrudService {
```

`@Transactional` is `org.springframework.transaction.annotation.Transactional` at
**class** level with **no attributes** — propagation `REQUIRED`, `readOnly = false`,
no `rollbackFor` overrides. Constructor-injects `CustomersRepository`,
`CustomersConverterService`, `CustomersPatchService`, passes all three to `super(...)`,
and additionally keeps its own `customersRepository` reference.

### 3.2 The single override

```java
@Override
public Optional<Customer> getExisting(PersonPojo input) throws BadInputException {
    String idNumber = input.getIdNumber();
    if (StringUtils.isBlank(idNumber)) {
        throw new BadInputException("Customer does not have an ID card");
    } else {
        return customersRepository.findByPersonIdNumber(idNumber);
    }
}
```

Steps, in order:
1. read `input.getIdNumber()` — **no null check on `input` itself**, so a null pojo is an
   NPE → HTTP 500. This is reachable: `CustomerProvisioningContractImpl` passes the
   order's customer straight through, and `OrdersConverterServiceImplTest`'s
   `no_customer_information` case covers exactly that path, which is why the contract
   declares and propagates `BadInputException` rather than hiding it;
2. `org.apache.commons.lang3.StringUtils.isBlank` — null, empty **and** whitespace-only
   all count as blank;
3. throw `BadInputException("Customer does not have an ID card")` (BR-CUSTOMER-006) —
   literal, G2;
4. otherwise delegate to the repository.

**No other method is overridden.** `create`, `update`, `partialUpdate`, `delete`,
`readOne` and `readMany` all come from `CrudGenericService` unchanged.

### 3.3 Inherited behaviour that this domain's rules depend on

From `jpa/services/crud/CrudGenericService.java` (class-level `@Transactional`, no
attributes). Two constants, both client-observable:

```java
protected static final String ITEM_NOT_FOUND = "Requested item(s) not found";
protected static final String ITEM_ALREADY_EXISTS = "The item already exists";
```

| Method | Behaviour | Rule |
|---|---|---|
| `create(M)` | `validateInputPojoBeforeCreation` → `converter.convertToNewEntity` → `repository.saveAndFlush` → `converter.convertToPojo`. Declares `throws BadInputException, EntityExistsException` | BR-CUSTOMER-003 |
| `validateInputPojoBeforeCreation(M)` | `if (getExisting(input).isPresent()) throw new EntityExistsException(ITEM_ALREADY_EXISTS);` → HTTP 409 | BR-CUSTOMER-007 |
| `update(M)` | `throw new UnsupportedOperationException("This method signature has been deprecated");` | — |
| `update(M, Long id)` | `convertToNewEntity(input)` → `inputEntity.setId(id)` → `saveAndFlush` → `convertToPojo`. **Full overwrite**; the nested `Person` id stays null, so `cascade = ALL` inserts a new person row | BR-CUSTOMER-013, `00` §2 W2 |
| `update(M, Predicate)` | `count > 1` → `throw new RuntimeException("Cannot update more than one item at a time")`; no match → `Optional.empty()`; else `update(input, firstMatch.getId())` | **BR-CUSTOMER-021** |
| `partialUpdate(Map, Long)` / `(Map, Predicate)` | `findById`/`findOne` → `flushPartialChanges`; a `BadInputException` is **wrapped**: `throw new RuntimeException(e)` → HTTP 500 | BR-CUSTOMER-011 |
| `flushPartialChanges` | `patchService.patchExistingEntity(changes, existing)`; `if (existing.equals(updated)) return existing;` else `saveAndFlush`. **Lombok equality includes `id` and `person`** (`01` §1.4) | — |
| `readOne(Predicate)` | empty → `throw new EntityNotFoundException(ITEM_NOT_FOUND)` | — |
| `readMany(int, int, Sort, Predicate)` | `PageRequest.of(...)`, count with/without predicate, `findAll`, convert each, return `new DataPagePojo<>(pojoList, pageIndex, totalCount, pageSize)` | — |
| `delete(Predicate)` | `count == 0` → `throw new EntityNotFoundException(ITEM_NOT_FOUND)`; else `repository.deleteAll(repository.findAll(filters))` | BR-CUSTOMER-014, BR-CUSTOMER-015 |

**The Phase 0 D1 fix sits above `delete`**, in `DataCrudGenericController`: a `DELETE`
whose parameters are all unrecognised now raises `BadInputException` → HTTP 400 instead of
matching every row and deleting the table. That guard ports with the shared kernel, and
`00` §2 of the user spec records that D19 leaves the same match-all shape reachable
through `update` and `partialUpdate` — deliberately, by ruling.

---

## 4. `jpa/services/conversion/impl/CustomersConverterServiceImpl.java`

`@Service` only — **no `@Transactional`.** Constructor-injects `PeopleConverterService`
and delegates wholly to it:

```java
@Override
public PersonPojo convertToPojo(Customer source) {
    return peopleConverterService.convertToPojo(source.getPerson());
}

@Override
public Customer convertToNewEntity(PersonPojo source) {
    Person targetPerson = peopleConverterService.convertToNewEntity(source);
    return Customer.builder()
        .person(targetPerson)
        .build();
}

@Override
public Customer applyChangesToExistingEntity(PersonPojo source, Customer target) {
    throw new UnsupportedOperationException("This method is deprecated");
}
```

Three facts for the port:

1. `convertToPojo` dereferences `source.getPerson()` with **no null guard**. Safe only
   because `optional = false` and EAGER fetch guarantee a person is present
   (BR-CUSTOMER-002, BR-CUSTOMER-022) — a latent NPE if either is ever relaxed.
2. `convertToNewEntity` builds the customer with **no id**, which is the mechanism behind
   `01` §2.1 and `00` §2 W2.
3. `applyChangesToExistingEntity` is a hard `UnsupportedOperationException` with the
   literal `"This method is deprecated"`. It is **never called** — `CrudGenericService`
   reaches for the patch service instead — but the literal is contract if it ever
   surfaces, so it ports verbatim (G2).

`PeopleConverterServiceImpl`'s mapping is specified in
`detailed-business-specification/domain-person/`. The only behaviour this domain relies on
is that **`phone1`/`phone2` are copied only when non-null**, in both directions.

---

## 5. `jpa/services/patch/impl/CustomersPatchServiceImpl.java`

`@Service` only — no `@Transactional`. Injects `PeoplePatchService`.

```java
@Override
public Customer patchExistingEntity(Map<String, Object> changes, Customer existing) throws BadInputException {
    Customer target = new Customer(existing);

    Map<String, Object> personChanges = Utils.copyMapWithUnprefixedEntries(changes, PERSON_DATA_MAP_KEYS_PREFIX);
    if (!personChanges.isEmpty()) {
        Person existingPerson = existing.getPerson();
        Person person = peoplePatchService.patchExistingEntity(personChanges, existingPerson);
        target.setPerson(person);
    }

    return target;
}

@Override
public Customer patchExistingEntity(PersonPojo changes, Customer existing) throws BadInputException {
    throw new UnsupportedOperationException("This method has been deprecated");
}
```

- The prefix is the **shared constant** `org.trebol.config.Constants.PERSON_DATA_MAP_KEYS_PREFIX`
  (`"person."`). `SalespeoplePatchServiceImpl` hardcodes the same string literal instead
  (BR-SALESPERSON-012) — an asymmetry to port verbatim, not to unify.
- `Utils.copyMapWithUnprefixedEntries` **replaces every occurrence** of the prefix, not
  just the leading one (BR-CUSTOMER-012).
- With an empty `personChanges` map the method returns an unmodified copy, which
  `flushPartialChanges` then finds `equals` to the original and **does not save**.
- Blank values never clear a stored name, id number or e-mail — that guard lives in
  `PeoplePatchServiceImpl` (BR-CUSTOMER-013).
- **This whole method is unreachable over HTTP** (BR-CUSTOMER-010, BR-CUSTOMER-011):
  `DataCustomersController` declares no `@PatchMapping`. It is still fully tested, and it
  must still be ported — `00` §2 W4.
- Second overload's literal is `"This method has been deprecated"`, which is **a
  different string** from the converter's `"This method is deprecated"`. Both exist in
  this codebase; do not normalise them (G2).

---

## 6. Controller — `api/controllers/DataCustomersController.java`

Class level, verbatim:

```java
@RestController
@RequestMapping("/data/customers")
@Tag(name = "People management")
@PreAuthorize("isAuthenticated()")
public class DataCustomersController
    extends DataCrudGenericController<PersonPojo, Customer> {
```

Note the `@Tag` is **`"People management"`**, shared with `DataSalespeopleController` and
`DataPeopleController` — three controllers under one OpenAPI tag.

| Verb | Path | `@PreAuthorize` | Signature | Status | Throws |
|---|---|---|---|---|---|
| GET | `/data/customers` | `hasAuthority('" + CUSTOMERS_READ + "')` | `DataPagePojo<PersonPojo> readMany(@RequestParam Map<String,String> allRequestParams)` | 200 | — |
| POST | `/data/customers` | `hasAuthority('" + CUSTOMERS_CREATE + "')` | `void create(@Valid @RequestBody PersonPojo input)` | **201** | `BadInputException, EntityExistsException` |
| PUT | `/data/customers` | `hasAuthority('" + CUSTOMERS_UPDATE + "')` | `void update(@Valid @RequestBody PersonPojo input, @RequestParam Map<String,String> requestParams)` | **204** | `EntityNotFoundException, BadInputException` |
| DELETE | `/data/customers` | `hasAuthority('" + CUSTOMERS_DELETE + "')` | `void delete(Map<String,String> requestParams)` | **204** | `BadInputException, EntityNotFoundException` |

`@Operation(summary = ...)` literals, verbatim: `"List customers."`,
`"Register new customer."`, `"Replace customers data."`, `"Deregister customers."` —
including the trailing full stops and the odd pluralisation of the last two.

Two deviations from the generic base to port exactly:

1. **`create` calls `crudService.create(input)` directly, not `super.create(...)`.**
2. **`delete`'s `Map` parameter has no `@RequestParam`** (`00` §2 W5); the salespeople
   equivalent does.

Plus the sort-spec hook:

```java
@Override
protected Map<String, OrderSpecifier<?>> getOrderSpecMap() {
    return CustomersSortSpec.ORDER_SPEC_MAP;
}
```

**No `@PatchMapping` exists** (BR-CUSTOMER-010). The authority values are resolved from
`AuthorityCodes` in the shared-contracts module — never string literals (Gate B, X6).
Full authority detail: `permissions.md`.

---

## 7. Filters and sorting

### 7.1 `jpa/services/predicates/impl/CustomersPredicateServiceImpl.java`

`@Service`, no `@Transactional`. Declares `public static final QPerson personPath = basePath.person;`
— **`public`** here, `private` in the salespeople equivalent. Another asymmetry to
preserve.

`parseMap(Map<String,String>)` switches on the parameter name, in this order:

| Parameter | Predicate | Note |
|---|---|---|
| `id` | `basePath.id.eq(Long.valueOf(value))` | **early `return`** — discards any other filter |
| `idNumber` | `personPath.idNumber.eq(value)` | **early `return`** — same |
| `name` | `personPath.firstName.eq(v).or(personPath.lastName.eq(v))` | accumulates |
| `firstName` | `personPath.firstName.eq(v)` | |
| `lastName` | `personPath.lastName.eq(v)` | |
| `email` | `personPath.email.eq(v)` | |
| `nameLike` | `firstName.likeIgnoreCase("%"+v+"%").or(lastName.likeIgnoreCase("%"+v+"%"))` | |
| `firstNameLike`, `lastNameLike`, `idNumberLike`, `emailLike` | corresponding `likeIgnoreCase("%"+v+"%")` | |
| *anything else* | `default: break` — **silently ignored** | this is what D1 made dangerous on `DELETE`, and what D19 leaves reachable on `update`/`partialUpdate` |

All **11** parameter names are client-observable contract and port verbatim (G2).
A non-numeric `id` is caught, not propagated:

```java
catch (NumberFormatException exc) {
    logger.info("Param '{}' couldn't be parsed as number (value: '{}')", paramName, stringValue);
}
```

— so a bad `id` degrades to "no filter from this parameter" at INFO level, it does not
fail the request. The method returns the accumulated `BooleanBuilder`.

### 7.2 `jpa/sortspecs/CustomersSortSpec.java`

`@NoArgsConstructor(access = PRIVATE)`, two static paths, one immutable map:

```java
public static final Map<String, OrderSpecifier<?>> ORDER_SPEC_MAP = Map.of(
    "idNumber", PERSON_PATH.idNumber.asc(),
    "firstName", PERSON_PATH.firstName.asc(),
    "email", PERSON_PATH.email.asc(),
    "phone1", PERSON_PATH.phone1.asc(),
    "phone2", PERSON_PATH.phone2.asc(),
    "name", PERSON_PATH.lastName.asc(),
    "lastName", PERSON_PATH.lastName.asc()
);
```

Seven keys. **`"name"` sorts by `lastName`**, and there is **no `"id"` key**
(`00` §2 W10). This closed map is also the verified mitigation for register item **D21**
(QueryDSL CVE-2024-49203): `SortSpecParserService` rejects any `sortBy` outside it, so
`orderBy` never receives a raw client string.

---

## 8. Guest provisioning — the Phase 0 D14 artefact

`api/services/GuestCustomerProvisioningService` + `api/services/impl/GuestCustomerProvisioningServiceImpl`.

```java
@Service
public class GuestCustomerProvisioningServiceImpl
    implements GuestCustomerProvisioningService {

    @Transactional
    @Override
    public void provisionCustomer(PersonPojo guestData) throws BadInputException {
        try {
            customersService.create(guestData);
        } catch (EntityExistsException exc) {
            logger.info("Guest with idNumber={} is already registered in the database", guestData.getIdNumber());
        }
    }
}
```

| Fact | Detail |
|---|---|
| Annotations | `@Service` on the class; `@Transactional` is **method-level with no attributes**, joining the CRUD service's transaction |
| Idempotency | catches `jakarta.persistence.EntityExistsException` (raised by `validateInputPojoBeforeCreation` with `"The item already exists"`) and does **not** rethrow (BR-CUSTOMER-018) |
| The caught exception is **not logged** | only the INFO line above, with `exc` unused. Preserve — it is the literal recorded in the D14 ruling |
| `BadInputException` | declared and **propagates**; the filter converts it to `BadCredentialsException("Insufficient or invalid profile data for guest")` → HTTP 401 |
| Rollback | `EntityExistsException` is unchecked but nothing marks the transaction rollback-only, so swallowing it is safe |
| Why it exists | register item **D14**: these writes used to run inside `JwtGuestAuthenticationFilter` **before** authentication, so a failed guest login still created rows (BR-CUSTOMER-017) |

The filter now authenticates **first**:

```java
// Register item D14: authenticate BEFORE provisioning. This filter used to persist a
// real person and customer row first, so a failed guest login still wrote business
// data. The write itself now belongs to a transactional application service.
Authentication result = authenticationManager.authenticate(authentication);
guestCustomerProvisioningService.provisionCustomer(guestCustomerData);
return result;
```

Both sides of this call land in the Identity service, so after Phase 2 it is **intra-service**
coupling. The two deliberately-unfixed D14 remainders — guest-name-as-password, and bean
validation of the submitted profile — are ruled PRESERVE in
`microservices-conversion/decisions/defect-register-rulings.md` and must be carried over
unchanged.

---

## 9. Test inventory (G8 — these port with the code)

| Test class | Path under `src/test/java/org/trebol/` | Baseline count |
|---|---|---|
| `DataCustomersControllerTest` | `api/controllers/` | 6 |
| `GuestCustomerProvisioningServiceImplTest` | `api/services/impl/` | 3 |
| `CustomersConverterServiceImplTest` | `jpa/services/conversion/impl/` | 2 |
| `CustomersCrudServiceImplTest` | `jpa/services/crud/impl/` | 1 |
| `CustomersPatchServiceImplTest` | `jpa/services/patch/impl/` | 1 |
| `CustomersPredicateServiceImplTest` | `jpa/services/predicates/impl/` | 1 |
| `CustomerProvisioningContractImplTest` | `jpa/services/contracts/impl/` | 4 (added in Phase 2, not in the Phase 0 baseline) |
| `CustomersTestHelper` | `testing/` | fixture, 0 tests |

`GuestCustomerProvisioningServiceImplTest`'s method names, verbatim:
`creates_the_guest_customer()`, `tolerates_an_already_registered_guest()`,
`propagates_invalid_guest_data()`.

**18 tests across 7 classes, plus one fixture.** None may be deleted or disabled (G3);
a module that compiles with zero tests fails the G8 check.

Covered by other domains' tests and **not** moved by this domain:
`RegistrationServiceImplTest` (5) and `RegistrationServiceImplTransactionalTest` (1) are
the user domain's; `OrdersConverterServiceImplTest` is the order domain's and stays in the
monolith until Phase 3; `JwtGuestAuthenticationFilterTest` (2) and
`JwtGuestAuthenticationFilterOrderingTest` (4) are the user domain's security tests.
