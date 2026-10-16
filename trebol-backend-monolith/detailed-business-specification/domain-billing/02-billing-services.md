# Billing — Services, Contract and Controller

Porting-depth behavioural specification. Companion to `00-domain-index.md` and
`01-billing-entity-and-transport.md`. Paths relative to `src/main/java/org/trebol/`.

---

## 1. Service contracts

| Contract | Extends | Declares |
|---|---|---|
| `jpa/services/crud/BillingTypesCrudService` | `CrudService<BillingTypePojo, BillingType>` | nothing of its own |
| `jpa/services/crud/BillingCompaniesCrudService` | `CrudService<BillingCompanyPojo, BillingCompany>` | nothing of its own |
| `jpa/services/conversion/BillingTypesConverterService` | `ConverterService<BillingTypePojo, BillingType>` | nothing |
| `jpa/services/conversion/BillingCompaniesConverterService` | `ConverterService<BillingCompanyPojo, BillingCompany>` | nothing |
| `jpa/services/conversion/AddressesConverterService` | `ConverterService<AddressPojo, Address>` | nothing |
| `jpa/services/patch/BillingTypesPatchService` | `PatchService<BillingTypePojo, BillingType>` | nothing |
| `jpa/services/patch/BillingCompaniesPatchService` | `PatchService<BillingCompanyPojo, BillingCompany>` | nothing |
| `jpa/services/predicates/BillingTypesPredicateService` | `PredicateService<BillingType>` | `QBillingType basePath = QBillingType.billingType;` |
| `jpa/services/predicates/BillingCompaniesPredicateService` | `PredicateService<BillingCompany>` | `QBillingCompany basePath = QBillingCompany.billingCompany;` |

**There is no `AddressesCrudService`, `AddressesPatchService` or `AddressesPredicateService`.**
`Address` has a converter and a repository and nothing else — which is the whole of
BR-BILLING-014's "no API surface" for addresses.

---

## 2. Repositories

All extend `org.trebol.jpa.Repository<T>` and are annotated with the **fully-qualified**
`@org.springframework.stereotype.Repository`, to avoid clashing with the project's own
`Repository` interface. Port that qualification verbatim.

### 2.1 `BillingTypesRepository`
```java
Optional<BillingType> findByName(String name);
```
Derived query, no `@Query`. The only declared method.

### 2.2 `BillingCompaniesRepository`
```java
Optional<BillingCompany> findByIdNumber(String idNumber);
```
Keyed on the **id number**, not the name — BR-BILLING-010. The name's separate unique
constraint is never consulted by any query.

### 2.3 `AddressesRepository` — the one `@Query` in the domain, and the W1 defect

```java
@Query(value = "SELECT a FROM Address a WHERE a.city = :city "
    + "AND a.municipality = :municipality "
    + "AND a.firstLine = :firstLine "
    + "AND a.secondLine = :secondLine "
    + "AND a.postalCode = :postalCode "
    + "AND a.notes = :notes")
Optional<Address> findByFields(
    @Param("city") String city,
    @Param("municipality") String municipality,
    @Param("firstLine") String firstLine,
    @Param("secondLine") String secondLine,
    @Param("postalCode") String postalCode,
    @Param("notes") String notes);
```

**Three of those six columns are nullable, and `= NULL` is never true.** So an address with
no second line, no postal code or no notes — which is most real addresses — is **invisible to
this query**. The caller (`OrdersConverterServiceImpl.findAddress`) concludes no match exists
and creates a new row; `UK_ADDR` accepts it, because a NULL makes the unique tuple distinct.

The result is silent row growth in `addresses`, one per checkout, for any address with a
null optional field. Boundary warning **W1**, ported unchanged — fixing it would change which
addresses are reused and needs its own ruling (G1).

**Note the parameter order** — `city, municipality, firstLine, secondLine, postalCode, notes`
— which is *not* `AddressPojo`'s field order. A transposed call site would compile.

### 2.4 `PaymentTypesRepository`
```java
Optional<PaymentType> findByName(String name);
```
Consumed only through `BillingContract.findPaymentTypeByName`.

---

## 3. CRUD services

### 3.1 `BillingTypesCrudServiceImpl`

`@Transactional` (class level, no attributes) + `@Service`. Extends
`CrudGenericService<BillingTypePojo, BillingType>`. Constructor-injects the repository, the
converter and the patch service, passes all three to `super(...)`, and keeps its own
`typesRepository`.

**One override:**
```java
@Override
public Optional<BillingType> getExisting(BillingTypePojo input) throws BadInputException {
    String name = input.getName();
    if (StringUtils.isBlank(name)) {
        throw new BadInputException("Billing type has no name");
    } else {
        return typesRepository.findByName(name);
    }
}
```
Literal `"Billing type has no name"` — **BR-BILLING-005**. No null guard on `input` itself.

### 3.2 `BillingCompaniesCrudServiceImpl`

Same shape. **One override:**
```java
String idNumber = input.getIdNumber();
if (StringUtils.isBlank(idNumber)) {
    throw new BadInputException("Billing company has no id number");
} else {
    return companiesRepository.findByIdNumber(idNumber);
}
```
Literal `"Billing company has no id number"` — **BR-BILLING-011**. This is the **only**
validation a billing company ever receives, because its pojo has no annotations at all
(BR-BILLING-013).

### 3.3 Inherited behaviour

From `CrudGenericService` (class-level `@Transactional`), with
`ITEM_NOT_FOUND = "Requested item(s) not found"` and
`ITEM_ALREADY_EXISTS = "The item already exists"`. Of its methods, only `create`,
`readMany`, `readOne` and the patch path are reachable in this domain — there is no route
that updates or deletes a billing type (`02` §5), so `update`/`delete` exist but are
unreachable over HTTP.

`validateInputPojoBeforeCreation` raising `EntityExistsException(ITEM_ALREADY_EXISTS)` → 409
is what makes a duplicate billing type a conflict.

---

## 4. The published contract — `BillingContract`

Gate A of the conversion. Before it, `OrdersConverterServiceImpl` reached
`BillingTypesRepository`, `BillingCompaniesCrudService` and
`BillingCompaniesConverterService` directly, and `OrdersPatchServiceImpl` reached
`PaymentTypesRepository`.

```java
public interface BillingContract {
    Optional<BillingType> findBillingTypeByName(String name);
    Optional<PaymentType> findPaymentTypeByName(String name);
    BillingCompany resolveOrCreateBillingCompany(BillingCompanyPojo company) throws BadInputException;
}
```

`BillingContractImpl` is `@Service` + class-level `@Transactional` (no attributes), injecting
`BillingTypesRepository`, `PaymentTypesRepository`, `BillingCompaniesCrudService` and
`BillingCompaniesConverterService`.

| Method | Transaction | Behaviour |
|---|---|---|
| `findBillingTypeByName` | `@Transactional(readOnly = true)` | delegates to the repository |
| `findPaymentTypeByName` | `@Transactional(readOnly = true)` | delegates to the repository |
| `resolveOrCreateBillingCompany` | inherits class-level **read-write** | `getExisting(company)`; if empty → `convertToNewEntity(company)` — **an unsaved entity**; else the existing one |

### 4.1 Why `resolveOrCreateBillingCompany` is the one to watch

**It is a read *or a write*, and its javadoc says so.** The newly converted company is
**not persisted here** — it is returned unsaved and cascaded when the order is saved, which
is how one checkout call creates a company row as a side effect.

In-process that is merely surprising. **Across a service boundary it would be a remote
write inside checkout**, and ADR-001 records it as an open consequence of the approved
grouping. Phase 3 does *not* have to solve it, because billing travels into the **same**
Orders service as the order domain — the call stays in-process. It is recorded here because
it is the first thing to re-examine if billing is ever split out on its own.

---

## 5. Controller — `api/controllers/DataBillingTypesController.java`

```java
@RestController
@RequestMapping("/data/billing_types")
@Tag(name = "Params management")
public class DataBillingTypesController
    extends DataGenericController<BillingTypePojo, BillingType> {
```

Note it extends **`DataGenericController`** — the **read-only** base — not
`DataCrudGenericController`. That is the structural reason this domain has no write routes.
`@Tag` is `"Params management"`, shared with `DataUserRolesController`.

| Verb | Path | `@PreAuthorize` | Signature | Status |
|---|---|---|---|---|
| GET | `/data/billing_types` | **none** | `DataPagePojo<BillingTypePojo> readMany(@RequestParam Map<String,String> allRequestParams)` | 200 |

`@Operation(summary = "List billing types.")`. No `@ResponseStatus`, no declared exceptions.

Plus the sort hook:
```java
@Override
protected Map<String, OrderSpecifier<?>> getOrderSpecMap() {
    return BillingTypesSortSpec.ORDER_SPEC_MAP;
}
```

**This is the domain's entire HTTP surface — one anonymous read.** There is no controller for
billing companies and none for addresses (BR-BILLING-014). Every write in this domain happens
as a side effect of checkout, through `BillingContract` and `AddressesConverterService`.

**The route is deliberately unauthenticated** (BR-BILLING-016) and **no billing authority
exists in the catalogue at all** (BR-BILLING-019) — see `permissions.md`.

---

## 6. Converters

All three are `@Service` + `@NoArgsConstructor` with **no `@Transactional`**.

**`BillingTypesConverterServiceImpl`** — `convertToPojo` →
`BillingTypePojo.builder().name(source.getName()).build()`; `convertToNewEntity` →
`BillingType.builder().name(source.getName()).build()`; `applyChangesToExistingEntity` →
`throw new UnsupportedOperationException("This method is deprecated")`.

**`BillingCompaniesConverterServiceImpl`** — maps `idNumber` and `name` both ways; same
deprecation literal.

**`AddressesConverterServiceImpl`** — the only one with conditional logic:

- `convertToPojo` builds with `city`, `municipality`, `firstLine` only, then conditionally
  `setSecondLine` / `setPostalCode` / `setNotes`, **each guarded by
  `if (!StringUtils.isBlank(...))`**. So a blank optional field is left null on the pojo,
  which `@JsonInclude(NON_EMPTY)` then omits from the response.
- `convertToNewEntity` builds all six fields unconditionally:
  ```java
  Address.builder().firstLine(..).secondLine(..).city(..).municipality(..)
      .postalCode(..).notes((source.getNotes())).build()
  ```
  **Note the redundant double parentheses around `source.getNotes()` — verbatim in the
  original.** Harmless; port it rather than tidying, so a diff of the ported file against the
  original is empty.
- `applyChangesToExistingEntity` → same deprecation literal.

**The asymmetry is the point:** reads blank-guard, writes do not. An empty-string second line
submitted at checkout is stored as an empty string, and then — per W1 — is *findable*, while
a null one is not.

---

## 7. Patch services

Both are `@Service` + `@NoArgsConstructor`, **no `@Transactional`**.

**`BillingTypesPatchServiceImpl`**
```java
BillingType target = new BillingType(existing);
if (changes.containsKey("name")) {
    String name = (String) changes.get("name");
    if (!StringUtils.isBlank(name)) {
        target.setName(name);
    }
}
return target;
```
The pojo overload throws `UnsupportedOperationException("This method has been deprecated")`.

**`BillingCompaniesPatchServiceImpl`** — same shape, handling `"idNumber"` then `"name"`,
each blank-guarded. Same pojo-overload literal.

Three properties to carry across:

1. **A blank value never clears stored data** (BR-BILLING-015) — the blank guard means a
   patch of `""` is silently ignored rather than writing an empty string.
2. **The cast is unguarded** — `(String) changes.get("name")` on a non-string value is a
   `ClassCastException` → 500.
3. **The deprecation literal differs from the converters'** — `"This method has been
   deprecated"` here, `"This method is deprecated"` there. Both exist; do not normalise (G2).

**Both patch services are unreachable over HTTP**, because the controller maps no PATCH.

---

## 8. Search and sorting

### 8.1 Predicate services

Both are `@Service`, no `@Transactional`, with a private slf4j `logger`. Each iterates the
parameter map inside a `try`/`catch (NumberFormatException)` and returns the accumulated
`BooleanBuilder`.

**`BillingTypesPredicateServiceImpl`** — 3 recognised keys:

| Parameter | Predicate |
|---|---|
| `id` | `basePath.id.eq(Long.valueOf(v))` — **early `return`** |
| `name` | `and(basePath.name.eq(v))` |
| `nameLike` | `and(basePath.name.likeIgnoreCase("%"+v+"%"))` |
| *anything else* | `default: break` — silently ignored |

**`BillingCompaniesPredicateServiceImpl`** — 5 recognised keys: `id` (**early return**),
`idNumber` (**early return**), `name`, `idNumberLike`, `nameLike`.

Both share the catch literal:
```java
logger.info("Param '{}' couldn't be parsed as number (value: '{}')", paramName, stringValue);
```

All 8 parameter names are client-observable contract (G2) — even the billing-company ones,
which no route can currently reach.

### 8.2 `jpa/sortspecs/BillingTypesSortSpec.java`

```java
@NoArgsConstructor(access = PRIVATE)
public class BillingTypesSortSpec {
    private static final QBillingType BASE_PATH = QBillingType.billingType;
    public static final Map<String, OrderSpecifier<?>> ORDER_SPEC_MAP = Map.of(
        "name", BASE_PATH.name.asc());
}
```

**One key, `name`. No `id` key.** There is no `BillingCompaniesSortSpec` and no
`AddressesSortSpec`.

This closed map is the verified mitigation for register item **D21** (QueryDSL
CVE-2024-49203): `SortSpecParserService` rejects any `sortBy` outside it, so `orderBy` never
receives a raw client string.

---

## 9. Test inventory (G8 — these port with the code)

| Test class | Path under `src/test/java/org/trebol/` |
|---|---|
| `DataBillingTypesControllerTest` | `api/controllers/` |
| `BillingTypesCrudServiceImplTest` | `jpa/services/crud/impl/` |
| `BillingCompaniesCrudServiceImplTest` | `jpa/services/crud/impl/` |
| `BillingTypesConverterServiceImplTest` | `jpa/services/conversion/impl/` |
| `BillingCompaniesConverterServiceImplTest` | `jpa/services/conversion/impl/` |
| `AddressesConverterServiceImplTest` | `jpa/services/conversion/impl/` |
| `BillingTypesPatchServiceImplTest` | `jpa/services/patch/impl/` |
| `BillingCompaniesPatchServiceImplTest` | `jpa/services/patch/impl/` |
| `BillingTypesPredicateServiceImplTest` | `jpa/services/predicates/impl/` |
| `BillingCompaniesPredicateServiceImplTest` | `jpa/services/predicates/impl/` |
| **`BillingContractImplTest`** | `jpa/services/contracts/impl/` — **7 tests**, the Gate A pin |
| `BillingTestHelper` | `testing/` — fixture, 0 tests |

`BillingContractImplTest` is the one to protect: it pins the resolve-or-create semantics that
`02` §4.1 flags as the domain's distributed-write risk, including that a created company is
**not** persisted by the contract itself.

None may be deleted or disabled (G3).
