# Shipper — Services, Controller and Published Contract

Porting-depth behavioural specification. Companion to `00-domain-index.md` and
`01-shipper-entity-and-transport.md`. Paths relative to `src/main/java/org/trebol/`.

---

## 1. Service contracts

| Contract | Extends | Declares |
|---|---|---|
| `jpa/services/crud/ShippersCrudService` | `CrudService<ShipperPojo, Shipper>` | nothing of its own |
| `jpa/services/conversion/ShippersConverterService` | `ConverterService<ShipperPojo, Shipper>` | nothing of its own |
| `jpa/services/patch/ShippersPatchService` | `PatchService<ShipperPojo, Shipper>` | nothing of its own |
| `jpa/services/predicates/ShippersPredicateService` | `PredicateService<Shipper>` | `QShipper basePath = QShipper.shipper;` |

Empty marker interfaces over the generic kernel, except the predicate service's static
`basePath`. **The behaviour of this domain lives almost entirely in `CrudGenericService`**,
which the Orders service must carry a copy of (`00` §3.4).

---

## 2. `jpa/repositories/ShippersRepository.java`

```java
@org.springframework.stereotype.Repository
public interface ShippersRepository
    extends Repository<Shipper> {

    Optional<Shipper> findByName(String name);
}
```

One derived query, **no `@Query`**. Note the **fully-qualified** `@org.springframework.stereotype.Repository`
annotation — used throughout this codebase to avoid clashing with the project's own
`org.trebol.jpa.Repository` interface. Port the qualification verbatim.

`org.trebol.jpa.Repository` is **not** annotated `@NoRepositoryBean`, which is why no
`@DataJpaTest` slice can bootstrap this project. The Orders service inherits that limitation,
so any integration-style test must be a `@SpringBootTest`.

---

## 3. `jpa/services/crud/impl/ShippersCrudServiceImpl.java`

`@Transactional` (class level, no attributes) + `@Service`. Extends
`CrudGenericService<ShipperPojo, Shipper>`. Constructor-injects the repository, the converter
and the patch service, passes all three to `super(...)`, and keeps its own
`shippersRepository`.

**One override — and it carries boundary warning W1:**

```java
@Override
public Optional<Shipper> getExisting(ShipperPojo input) throws BadInputException {
    String name = input.getName();
    if (StringUtils.isBlank(name)) {
        throw new BadInputException("Billing type has no name");
    } else {
        return shippersRepository.findByName(name);
    }
}
```

> **The message says `"Billing type has no name"`.** It is a copy-paste defect from
> `BillingTypesCrudServiceImpl`, and it names **the wrong domain entirely**: a client that
> submits a blank carrier name is told something about billing types.

**It ports verbatim.** The string is client-observable (G2), and correcting it is a contract
change that needs its own ruling — exactly the kind of "obvious tidy-up" that invariant G1
exists to prevent. It is recorded as W1 in `00` §2 and as E1 in `errors.md` so that it is a
known, deliberate carry-over rather than an uncaught mistake.

This is also the enforcement point for **BR-SHIPPER-003**: a blank name is rejected before
any query runs. There is **no null guard on `input` itself**, so a null pojo is an NPE → 500.

**No other method is overridden.** `create`, `update`, `partialUpdate`, `delete`, `readOne`
and `readMany` all come from `CrudGenericService` unchanged, including:

| Method | Behaviour | Rule |
|---|---|---|
| `validateInputPojoBeforeCreation` | `getExisting(input).isPresent()` → `EntityExistsException("The item already exists")` → **409** | BR-SHIPPER-006 |
| `update(M, Predicate)` | `count > 1` → `RuntimeException("Cannot update more than one item at a time")` | **BR-SHIPPER-011** |
| `partialUpdate(Map, Predicate)` | same count guard | **BR-SHIPPER-011** |
| `delete(Predicate)` | `count == 0` → `EntityNotFoundException("Requested item(s) not found")`; else `deleteAll(findAll(filters))` | BR-SHIPPER-013 |
| `flushPartialChanges` | patch, then `if (existing.equals(updated)) return existing;` else `saveAndFlush` | — |

---

## 4. `jpa/services/conversion/impl/ShippersConverterServiceImpl.java`

`@Service` + `@NoArgsConstructor`, **no `@Transactional`**.

```java
convertToPojo        → ShipperPojo.builder().name(source.getName()).build()
convertToNewEntity   → Shipper.builder().name(source.getName()).build()
applyChangesToExistingEntity → throw new UnsupportedOperationException("This method is deprecated")
```

With one business attribute there is nothing else to map. `applyChangesToExistingEntity` is
never called — the kernel reaches for the patch service instead — but the literal is contract
if it ever surfaces, so it ports verbatim.

**Note the literal is `"This method is deprecated"`**, while the patch service's equivalent is
`"This method has been deprecated"` (§6). Both exist; do not normalise (W9, G2).

---

## 5. Controller — `api/controllers/DataShippersController.java`

```java
@RestController
@RequestMapping("/data/shippers")
@Tag(name = "Shippers management")
public class DataShippersController
    extends DataCrudGenericController<ShipperPojo, Shipper> {
```

**No class-level `@PreAuthorize`.** That is deliberate and it is what makes the read
anonymous — there is no second layer to fall back on, because `SecurityConfig` declares no
request-matcher rules at all (the Gate D finding, `ADR-002`).

Static imports: `CREATED`, `NO_CONTENT`, and `AuthorityCodes.SHIPPERS_CREATE` /
`SHIPPERS_DELETE` / `SHIPPERS_UPDATE`.

| Verb | `@PreAuthorize` (expanded) | Signature | Status |
|---|---|---|---|
| GET | **none — unauthenticated** | `DataPagePojo<ShipperPojo> readMany(@RequestParam Map<String,String> allRequestParams)` | 200 |
| POST | `hasAuthority('shippers:create')` | `void create(@Valid @RequestBody ShipperPojo input)` → **`crudService.create(input)`**, not `super.create(...)` | **201** |
| PUT | `hasAuthority('shippers:update')` | `void update(@Valid @RequestBody ShipperPojo input, @RequestParam Map<String,String> requestParams)` → `super.update(...)` | **204** |
| PATCH | `hasAuthority('shippers:update')` | `void partialUpdate(@RequestBody Map<String,Object> input, @RequestParam Map<String,String> requestParams)` — **no `@Valid`** | **204** |
| DELETE | `hasAuthority('shippers:delete')` | `void delete(@RequestParam Map<String,String> requestParams)` | **204** |

All four writes declare `throws BadInputException` plus either `EntityExistsException`
(POST) or `EntityNotFoundException` (PUT/PATCH/DELETE).

Plus the sort hook:
```java
@Override
protected Map<String, OrderSpecifier<?>> getOrderSpecMap() {
    return ShippersSortSpec.ORDER_SPEC_MAP;
}
```

Three things to port exactly:

1. **`GET` is anonymous** (BR-SHIPPER-008), and the seeded `shippers:read` authority is
   **never referenced** (BR-SHIPPER-009). See `permissions.md`.
2. **`PATCH` has no `@Valid`** (W5), so its body is entirely unvalidated while `POST` and
   `PUT` both validate. This is the only domain in the conversion with a `PATCH` route whose
   validation differs from its `PUT`.
3. **`POST` calls `crudService.create(input)` directly**, not `super.create(...)` — the same
   deviation as the customer, salesperson, user and user-role controllers.

**Note:** `PATCH` exists here and does **not** exist on `/data/customers`,
`/data/salespeople`, `/data/users` or `/data/user_roles`. Shippers and products are the
domains that expose partial update.

---

## 6. `jpa/services/patch/impl/ShippersPatchServiceImpl.java`

`@Service` + `@NoArgsConstructor`, **no `@Transactional`**.

```java
Shipper target = new Shipper(existing);
if (changes.containsKey("name")) {
    String name = (String) changes.get("name");
    if (!StringUtils.isBlank(name)) {
        target.setName(name);
    }
}
return target;
```

The pojo overload throws `UnsupportedOperationException("This method has been deprecated")`.

| Property | Detail |
|---|---|
| **A blank value never clears the stored name** | **BR-SHIPPER-007** — the blank guard means patching `""` is silently ignored rather than writing an empty string |
| The cast is **unguarded** | `(String) changes.get("name")` on a non-string value is a `ClassCastException` → 500. The `PATCH` route has no `@Valid`, so nothing upstream prevents it |
| The copy retains `id` | so `saveAndFlush` is an update (`01` §1.2) |
| A no-op patch **skips the write** | `flushPartialChanges` finds the copy `equals` to the original and returns it unsaved |

**This patch service is reachable**, unlike the customer, salesperson and billing ones —
`PATCH /data/shippers` is mapped.

---

## 7. Search and sorting

### 7.1 `jpa/services/predicates/impl/ShippersPredicateServiceImpl.java`

`@Service`, no `@Transactional`, with a private slf4j `logger`. `parseMap` iterates the
parameter map inside a `try`/`catch (NumberFormatException)` and returns the accumulated
`BooleanBuilder`.

| Parameter | Predicate |
|---|---|
| `id` | `basePath.id.eq(Long.valueOf(v))` — **early `return`**, discarding any other filter |
| `name` | `basePath.name.eq(v)` — **early `return`** |
| `nameLike` | `and(basePath.name.likeIgnoreCase("%"+v+"%"))` |
| *anything else* | `default: break` — **silently ignored** |

**Three recognised parameter names**, all client-observable contract (G2).

The catch literal:
```java
logger.info("Param '{}' couldn't be parsed as number (value: '{}')", paramName, stringValue);
```
So a non-numeric `id` degrades to "no filter from this parameter" at INFO level rather than
failing the request.

**The `default: break` is what the Phase-0 D1 fix guards against on `DELETE`**: a request
whose parameters are all unrecognised produces an empty `BooleanBuilder`, which
`DataCrudGenericController.delete` now rejects with
`BadInputException("No recognized filtering parameters were provided")` → 400, instead of
matching every row. Register item **D19** records that the same match-all shape remains
reachable through `update` and `partialUpdate`, ruled PRESERVE in scope.

### 7.2 `jpa/sortspecs/ShippersSortSpec.java`

```java
@NoArgsConstructor(access = PRIVATE)
public class ShippersSortSpec {
    private static final QShipper BASE_PATH = QShipper.shipper;
    public static final Map<String, OrderSpecifier<?>> ORDER_SPEC_MAP = Map.of(
        "name", BASE_PATH.name.asc());
}
```

**One key, `name`. No `id` key** — identical in shape to `BillingTypesSortSpec`.

This closed map is the verified mitigation for register item **D21** (QueryDSL
CVE-2024-49203): `SortSpecParserService` rejects any `sortBy` outside it, so `orderBy` never
receives a raw client string.

---

## 8. The published contract — `ShipperLookupContract`

Gate A of the conversion. Before it, `OrdersConverterServiceImpl` and
`OrdersPatchServiceImpl` reached `ShippersRepository` directly.

```java
public interface ShipperLookupContract {
    Optional<Shipper> findShipperByName(String name);
}
```

```java
@Service
@Transactional(readOnly = true)
public class ShipperLookupContractImpl
    implements ShipperLookupContract {

    @Override
    public Optional<Shipper> findShipperByName(String name) {
        return shippersRepository.findByName(name);
    }
}
```

| Property | Value |
|---|---|
| Direction | **read-only** — the order domain never creates a carrier through this seam |
| Transaction | `@Transactional(readOnly = true)` at class level |
| Implementation | pure delegation; behaviour identical to the baseline (G1) |
| Parameter shape | takes a **name**, not an entity — so unlike `SalespersonLookupContract`, it needs no redesign to survive the extraction |

**This is the simplest of the three Gate A seams and the only one with no open question.**
`BillingContract.resolveOrCreateBillingCompany` is a read *or a write*;
`CustomerProvisioningContract.resolveOrCreateCustomer` likewise; this one only reads, and it
is keyed by a value rather than an entity.

### 8.1 The missing test — added in step 3.8

**There is no `ShipperLookupContractImplTest` today.** `BillingContractImplTest` has 7 tests
and `CustomerProvisioningContractImplTest` has 4; this seam has **no behavioural pin at
all**.

That gap matters more after extraction than before. Once Orders is a separate service the
contract is the only thing between the checkout path and a carrier lookup, and an untested
seam is the one most likely to drift. Step 3.8 adds the test.

---

## 9. Test inventory (G8 — these port with the code)

| Test class | Path under `src/test/java/org/trebol/` | State |
|---|---|---|
| `DataShippersControllerTest` | `api/controllers/` | exists |
| `ShippersCrudServiceImplTest` | `jpa/services/crud/impl/` | exists |
| `ShippersConverterServiceImplTest` | `jpa/services/conversion/impl/` | exists |
| `ShippersPatchServiceImplTest` | `jpa/services/patch/impl/` | exists |
| `ShippersPredicateServiceImplTest` | `jpa/services/predicates/impl/` | exists |
| **`ShipperLookupContractImplTest`** | `jpa/services/contracts/impl/` | **does not exist — added in step 3.8** |

`ShippersCrudServiceImplTest` is the one to read carefully during the port: it asserts the
blank-name rejection, and therefore asserts the **wrong-domain message** from W1. If that
assertion is ever "corrected", the test and the code will disagree and one of them will be
silently changed to match. The message is contract; the test is right to pin it.

None may be deleted or disabled (G3).
