# Salesperson — Services, Controller and Published Contract

Porting-depth behavioural specification. Companion to `00-domain-index.md` and
`01-salesperson-entity-and-transport.md`. Paths relative to `src/main/java/org/trebol/`.

Points marked **⚠ DIFFERS** diverge from the near-twin `customer` domain.

---

## 1. Service contracts

| Contract | Extends | Declares |
|---|---|---|
| `jpa/services/crud/SalespeopleCrudService` | `CrudService<PersonPojo, Salesperson>` | nothing of its own |
| `jpa/services/conversion/SalespeopleConverterService` | `ConverterService<PersonPojo, Salesperson>` | nothing of its own |
| `jpa/services/patch/SalespeoplePatchService` | `PatchService<PersonPojo, Salesperson>` | nothing of its own |
| `jpa/services/predicates/SalespeoplePredicateService` | `PredicateService<Salesperson>` | `QSalesperson basePath = QSalesperson.salesperson;` |

Empty marker interfaces over the generic kernel. **The behaviour of this domain lives
almost entirely in `CrudGenericService`**, which the Identity service must carry a copy of
(`00` §3.4).

---

## 2. `jpa/repositories/SalespeopleRepository.java`

```java
@Repository
public interface SalespeopleRepository
    extends Repository<Salesperson> {

    @Query(value = "SELECT s FROM Salesperson s JOIN FETCH s.person p WHERE p.idNumber = :idNumber")
    Optional<Salesperson> findByPersonIdNumber(@Param("idNumber") String idNumber);
}
```

One declared method; everything else inherited from `org.trebol.jpa.Repository<E>`. No
`@Modifying`, no native query, no `@EntityGraph`.

**The `Optional` return type is the latent fault described in `01` §1.5** — it queries a
**non-unique** column. Port verbatim; the signature is what callers compile against, and
narrowing it to a `List` would be an API change (G2).

`org.trebol.jpa.Repository` is **not** `@NoRepositoryBean`, which is why no
`@DataJpaTest` slice can bootstrap this project. Identity inherits that limitation.

---

## 3. `jpa/services/crud/impl/SalespeopleCrudServiceImpl.java`

### 3.1 Declaration

```java
@Transactional
@Service
public class SalespeopleCrudServiceImpl
    extends CrudGenericService<PersonPojo, Salesperson>
    implements SalespeopleCrudService {
```

`@Transactional` at **class** level with **no attributes** — propagation `REQUIRED`,
`readOnly = false`, no `rollbackFor`. Constructor-injects `SalespeopleRepository`,
`SalespeopleConverterService`, `SalespeoplePatchService`, passes all three to `super(...)`
and keeps its own repository reference.

### 3.2 The single override

```java
@Override
public Optional<Salesperson> getExisting(PersonPojo input) throws BadInputException {
    String idNumber = input.getIdNumber();
    if (StringUtils.isBlank(idNumber)) {
        throw new BadInputException("Salesperson does not have an ID card");
    } else {
        return salespeopleRepository.findByPersonIdNumber(idNumber);
    }
}
```

Steps:
1. read `input.getIdNumber()` — **no null check on `input`**, so a null pojo is an NPE →
   500. Less exposed than the customer equivalent, because no contract method passes
   external data here: `SalespersonLookupContract` is read-only and takes an **entity**;
2. `org.apache.commons.lang3.StringUtils.isBlank` — null, empty and whitespace-only all
   count;
3. throw `BadInputException("Salesperson does not have an ID card")` (BR-SALESPERSON-006);
4. otherwise delegate to the repository.

**No other method is overridden.**

### 3.3 Inherited behaviour that this domain's rules depend on

From `jpa/services/crud/CrudGenericService.java` (class-level `@Transactional`, no
attributes), with its two client-observable constants
`ITEM_NOT_FOUND = "Requested item(s) not found"` and
`ITEM_ALREADY_EXISTS = "The item already exists"`:

| Method | Behaviour | Rule |
|---|---|---|
| `create(M)` | `validateInputPojoBeforeCreation` → `convertToNewEntity` → `saveAndFlush` → `convertToPojo`; `throws BadInputException, EntityExistsException` | BR-SALESPERSON-003 |
| `validateInputPojoBeforeCreation(M)` | `if (getExisting(input).isPresent()) throw new EntityExistsException(ITEM_ALREADY_EXISTS);` → **409** | BR-SALESPERSON-007 |
| `update(M)` | `throw new UnsupportedOperationException("This method signature has been deprecated");` | — |
| `update(M, Long id)` | `convertToNewEntity` → `setId(id)` → `saveAndFlush` → `convertToPojo`. **Full overwrite**; nested `Person` id stays null → new `people` row | **BR-SALESPERSON-013** |
| `update(M, Predicate)` | `count > 1` → `throw new RuntimeException("Cannot update more than one item at a time")`; no match → `Optional.empty()`; else `update(input, firstMatch.getId())` | **BR-SALESPERSON-022** |
| `partialUpdate(Map, Long)` / `(Map, Predicate)` | `findById`/`findOne` → `flushPartialChanges`; a `BadInputException` is **wrapped** as `RuntimeException(e)` → 500 | BR-SALESPERSON-011 |
| `flushPartialChanges` | patch, then `if (existing.equals(updated)) return existing;` else `saveAndFlush` | — |
| `readOne(Predicate)` | empty → `EntityNotFoundException(ITEM_NOT_FOUND)` | — |
| `readMany(int, int, Sort, Predicate)` | `PageRequest.of(...)`, count, `findAll`, convert, `new DataPagePojo<>(...)` | — |
| `delete(Predicate)` | `count == 0` → `EntityNotFoundException(ITEM_NOT_FOUND)`; else `repository.deleteAll(repository.findAll(filters))` | BR-SALESPERSON-018, BR-SALESPERSON-019 |

The Phase 0 **D1** fix sits above `delete`, in `DataCrudGenericController`: an
all-unrecognised-parameter `DELETE` now raises `BadInputException` → 400 instead of
deleting every row. **D19** records that the same match-all shape remains reachable
through `update`/`partialUpdate`, ruled PRESERVE in scope.

---

## 4. `jpa/services/conversion/impl/SalespeopleConverterServiceImpl.java`

`@Service` only — **no `@Transactional`.** Injects `PeopleConverterService` and delegates
wholly:

```java
@Override
public PersonPojo convertToPojo(Salesperson source) {
    return peopleConverterService.convertToPojo(source.getPerson());
}

@Override
public Salesperson convertToNewEntity(PersonPojo source) {
    Person targetPerson = peopleConverterService.convertToNewEntity(source);
    return Salesperson.builder()
        .person(targetPerson)
        .build();
}

@Override
public Salesperson applyChangesToExistingEntity(PersonPojo source, Salesperson target) {
    throw new UnsupportedOperationException("This method is deprecated");
}
```

1. `convertToPojo` dereferences `source.getPerson()` with **no null guard** — safe only
   because `optional = false` plus EAGER fetch guarantee a person
   (BR-SALESPERSON-001, BR-SALESPERSON-023).
2. `convertToNewEntity` builds with **no id** (`01` §2.1).
3. `applyChangesToExistingEntity` throws with the literal `"This method is deprecated"`.
   **Never called** — the kernel reaches for the patch service instead — but the literal
   ports verbatim (G2).

This class is **byte-for-byte analogous** to `CustomersConverterServiceImpl`, same literal
included. That is one of the few places the two domains are genuinely identical.

---

## 5. `jpa/services/patch/impl/SalespeoplePatchServiceImpl.java`

`@Service` only, no `@Transactional`. Injects `PeoplePatchService`.

```java
@Override
public Salesperson patchExistingEntity(Map<String, Object> changes, Salesperson existing) throws BadInputException {
    Salesperson target = new Salesperson(existing);

    Map<String, Object> personChanges = Utils.copyMapWithUnprefixedEntries(changes, "person.");
    if (!personChanges.isEmpty()) {
        Person existingPerson = existing.getPerson();
        Person person = peoplePatchService.patchExistingEntity(personChanges, existingPerson);
        target.setPerson(person);
    }

    return target;
}

@Override
public Salesperson patchExistingEntity(PersonPojo changes, Salesperson existing) throws BadInputException {
    throw new UnsupportedOperationException("This method has been deprecated");
}
```

- **⚠ DIFFERS — the prefix is the hardcoded literal `"person."`**, not
  `Constants.PERSON_DATA_MAP_KEYS_PREFIX`, which the customer equivalent uses
  (BR-SALESPERSON-012). Identical behaviour, divergent source. Port the divergence; do not
  unify it, and do not add the `Constants` import.
- `Utils.copyMapWithUnprefixedEntries` **replaces every occurrence** of the prefix, not
  just the leading one.
- An empty `personChanges` returns an unmodified copy, which `flushPartialChanges` finds
  `equals` and **does not save**.
- Blank values never clear a stored name, id number or e-mail — that guard is in
  `PeoplePatchServiceImpl`.
- **Unreachable over HTTP** (BR-SALESPERSON-010, BR-SALESPERSON-011): the controller
  declares no `@PatchMapping`. Port regardless.
- The second overload's literal `"This method has been deprecated"` is **a different
  string** from the converter's `"This method is deprecated"`. Both exist; do not
  normalise (G2).

---

## 6. Controller — `api/controllers/DataSalespeopleController.java`

Class level, verbatim:

```java
@RestController
@RequestMapping("/data/salespeople")
@Tag(name = "People management")
@PreAuthorize("isAuthenticated()")
public class DataSalespeopleController
    extends DataCrudGenericController<PersonPojo, Salesperson> {
```

`@Tag` is **`"People management"`**, shared with `DataCustomersController` and
`DataPeopleController`. **⚠ DIFFERS —** the constructor's sort-service parameter is named
`sortService` rather than `sortSpecParserService` (`00` §2 W8).

| Verb | Path | `@PreAuthorize` | Signature | Status | Throws |
|---|---|---|---|---|---|
| GET | `/data/salespeople` | `hasAuthority('" + SALESPEOPLE_READ + "')` | `DataPagePojo<PersonPojo> readMany(@RequestParam Map<String,String> allRequestParams)` | 200 | — |
| POST | `/data/salespeople` | `hasAuthority('" + SALESPEOPLE_CREATE + "')` | `void create(@Valid @RequestBody PersonPojo input)` | **201** | `BadInputException, EntityExistsException` |
| PUT | `/data/salespeople` | `hasAuthority('" + SALESPEOPLE_UPDATE + "')` | `void update(@Valid @RequestBody PersonPojo input, @RequestParam Map<String,String> requestParams)` | **204** | `BadInputException, EntityNotFoundException` |
| DELETE | `/data/salespeople` | `hasAuthority('" + SALESPEOPLE_DELETE + "')` | `void delete(@RequestParam Map<String,String> requestParams)` | **204** | `BadInputException, EntityNotFoundException` |

`@Operation(summary = ...)` literals, verbatim: `"List salespeople."`,
`"Register new salespeople."`, `"Replace salespeople data."`, `"Deregister salespeople."`.

Two points to port exactly:

1. **`create` calls `crudService.create(input)` directly, not `super.create(...)`** —
   same as the customer controller.
2. **⚠ DIFFERS — `delete`'s `Map` parameter HAS `@RequestParam`**, while the customer
   controller's does not (`00` §2 W5 of the customer spec). Port both as they are.

Plus the sort-spec hook:

```java
@Override
protected Map<String, OrderSpecifier<?>> getOrderSpecMap() {
    return SalespeopleSortSpec.ORDER_SPEC_MAP;
}
```

**No `@PatchMapping` exists** (BR-SALESPERSON-010). Authority values resolve from
`AuthorityCodes` in the shared-contracts module — never literals (Gate B, X6). Full
detail: `permissions.md`.

---

## 7. Filters and sorting

### 7.1 `jpa/services/predicates/impl/SalespeoplePredicateServiceImpl.java`

`@Service`, no `@Transactional`. **⚠ DIFFERS —** declares
`private static final QPerson personPath = basePath.person;`, where the customer
equivalent makes the same field `public` (`00` §2 W7).

`parseMap(Map<String,String>)` switches on the parameter name, in this order:

| Parameter | Predicate | Note |
|---|---|---|
| `id` | `basePath.id.eq(Long.valueOf(value))` | **early `return`** — discards other filters |
| `idNumber` | `personPath.idNumber.eq(value)` | **early `return`** |
| `name` | `personPath.firstName.eq(v).or(personPath.lastName.eq(v))` | accumulates |
| `firstName` | `personPath.firstName.eq(v)` | |
| `lastName` | `personPath.lastName.eq(v)` | |
| `email` | `personPath.email.eq(v)` | |
| `nameLike` | `firstName.likeIgnoreCase("%"+v+"%").or(lastName.likeIgnoreCase("%"+v+"%"))` | |
| `firstNameLike`, `lastNameLike`, `idNumberLike`, `emailLike` | corresponding `likeIgnoreCase("%"+v+"%")` | |
| *anything else* | `default: break` — **silently ignored** | the D1/D19 match-all shape |

All **11** parameter names are client-observable contract (G2). Apart from `personPath`'s
modifier, the body is **identical** to the customer predicate service. A non-numeric `id`
is caught, not propagated:

```java
catch (NumberFormatException exc) {
    logger.info("Param '{}' couldn't be parsed as number (value: '{}')", paramName, stringValue);
}
```

### 7.2 `jpa/sortspecs/SalespeopleSortSpec.java`

`@NoArgsConstructor(access = PRIVATE)`, `BASE_PATH = QSalesperson.salesperson`,
`PERSON_PATH = BASE_PATH.person`, one immutable map:

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

Seven keys, identical to the customer spec's. **`"name"` sorts by `lastName`**; there is
**no `"id"` key** (`00` §2 W12). Note BR-SALESPERSON-017 separately: `OrdersSortSpec`
offers no salesperson key at all, so orders cannot be sorted by salesperson — that gap
stays.

This closed map is the verified mitigation for register item **D21** (QueryDSL
CVE-2024-49203): `SortSpecParserService` rejects any `sortBy` outside it, so `orderBy`
never receives a raw client string.

---

## 8. The published contract — `SalespersonLookupContract`

Phase 2's half of the HS-02 fix. The order read path previously called
`SalespeopleConverterService` directly.

```java
public interface SalespersonLookupContract {
    PersonPojo describeSalesperson(Salesperson salesperson);
}
```

```java
@Service
@Transactional(readOnly = true)
public class SalespersonLookupContractImpl
    implements SalespersonLookupContract {

    @Override
    public PersonPojo describeSalesperson(Salesperson salesperson) {
        return salespeopleConverterService.convertToPojo(salesperson);
    }
}
```

| Property | Value |
|---|---|
| Direction | **read-only** — the order domain never creates a salesperson through this seam |
| Transaction | `@Transactional(readOnly = true)` at class level |
| Implementation | pure delegation; no logic rewritten, so behaviour is identical to the baseline (G1) |
| Current parameter shape | takes a `Salesperson` **entity** |

**The entity-typed parameter is deliberate for Phase 2 and must be revisited in Phase 3.**
It works today because both sides are in the same process and the order entity holds a
real `@ManyToOne` to `Salesperson`. Once Orders is a separate service that association is
cut, and the contract will have to take an identifier instead. Recorded here rather than
pre-emptively redesigned, because changing the signature now would mean changing the order
domain's call site twice.

Contrast `CustomerProvisioningContract`, which takes a `PersonPojo` and is **read *or*
write** — that asymmetry reflects the real asymmetry in the two domains' roles at
checkout: every order must have a customer (BR-CUSTOMER-016), while an order may have no
salesperson and no route ever sets one (BR-SALESPERSON-014, BR-SALESPERSON-015).

---

## 9. Test inventory (G8 — these port with the code)

| Test class | Path under `src/test/java/org/trebol/` | Baseline count |
|---|---|---|
| `DataSalespeopleControllerTest` | `api/controllers/` | 6 |
| `SalespeopleConverterServiceImplTest` | `jpa/services/conversion/impl/` | 2 |
| `SalespeopleCrudServiceImplTest` | `jpa/services/crud/impl/` | 1 |
| `SalespeoplePatchServiceImplTest` | `jpa/services/patch/impl/` | 1 |
| `SalespeoplePredicateServiceImplTest` | `jpa/services/predicates/impl/` | 1 |
| `SalespersonLookupContractImplTest` | `jpa/services/contracts/impl/` | 1 (added in Phase 2, not in the Phase 0 baseline) |
| `SalespeopleTestHelper` | `testing/` | fixture, 0 tests |

**12 tests across 6 classes, plus one fixture.** None may be deleted or disabled (G3); a
module that compiles with zero tests fails the G8 check.

`OrdersConverterServiceImplTest` also exercises `describeSalesperson` through the contract
mock, but it is the **order domain's** test and stays in the monolith until Phase 3.
