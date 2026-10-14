# 02 — Product Category Services

Source files covered: the repository, the four service contracts and their
implementations, the tree resolver, the sort spec and the controller — 13 of the
domain's 15 files (the other two are specified in document `01`).

Every literal message, parameter name, sort key and authority constant below is
**verbatim** and is client-observable contract under invariant G2.

---

## 1. The four service contracts

All four are type bindings onto shared-kernel generics. Three declare **no methods of
their own**:

| Contract | Declares |
|---|---|
| `ProductCategoriesCrudService extends CrudService<ProductCategoryPojo, ProductCategory>` | nothing |
| `ProductCategoriesConverterService extends ConverterService<ProductCategoryPojo, ProductCategory>` | nothing |
| `ProductCategoriesPatchService extends PatchService<ProductCategoryPojo, ProductCategory>` | nothing |
| `ProductCategoriesPredicateService extends PredicateService` | one constant: `QProductCategory basePath = QProductCategory.productCategory;` |

A rewrite can collapse these into the implementations only if it accepts losing the
injection points the tests mock. All six test classes mock against these interfaces, so
under G8 and G3 they port as-is.

---

## 2. `ProductsCategoriesRepository`

`@Repository public interface ProductsCategoriesRepository extends Repository<ProductCategory>`

Note the class name: **`ProductsCategories`**, plural-plural, inconsistent with the
singular entity and with the service names. It ports verbatim; renaming is forbidden.

```java
Optional<ProductCategory> findByCode(String code);

List<ProductCategory> findByName(String code);   // parameter is literally named "code"

List<ProductCategory> findByParent(ProductCategory parent);

@Query("SELECT r.id FROM ProductCategory r WHERE r.parent.id = :parentId")
List<Long> findIdsByParentId(@Param("parentId") Long parentId);
```

| Method | Used by | Notes |
|---|---|---|
| `findByCode` | `getExisting`, the converter's parent resolution, `getBranchIdsFromRootCode` | The domain's only identity lookup. |
| `findByName` | nothing in production | Returns a `List` because sibling-scoped uniqueness makes names non-unique globally. The misnamed parameter is cosmetic — Spring Data derives the query from the method name, not the parameter. |
| `findByParent` | `getBranchesFromRoot` and its recursion | Entity-valued parameter; one query per visited node. |
| `findIdsByParentId` | `getBranchIdsFromRootId` and its recursion | The id-only projection exists to make the recursive walk cheaper. The JPQL string is contract for the port — it must be copied verbatim. |

The generic `Repository<ProductCategory>` base supplies `findAll(Predicate, Pageable)`,
`findOne(Predicate)`, `count(Predicate)`, `saveAndFlush`, `deleteAll` and the rest of
the QueryDSL-capable surface.

---

## 3. `ProductCategoriesCrudServiceImpl`

`@Transactional @Service`, extends `CrudGenericService<ProductCategoryPojo, ProductCategory>`.

Constructor injects `ProductsCategoriesRepository`, `ProductCategoriesConverterService`
and `ProductCategoriesPatchService`; all three are passed to `super`, and the
repository and patch service are also retained as fields for the two overrides.

### 3.1 `getExisting(ProductCategoryPojo input)` — override

```java
@Override public Optional<ProductCategory> getExisting(ProductCategoryPojo input) throws BadInputException
```

1. `String code = input.getCode();`
2. If `StringUtils.isBlank(code)` → **`throw new BadInputException("Invalid category code")`**
3. Otherwise `return categoriesRepository.findByCode(code);`

**`code` is the sole business key for existence checks; `name` is never consulted**
(BR-PRODCAT-003, BR-PRODCAT-004). Because the composite `(parent, name)` constraint is
*not* checked here, a sibling-name collision passes this guard and fails at the
database — the same class of defect as D7 in the `image` domain, and it is **not** in
scope for Phase 1. It carries no fix ruling; porting preserves it.

### 3.2 `flushPartialChanges(Map<String, Object> changes, ProductCategory existingEntity)` — override

Declared `protected final`.

1. `preparedEntity = categoriesPatchService.patchExistingEntity(changes, existingEntity)`
   — returns a clone **with `parent` null** (document `01` §1.6).
2. If `existingEntity.getParent() != null` → `preparedEntity.setParent(existingEntity.getParent())`
   — **the original parent is always restored.**
3. If `existingEntity.equals(preparedEntity)` → return `existingEntity` **without any
   database write** (the no-op patch optimisation, which depends on the all-fields
   equality of document `01` §1.5).
4. Otherwise `return categoriesRepository.saveAndFlush(preparedEntity);`

Step 2 is the mechanism behind BR-PRODCAT-006: **`PATCH` can neither detach nor
re-parent a category**, regardless of what the request body contains.

### 3.3 Inherited CRUD behaviour (`CrudGenericService`)

Two inherited literals, declared as constants on the base:

```java
protected static final String ITEM_NOT_FOUND = "Requested item(s) not found";
protected static final String ITEM_ALREADY_EXISTS = "The item already exists";
```

| Operation | Behaviour |
|---|---|
| `create(M input)` | `validateInputPojoBeforeCreation`; if `getExisting(input).isPresent()` → `throw new EntityExistsException(ITEM_ALREADY_EXISTS)`; else `convertToNewEntity` → `saveAndFlush` → `convertToPojo` of the saved entity. |
| `update(M input)` | **`throw new UnsupportedOperationException("This method signature has been deprecated")`** — unconditional. |
| `update(M input, Long id)` | Converts the input to a **brand-new entity**, `setId(id)`, `saveAndFlush`. A **full replacement**: every field the input omits is written as null, and the parent is whatever the converter resolved. This is the only re-parenting path and the only path that can write a cycle (BR-PRODCAT-008, BR-PRODCAT-009). |
| `update(M input, Predicate filters)` | `count(filters) > 1` → **`throw new RuntimeException("Cannot update more than one item at a time")`**; `findOne` empty → `Optional.empty()`; else delegates to `update(input, id)`. |
| `partialUpdate(Map, Long)` | Loads by id, delegates to `flushPartialChanges`. A `BadInputException` from below is wrapped as `new RuntimeException(e)`. |
| `partialUpdate(Map, Predicate)` | Same single-row guard and the same `"Cannot update more than one item at a time"` message (BR-PRODCAT-020). |
| `delete(Predicate filters)` | `count(filters) == 0` → `throw new EntityNotFoundException(ITEM_NOT_FOUND)`; else `deleteAll(findAll(filters))`. |
| `readOne(Predicate)` | Empty → `throw new EntityNotFoundException(ITEM_NOT_FOUND)`. |
| `readMany(...)` | Page of converted pojos; see §9. |

`@Transactional` is on the class, so each service call is one transaction. The tree
resolver is **not** called from any write path, so no write ever validates the tree.

---

## 4. `ProductCategoriesConverterServiceImpl`

`@Service`; injects `ProductsCategoriesRepository` — a converter that performs
**database reads**, which is unusual and is the parent-resolution mechanism.

### 4.1 `convertToPojo(ProductCategory source)`

```java
return ProductCategoryPojo.builder()
    .code(source.getCode())
    .name(source.getName())
    .build();
```

**The parent is never emitted.** This is why the LAZY parent association never causes a
lazy-initialization failure on the read path, and why the outbound nesting depth is 0
(document `01` §2.2).

### 4.2 `convertToNewEntity(ProductCategoryPojo source)`

1. `ProductCategory target = ProductCategory.builder().code(source.getCode()).name(source.getName()).build();`
   — `id` null, `parent` null.
2. If `source.getParent() != null`:
   `String parentCode = source.getParent().getCode();`
   `categoriesRepository.findByCode(parentCode).ifPresent(target::setParent);`
3. Return `target`.

**Parent resolution — all four cases, all silent:**

| Input | Result |
|---|---|
| `parent` absent / null | Category is a **root**. Correct and intended. |
| `parent.code` unknown | `findByCode` empty → `ifPresent` no-op → category is a **root**, HTTP 201, **no error** (W2, BR-PRODCAT-008). |
| `parent.code` blank or null | Passed straight to `findByCode` with no guard; empty result → **root**. No NPE. |
| `parent.code` equals the category's own code | Resolves to **itself** → a self-parent cycle is persisted with **zero validation** (W1, BR-PRODCAT-009). |

There is no ancestor walk, so a longer cycle (A→B→A) is equally creatable through two
`PUT` calls.

### 4.3 `applyChangesToExistingEntity(ProductCategoryPojo source, ProductCategory target)`

**`throw new UnsupportedOperationException("This method is deprecated")`** —
unconditional. The method exists only to satisfy the generic contract. Note the message
differs from the patch service's (§5.2); both must port verbatim.

---

## 5. `ProductCategoriesPatchServiceImpl`

`@Service @NoArgsConstructor` — no dependencies, pure in-memory transformation.

### 5.1 `patchExistingEntity(Map<String, Object> changes, ProductCategory existing)`

Declared `throws BadInputException`, but **never actually throws it**.

1. `ProductCategory target = new ProductCategory(existing);` — the clone, **with
   `parent` dropped to null**.
2. If `changes.containsKey("code")`: cast the value to `String`; if
   `!StringUtils.isBlank(code)` → `target.setCode(code)`.
3. Identical logic for `"name"`.
4. `return target;`

**Three porting-critical facts:**

- **No `"parent"` key is handled at all.** Supplying one is silently ignored
  (BR-PRODCAT-007), independently of the restore in §3.2.
- **Blank values never clear a field** (BR-PRODCAT-019). A patch of
  `{"name": ""}` is accepted, changes nothing, and because the clone then equals the
  original, performs no write and returns 204.
- **A non-String JSON value causes a `ClassCastException`** — an unchecked cast with no
  type guard. `{"code": 5}` yields a 500. Unhandled and unregistered; it ports as-is.

Only the two keys above are recognised. Unknown keys are ignored without error.

### 5.2 `patchExistingEntity(ProductCategoryPojo changes, ProductCategory existing)`

**`throw new UnsupportedOperationException("This method signature has been deprecated")`**
— unconditional. Message text differs from §4.3; preserve both exactly.

---

## 6. `ProductCategoriesPredicateServiceImpl`

`@Service`. Holds `private static final QProductCategory parentPath = basePath.parent;`,
an SLF4J logger, and an injected `ProductCategoryTreeResolverService`.

### 6.1 `parseMap(Map<String, String> queryParamsMap)`

Builds a QueryDSL `BooleanBuilder`, iterating the entries and switching on the
parameter name. **The accepted parameter names are contract.**

| Parameter | Behaviour |
|---|---|
| `id` | **Returns immediately** with `basePath.id.eq(Long.valueOf(v))` — every other parameter is discarded (BR-PRODCAT-013). |
| `code` | **Returns immediately** with `basePath.code.eq(v)` — same short-circuit. |
| `name` | `and(basePath.name.eq(v))` — exact match. |
| `nameLike` | `and(basePath.name.likeIgnoreCase("%" + v + "%"))` — the value is **not escaped**, so `%` and `_` in client input act as wildcards. |
| `parentCode` | Applied only if `isNotBlank` → `and(parentPath.code.eq(v))`. |
| `parentId` | If **blank** → `and(parentPath.isNull())`, i.e. **root categories only** (BR-PRODCAT-012). Otherwise `and(parentPath.id.eq(Long.valueOf(v)))`. |
| `rootId` | If not blank → `and(parentPath.id.in(treeResolver.getBranchIdsFromRootId(Long.valueOf(v))))`. An **empty** list is still applied, so an unknown root id **matches nothing**. |
| `rootCode` | If not blank → `getBranchIdsFromRootCode(v)`, and the `in` clause is applied **only when the list is non-empty** (`CollectionUtils.isEmpty` guard). An unknown root code therefore adds **no filter at all** and the query **matches everything** (W3, BR-PRODCAT-011). |
| anything else | Ignored silently. |

`catch (NumberFormatException exc)` →
`logger.info("Param '{}' couldn't be parsed as number (value: '{}')", paramName, stringValue)`
and the loop continues: a malformed numeric filter is **silently dropped** rather than
rejected, widening the result set.

The method returns the `BooleanBuilder`, which may carry no condition at all — a
match-everything predicate. On the read path that is a full listing; on the delete path
the Phase-0 D1 guard in `DataCrudGenericController` now rejects it (§9.3).

**`rootId` and `rootCode` behave differently for an unresolvable value.** That
asymmetry is the single most surprising thing in this class and must be preserved.

---

## 7. `ProductCategoriesSortSpec`

```java
@NoArgsConstructor(access = PRIVATE)
public class ProductCategoriesSortSpec {
    private static final QProductCategory BASE_PATH = QProductCategory.productCategory;
    public static final Map<String, OrderSpecifier<?>> ORDER_SPEC_MAP = Map.of(
        "name", BASE_PATH.name.asc(),
        "code", BASE_PATH.code.asc()
    );
}
```

**Exactly two accepted sort keys** — `name` → `productCategory.name`, `code` →
`productCategory.code`. No `id`, no `parent`. Direction is baked in as `.asc()`; the
shared `SortSpecParserService` flips it for a descending request. A sort key outside
this map is rejected by the parser service, not here.

This whitelist is also the **mitigation evidence for the QueryDSL CVE**
(register item D21): `orderBy` only ever receives an `OrderSpecifier` from this map, so
no raw client string reaches it.

---

## 8. `ProductCategoryTreeResolverService`

### 8.1 Contract

```java
List<ProductCategory> getBranchesFromRoot(ProductCategory rootBranch);
List<Long>            getBranchIdsFromRootId(Long rootId);
List<Long>            getBranchIdsFromRootCode(String rootCode);
```

Javadoc on each: *"Recursively fetches all categories descendants of a given \"root\"
category."*

### 8.2 `ProductCategoryTreeResolverServiceImpl`

`@Service`; injects `ProductsCategoriesRepository` and `org.trebol.config.ApiProperties`.

**`getBranchesFromRoot(rootBranch)`**
1. `int maxAllowedDepth = apiProperties.getMaxCategoryFetchingRecursionDepth();`
2. `immediateDescendants = repository.findByParent(rootBranch);`
3. `allBranches = new ArrayList<>(immediateDescendants);`
4. For each immediate descendant → `recursivelyAddBranches(allBranches, branch, maxAllowedDepth);`
5. Return `allBranches` — **the root itself is not included.**

**`getBranchIdsFromRootId(rootId)`** — identical shape using `findIdsByParentId` and
`recursivelyAddBranchIds`.

**`getBranchIdsFromRootCode(rootCode)`** — `findByCode(rootCode)`; if empty
**`return List.of()`** (an empty list, **no exception**); else
`getBranchIdsFromRootId(byCode.get().getId())`. This is the upstream half of W3.

**The two private recursors**
```java
private void recursivelyAddBranches(Collection<ProductCategory> allBranches, ProductCategory branch, int deepnessLeft)
private void recursivelyAddBranchIds(Collection<Long> branchIds, Long bId, int deepnessLeft)
```
Each: if `deepnessLeft > 0`, query the children, add each to the collection, and recurse
with `deepnessLeft - 1`.

**Properties of the traversal that a rewrite must reproduce:**

| Property | Detail |
|---|---|
| Order | Depth-first, pre-order. |
| Cost | **One query per visited node** — an N+1 pattern by construction. |
| Cycle handling | **None.** No visited set, no equality check, no root-identity check. A cycle simply recurses until `deepnessLeft` reaches 0, and because the accumulator is an `ArrayList`, the returned list contains **duplicated ids** (BR-PRODCAT-009, BR-PRODCAT-010). |
| Bound | `ApiProperties.maxCategoryFetchingRecursionDepth`, bound from `trebol.api.max-category-fetching-recursion-depth=20` in `application.properties`. The top level is fetched **before** the counter applies, so the effective reachable depth is `depth + 1`. |
| Root inclusion | Never included in the result. |

**Consumers.** Only `ProductCategoriesPredicateServiceImpl` (`rootId`, `rootCode`) and
`ProductsPredicateServiceImpl` (branch-aware product search, CDF-05 — owned by the
`product` domain). **No controller calls it directly, and
`getBranchesFromRoot(ProductCategory)` has no production caller at all** (W7): it is the
entity-returning variant that the never-implemented orphan-before-delete operation would
have used.

The depth setting ports into the catalogue service's configuration with the same key and
the same default of 20.

---

## 9. `DataProductCategoriesController`

`@RestController`, `@RequestMapping("/data/product_categories")`,
`@Tag(name = "Product Categories management")`; extends
`DataCrudGenericController<ProductCategoryPojo, ProductCategory>`.

Constructor injects `PaginationService`, `SortSpecParserService`,
`ProductCategoriesCrudService` and `ProductCategoriesPredicateService`, all passed to
`super`.

Authority constants are imported statically from
`org.trebol.security.authorities.AuthorityCodes` (Phase 0, Gate B — no raw literals
remain).

### 9.1 Route table

| Method | Mapping | Response status | `@PreAuthorize` | Signature |
|---|---|---|---|---|
| `readMany` | `@GetMapping` → `GET /data/product_categories` | 200 | **none — publicly reachable** | `DataPagePojo<ProductCategoryPojo> readMany(@RequestParam Map<String,String> allRequestParams)` |
| `create` | `@PostMapping` | `201 CREATED` | `hasAuthority('` + `PRODUCT_CATEGORIES_CREATE` + `')` | `void create(@Valid @RequestBody ProductCategoryPojo input) throws BadInputException, EntityExistsException` |
| `update` | `@PutMapping` | `204 NO_CONTENT` | `PRODUCT_CATEGORIES_UPDATE` | `void update(@Valid @RequestBody ProductCategoryPojo input, @RequestParam Map<String,String> requestParams) throws BadInputException, EntityNotFoundException` |
| `partialUpdate` | `@PatchMapping` | `204 NO_CONTENT` | `PRODUCT_CATEGORIES_UPDATE` | `void partialUpdate(@RequestBody Map<String,Object> input, @RequestParam Map<String,String> requestParams) throws BadInputException, EntityNotFoundException` |
| `delete` | `@DeleteMapping` | `204 NO_CONTENT` | `PRODUCT_CATEGORIES_DELETE` | `void delete(@RequestParam Map<String,String> requestParams) throws BadInputException, EntityNotFoundException` |

`@Override protected Map<String, OrderSpecifier<?>> getOrderSpecMap()` returns
`ProductCategoriesSortSpec.ORDER_SPEC_MAP`.

`PRODUCT_CATEGORIES_READ` (`"product_categories:read"`) exists in the catalogue and is
seeded in the database, but **is referenced by nothing** — reads are unauthenticated
(BR-PRODCAT-014, BR-PRODCAT-015). See `permissions.md`.

### 9.2 The empty-parameter rewrite — a real behavioural rule

`readMany` substitutes `Map.of("parentId", "")` when `allRequestParams` is null or
empty. The predicate service maps a blank `parentId` to `parent IS NULL`. Therefore:

- `GET /data/product_categories` → **root categories only** (BR-PRODCAT-012).
- Supplying *any* parameter switches to the normal predicate path, so
  `GET /data/product_categories?name=x` searches the **whole tree**, not just roots.
- `id` or `code` short-circuit and discard all other filters (§6.1).

A rewrite that treats "no filters" as "return everything" changes the default
storefront response. This is the subtlest behaviour in the domain.

### 9.3 Inherited write behaviour (`DataCrudGenericController`)

```java
public static final String NO_RECOGNIZED_FILTERING_PARAMS = "No recognized filtering parameters were provided";
```

- `update` / `partialUpdate`: if `requestParams.isEmpty()` →
  **`throw new BadInputException("Missing request params")`**; then
  `predicateService.parseMap(requestParams)`, and the resolved row is
  `orElseThrow(() -> new EntityNotFoundException("No element was found to update"))`.
- `delete`: acts only when `!requestParams.isEmpty()` — **an empty parameter map is a
  silent no-op returning 204**. Otherwise, if the resulting predicate is a
  `BooleanBuilder` with `!hasValue()` →
  **`throw new BadInputException(NO_RECOGNIZED_FILTERING_PARAMS)`** (the Phase-0 D1
  fix), else `crudService.delete(predicate)`.

**Writes are addressed by query parameters.** The short-circuiting of §6.1, the
unescaped `nameLike` metacharacters and the silently dropped malformed numerics all
therefore apply to `PUT`, `PATCH` and `DELETE`, not only to searches. There is no
path-identifier route form.

### 9.4 Registered routes

`application.properties` also registers, for CORS purposes,
`GET,POST,PUT,DELETE /data/product_categories;` and
`GET,PUT,DELETE /data/product_categories/*;`. The starred form is **not served by any
mapping** in this controller — a stale register entry, preserved as-is.

---

## 10. Test inventory (G8 — these port with the code)

| Test class | Mocks / helpers |
|---|---|
| `api/controllers/DataProductCategoriesControllerTest` | extends `api/DataCrudGenericControllerTest`; mocks all four injected services |
| `jpa/services/conversion/impl/ProductCategoriesConverterServiceImplTest` | `ProductCategoriesTestHelper`, mocks `ProductsCategoriesRepository` |
| `jpa/services/crud/impl/ProductCategoriesCrudServiceImplTest` | `ProductCategoriesTestHelper`, mocks repository + patch service |
| `jpa/services/impl/ProductCategoryTreeResolverServiceImplTest` | mocks `ProductsCategoriesRepository` + `ApiProperties` |
| `jpa/services/patch/impl/ProductCategoriesPatchServiceImplTest` | none |
| `jpa/services/predicates/impl/ProductCategoriesPredicateServiceImplTest` | mocks `ProductCategoryTreeResolverService` |

Shared helpers that must port with them: `org/trebol/testing/ProductCategoriesTestHelper`
(constants `PRODUCT_ID = 1L`, `CATEGORY_NAME = "test product name"`,
`CATEGORY_CODE = "TESTPROD1"`; factories `productCategoryPojoForFetch()`,
`productCategoryPojoBeforeCreation()`, `productCategoryPojoAfterCreation()`,
`productCategoryEntityBeforeCreation()`, `productCategoryEntityAfterCreation()`) and the
abstract `org/trebol/api/DataCrudGenericControllerTest` base.
