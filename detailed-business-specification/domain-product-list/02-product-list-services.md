# 02 — Product List Services

Source files covered: two repositories, the six service contracts and their five
implementations, two sort specs and two controllers — 18 of the domain's 21 files (the
other three are specified in document `01`).

Every literal message, parameter name, sort key and JPQL string below is **verbatim** and
is client-observable contract under invariant G2.

---

## 1. The six service contracts

| Contract | Declares |
|---|---|
| `ProductListCrudService extends CrudService<ProductListPojo, ProductList>` | nothing |
| `ProductListsConverterService extends ConverterService<ProductListPojo, ProductList>` | nothing |
| `ProductListItemsConverterService extends ConverterService<ProductPojo, ProductListItem>` | nothing — note the **cross-domain pojo type** |
| `ProductListsPatchService extends PatchService<ProductListPojo, ProductList>` | nothing |
| `ProductListsPredicateService extends PredicateService` | one constant: `QProductList basePath = QProductList.productList;` |
| `ProductListItemsPredicateService extends PredicateService` | one constant: `QProductListItem basePath = QProductListItem.productListItem;` |

There is **no CRUD service and no patch service for membership rows** — the contents
controller talks to the repository directly (§7).

---

## 2. Repositories

### 2.1 `ProductListsRepository extends Repository<ProductList>`

```java
@Repository
public interface ProductListsRepository extends Repository<ProductList> {
    Optional<ProductList> findByName(String name);
}
```

**One declared method, and it is `findByName` — there is no `findByCode`.** Everything
else is inherited: `count(Predicate)`, `findAll(Predicate)`, `findOne(Predicate)`,
`deleteAll`, `saveAndFlush`.

The absence of `findByCode` is why the contents controller resolves a list with
`findOne(QProductList.productList.code.eq(listCode))` instead (§7), and why existence
checks use the name (§3.2, BR-PRODLIST-003).

### 2.2 `ProductListItemsRepository extends Repository<ProductListItem>`

```java
@Modifying
@Transactional
@Query("DELETE FROM ProductListItem pi WHERE pi.list.id = :id")
void deleteByListId(@Param("id") Long id);

@Modifying
@Transactional
@Query("DELETE FROM ProductListItem pi WHERE pi.product.id = :id")
void deleteByProductId(@Param("id") Long id);
```

Both are **bulk JPQL deletes** carrying `@Modifying` and
`org.springframework.transaction.annotation.Transactional` **on the repository method
itself**. Two consequences a rewrite must preserve:

1. Bulk JPQL bypasses the persistence context and any cascade or callback — not that
   there are any callbacks here.
2. **The method-level `@Transactional` means that when the caller has no transaction, the
   delete commits on its own.** That is exactly the situation in
   `DataProductListContentsController.updateContents`, which has no `@Transactional`
   anywhere — so the delete commits before the re-inserts begin (§7.3, W1).

**`deleteByProductId` has no caller in `src/main`** (BR-PRODLIST-024, W12). Product
deletion relies on the database's `ON DELETE CASCADE` instead (BR-PRODLIST-023). It ports
verbatim anyway; removing it is a public API change.

---

## 3. `ProductListsCrudServiceImpl`

`@Transactional @Service`, `extends CrudGenericService<ProductListPojo, ProductList>
implements ProductListCrudService`.

Constructor takes `(ProductListsRepository, ProductListItemsRepository,
ProductListsConverterService, ProductListsPatchService)`, passes the lists repository,
converter and patch service to `super`, and **retains both repositories** as fields.

It overrides exactly two methods.

### 3.1 `delete(Predicate filters)` — override

`public void delete(Predicate filters) throws EntityNotFoundException`

1. `long count = listsRepository.count(filters);`
2. If `count == 0` → **`throw new EntityNotFoundException("Requested item(s) not found")`**
   (the inherited `ITEM_NOT_FOUND` constant; BR-PRODLIST-022).
3. `for (ProductList list : listsRepository.findAll(filters)) { listItemsRepository.deleteByListId(list.getId()); }`
4. `listsRepository.deleteAll(listsRepository.findAll(filters));`

**Children before parents** (BR-PRODLIST-021) — necessary because the `@OneToMany`
declares no cascade and no `orphanRemoval`. **`findAll(filters)` is executed twice**
(W13), once in step 3 and once in step 4.

The whole method runs inside the class-level `@Transactional`, so the bulk child deletes
join that transaction rather than committing independently — the opposite of the contents
controller's situation.

### 3.2 `getExisting(ProductListPojo input)` — override

```java
String name = input.getName();
if (StringUtils.isBlank(name)) {
    throw new BadInputException("The specified list has no name");
} else {
    return listsRepository.findByName(name);
}
```

Exact message: **`"The specified list has no name"`** (BR-PRODLIST-004).

**Existence is resolved by `name`, not `code`** (BR-PRODLIST-003, W5) — the opposite
choice from `product` (barcode) and `product-category` (code), and inconsistent with the
contents API, which addresses lists by `code`. Therefore:

- A duplicate **name** is caught cleanly: `validateInputPojoBeforeCreation` →
  **`EntityExistsException("The item already exists")`** → 409.
- A duplicate **code** passes the guard entirely and fails at the
  `product_list_code` unique constraint → **500**.
- A `POST` with no `name` reaches here (the pojo has no validation on `name`) and
  produces a 400.

### 3.3 Inherited CRUD behaviour

Inherited literals: `ITEM_NOT_FOUND = "Requested item(s) not found"`,
`ITEM_ALREADY_EXISTS = "The item already exists"`.

| Operation | Behaviour |
|---|---|
| `create(M)` | `validateInputPojoBeforeCreation` → `convertToNewEntity` → `saveAndFlush` → `convertToPojo`. |
| `update(M input)` | **`throw new UnsupportedOperationException("This method signature has been deprecated")`**. |
| `update(M, Long id)` | `convertToNewEntity`, `setId(id)`, `saveAndFlush`, `convertToPojo`. **Because the converter maps neither `disabled` nor `items`, a `PUT` resets `disabled` to `false`** (BR-PRODLIST-008, W6). Full replacement: an omitted `name` is written as null. |
| `update(M, Predicate)` | `count > 1` → **`throw new RuntimeException("Cannot update more than one item at a time")`** (BR-PRODLIST-029); `findOne` empty → `Optional.empty()`. |
| `partialUpdate(Map, Long)` / `(Map, Predicate)` | Predicate variant carries the same single-row guard and message; a `BadInputException` from the patch service is rewrapped as `new RuntimeException(e)`. |
| `flushPartialChanges(Map, entity)` | `patchService.patchExistingEntity(...)`; if `existingEntity.equals(updatedEntity)` return the original **with no write**; else `saveAndFlush`. Depends on the all-fields equality of document `01` §1.5, which includes the `items` collection. |
| `readOne(Predicate)` | Empty → **`throw new EntityNotFoundException("Requested item(s) not found")`**. |
| `readMany(...)` | Page of converted pojos — **one `COUNT` query per row** (§4.1, W9). |

---

## 4. The two converters

### 4.1 `ProductListConverterServiceImpl`

`@Service`, implements `ProductListsConverterService`. **Not `@Transactional`.**
Constructor-injects `ProductListItemsRepository productListItemRepository`.

**`convertToPojo(ProductList source)`**
1. `Long sourceListId = source.getId();`
2. `long itemCount = productListItemRepository.count(QProductListItem.productListItem.list.id.eq(sourceListId));`
3. `return ProductListPojo.builder().name(source.getName()).code(source.getCode()).totalCount(itemCount).build();`

**Step 2 is one `COUNT` query per list row**, so `readMany` is N+1 by construction
(W9). `disabled` is never mapped out (BR-PRODLIST-010).

**`convertToNewEntity(ProductListPojo source)`**
`ProductList.builder().name(source.getName()).code(source.getCode()).build()` —
`disabled` takes the primitive default **`false`**, `items` is null, `id` is null. This
single line is the cause of W6.

**`applyChangesToExistingEntity(...)`** →
**`throw new UnsupportedOperationException("This method is deprecated")`**.

### 4.2 `ProductListItemsConverterServiceImpl`

`@Transactional @Service`, implements `ProductListItemsConverterService`. Injects
**`ProductsConverterService`** — a cross-domain dependency, inside the catalogue.

| Method | Behaviour |
|---|---|
| `convertToPojo(ProductListItem source)` | `return productsConverterService.convertToPojo(source.getProduct());` — **the membership row's own id is never exposed** (BR-PRODLIST-013). A contents read therefore returns products indistinguishable from `/data/products` elements, except that images are never populated (the product converter does not fill them). |
| `convertToNewEntity(ProductPojo source)` | **`throw new UnsupportedOperationException("Not implemented")`** — a **third distinct message literal**; do not unify it with the other two. |
| `applyChangesToExistingEntity(ProductPojo, ProductListItem)` | **`throw new UnsupportedOperationException("This method is deprecated")`**. |

Because `convertToNewEntity` is unimplemented, **membership rows are constructed by hand
in the controller** (§7.2), which is how the generic framework comes to be bypassed.

---

## 5. `ProductListPatchServiceImpl`

`@Service @NoArgsConstructor` — no dependencies, pure in-memory transformation.

### 5.1 `patchExistingEntity(Map<String, Object> changes, ProductList existing)`

Declared `throws BadInputException`, but **never actually throws it**.

1. `ProductList target = new ProductList(existing);` — the clone, **with `items` null**
   but **`disabled` preserved** (document `01` §1.6).
2. If `changes.containsKey("name")`: cast to `(String)`; apply only if
   `!StringUtils.isBlank(name)` → `target.setName(name)`.
3. If `changes.containsKey("code")`: same cast and blank guard → `target.setCode(code)`.
4. Return `target`.

**Only two keys are recognised.** `disabled` and `totalCount` are **not handled**
(BR-PRODLIST-008) — supplying them is silently ignored. Unknown keys likewise.

**Blank values never clear a stored value.** A non-String JSON value causes an unchecked
**`ClassCastException`**, not a `BadInputException` → 500.

**`PATCH` is therefore the only operation that preserves `disabled`**, purely as a side
effect of the copy constructor.

### 5.2 `patchExistingEntity(ProductListPojo changes, ProductList existing)`

**`throw new UnsupportedOperationException("This method signature has been deprecated")`**.

---

## 6. The two predicate services

### 6.1 `ProductListsPredicateServiceImpl`

`@Transactional @Service`. A null parameter map yields an empty builder.

| Parameter | Behaviour |
|---|---|
| `name`, `listName` | **Returns immediately** with `and(basePath.name.eq(value))` — all other parameters discarded. |
| `code`, `listCode` | **Returns immediately** with `and(basePath.code.eq(value))`. |
| `nameLike` | `and(basePath.name.likeIgnoreCase("%" + value + "%"))`, continues. Value **not escaped**. |
| `codeLike` | `and(basePath.code.likeIgnoreCase("%" + value + "%"))`, continues. |
| anything else | `default: break` — ignored silently. |

**Two aliases per identity key** — `name`/`listName` and `code`/`listCode` — which exist
so that the same query string works against both this service and the contents service.
**There is no `id` key at all** (BR-PRODLIST-006), so lists cannot be addressed by
surrogate key on any route.

The `catch (NumberFormatException exc)` block logs
`"Param '{}' couldn't be parsed as number (value: '{}')"` but is **unreachable** — this
service parses no numbers. It ports as-is.

### 6.2 `ProductListItemsPredicateServiceImpl`

`@Transactional @Service`. **All branches `break`** — predicates accumulate, nothing
short-circuits.

| Parameter | Predicate |
|---|---|
| `listName` | `basePath.list.name.eq(value)` |
| `listCode` | `basePath.list.code.eq(value)` |
| `productName` | `basePath.product.name.eq(value)` |
| `productCode` | `basePath.product.barcode.eq(value)` |
| `productNameLike` | `basePath.product.name.likeIgnoreCase("%" + value + "%")` |
| `productCodeLike` | `basePath.product.barcode.likeIgnoreCase("%" + value + "%")` |
| anything else | ignored |

Note `productCode` maps to the product's **`barcode`** field — the field/column/parameter
naming divergence of the product domain surfaces here as a third name.

**Two consequences that make W2 dangerous:**

1. Pagination parameters (`pageIndex`, `pageSize`, `sortBy`, `order`) fall through to
   `default` and are ignored, which is correct — but so does **every mistyped product
   filter**.
2. With only `listCode` supplied the predicate matches **every row in that list**. That
   is what turns the contents `DELETE` into a full clear (BR-PRODLIST-017), and what makes
   a typo in a product filter clear the list instead of narrowing the delete.

---

## 7. The contents management path — `DataProductListContentsController`

`@RestController`, `@RequestMapping("/data/product_list_contents")`,
`@Tag(name = "Product Lists management")`. **It extends nothing** — the only
write-capable controller in the catalogue that bypasses the generic framework entirely
(BR-PRODLIST-030). Consequences: no `DataCrudGenericController` guards, no
`NO_RECOGNIZED_FILTERING_PARAMS` protection, and **no `@Transactional` anywhere in the
class**.

Own constant: `private static final String ITEM_NOT_FOUND = "Requested item(s) not found";`
— the same text as the kernel's, declared again locally. Both copies port.

Seven constructor-injected dependencies: `PaginationService`, `SortSpecParserService`,
`ProductListItemsRepository`, `ProductListsRepository`,
`ProductListItemsPredicateService`, **`ProductsCrudService`** (cross-domain) and
`ProductListItemsConverterService`.

### 7.0 The shared resolver — every endpoint starts here

```java
private Optional<ProductList> fetchProductListByCode(Map<String, String> requestParams) throws BadInputException {
    String listCode = requestParams.get("listCode");
    if (StringUtils.isBlank(listCode)) {
        throw new BadInputException("listCode query param is required");
    }
    return listsRepository.findOne(QProductList.productList.code.eq(listCode));
}
```

All four endpoints call it first and, on `Optional.isEmpty()`, **`throw new
EntityNotFoundException("Requested item(s) not found")`** (BR-PRODLIST-011,
BR-PRODLIST-012).

**The `disabled` flag is never consulted** (BR-PRODLIST-009) — a disabled list's contents
are fully readable and writable.

### 7.1 READ — `readContents`

1. Resolve the list (§7.0).
2. `pageIndex = paginationService.determineRequestedPageIndex(requestParams)`;
   `pageSize = paginationService.determineRequestedPageSize(requestParams)`.
3. If `requestParams.containsKey("sortBy")` →
   `Sort order = sortService.parse(ProductListItemsSortSpec.ORDER_SPEC_MAP, requestParams);`
   `pagination = PageRequest.of(pageIndex, pageSize, order);` else
   `PageRequest.of(pageIndex, pageSize)`.
4. `Predicate predicate = listItemsPredicateService.parseMap(requestParams);`
   `Page<ProductListItem> listItems = listItemsRepository.findAll(predicate, pagination);`
5. Per item: `ProductPojo productPojo = itemConverterService.convertToPojo(item);` →
   accumulate.
6. `long totalCount = listItemsRepository.count(QProductListItem.productListItem.list.id.eq(match.get().getId()));`
7. `return new DataPagePojo<>(products, pageIndex, totalCount, pageSize);`

**Step 6 ignores the product filters entirely** and counts the whole list (W10), so a
filtered page reports a total inconsistent with its own contents. Preserved behaviour.

Every item load walks the **eager** associations (document `01` §2.2), so this endpoint
reads a full `ProductList` and a full `Product` per row with no `JOIN FETCH`.

### 7.2 ADD — `addToContents`

1. Resolve the list → 404 if unknown.
2. `Optional<Product> productMatch = productCrudService.getExisting(input);` — resolves by
   barcode. A **blank or missing barcode** throws
   **`BadInputException("Invalid product barcode")`** from the product domain; an
   **unknown barcode** returns empty.
3. If `productMatch.isPresent()`, build
   `ProductListItem.builder().list(listMatch.get()).product(productMatch.get()).build()`
   (`id` null) — **by hand, because the item converter's `convertToNewEntity` throws
   `"Not implemented"`** (§4.2).
4. `if (!listItemsRepository.exists(Example.of(listItem))) { listItemsRepository.save(listItem); }`

**Idempotent in practice, non-atomically** (BR-PRODLIST-014, W3): a read-then-write
`Example` match with no database unique constraint and no surrounding transaction, so
concurrent adds duplicate.

**An unknown barcode silently does nothing and still returns 201** (BR-PRODLIST-015,
W4).

### 7.3 REPLACE — `updateContents` — destructive first

1. Resolve the list → 404 if unknown.
2. **`listItemsRepository.deleteByListId(listMatch.get().getId());`** — the bulk JPQL
   delete. Because the repository method carries its own `@Transactional` and the
   controller has none, **it commits immediately**.
3. For each `ProductPojo p`: `productCrudService.getExisting(p)`; if present, build the
   item and `if (!listItemsRepository.exists(Example.of(listItem))) save(listItem);` —
   **each save is its own transaction.**

**Consequences, all preserved (BR-PRODLIST-016, W1):**

| Input | Outcome |
|---|---|
| Empty body | List **emptied**, 204. |
| All products unresolvable | List **emptied**, 204 — indistinguishable from success. |
| A blank barcode mid-collection | `BadInputException` propagates → 400, **after the delete has already committed** and after some re-inserts have committed. The list is left in a partial state with **no rollback and no compensating action**. |
| Duplicates within the submitted collection | Collapse to one row, thanks to the `exists` check. |

The body is **not `@Valid`** (BR-PRODLIST-018, W11), which is why a blank barcode reaches
the service at all.

### 7.4 REMOVE — `deleteFromContents`

1. Resolve the list → 404 if unknown.
2. `Predicate predicate = listItemsPredicateService.parseMap(requestParams);`
3. `listItemsRepository.deleteAll(listItemsRepository.findAll(predicate));` —
   entity-by-entity delete inside the repository's own transaction.

**With only `listCode` supplied the predicate matches every row of the list, so the whole
list is cleared** (BR-PRODLIST-017, W2) — inverting the convention of every other
write-capable controller, where an unrecognised selector is now rejected. Because this
controller extends nothing, the Phase-0 D1 guard does **not** apply, and a mistyped
product filter degrades to "delete the whole list".

**Note the predicate is not scoped to the resolved list.** It is built purely from the
request parameters, so a request naming one list in `listCode` and a different one in
`listName` deletes from whichever the predicate matches.

---

## 8. The two sort specs

### 8.1 `ProductListsSortSpec`

```java
@NoArgsConstructor(access = PRIVATE)
public class ProductListsSortSpec {
    private static final QProductList BASE_PATH = QProductList.productList;
    public static final Map<String, OrderSpecifier<?>> ORDER_SPEC_MAP = Map.of(
        "name",       BASE_PATH.name.asc(),
        "code",       BASE_PATH.code.asc(),
        "totalCount", BASE_PATH.items.size().asc()
    );
}
```

**Three keys.** `totalCount` → `items.size()`, an **aggregate subquery per row**
(BR-PRODLIST-007, W9) layered on top of the converter's per-row `COUNT`. Sorting lists by
item count is therefore the most expensive read in the catalogue. `disabled` is not
sortable.

### 8.2 `ProductListItemsSortSpec`

```java
private static final QProductListItem BASE_PATH = QProductListItem.productListItem;
private static final QProduct PRODUCT_PATH = BASE_PATH.product;
// "name"    -> PRODUCT_PATH.name.asc()
// "barcode" -> PRODUCT_PATH.barcode.asc()
```

**Two keys, both reaching into the product domain.** No key sorts by membership id or by
list, so contents have **no stable insertion order** — unsorted reads return whatever the
database yields.

Both maps are the **mitigation evidence for the QueryDSL CVE** (register item D21):
`orderBy` only ever receives an `OrderSpecifier` from these maps, so no raw client string
reaches it.

---

## 9. `DataProductListsController`

`@RestController`, `@RequestMapping("/data/product_lists")`,
`@Tag(name = "Product Lists management")`; extends
`DataCrudGenericController<ProductListPojo, ProductList>`. Injects `PaginationService`,
`SortSpecParserService`, `ProductListCrudService`, `ProductListsPredicateService`.

| Method | Mapping | Response status | `@PreAuthorize` | Signature |
|---|---|---|---|---|
| `readMany` | `@GetMapping` → `GET /data/product_lists` | 200 | **none — publicly reachable** | `DataPagePojo<ProductListPojo> readMany(@RequestParam Map<String,String> allRequestParams)` |
| `create` | `@PostMapping` | `201 CREATED` | `PRODUCT_LISTS_CREATE` | `void create(@Valid @RequestBody ProductListPojo input) throws BadInputException, EntityExistsException` |
| `update` | `@PutMapping` | `204 NO_CONTENT` | `PRODUCT_LISTS_UPDATE` | `void update(@Valid @RequestBody ProductListPojo input, @RequestParam Map<String,String> requestParams) throws BadInputException, EntityNotFoundException` |
| `partialUpdate` | `@PatchMapping` | `204 NO_CONTENT` | `PRODUCT_LISTS_UPDATE` | `void partialUpdate(@RequestBody Map<String,Object> input, @RequestParam Map<String,String> requestParams) throws BadInputException, EntityNotFoundException` — **no `@Valid`** |
| `delete` | `@DeleteMapping` | `204 NO_CONTENT` | `PRODUCT_LISTS_DELETE` | `void delete(@RequestParam Map<String,String> requestParams) throws BadInputException, EntityNotFoundException` |

`@Override protected Map<String, OrderSpecifier<?>> getOrderSpecMap()` returns
`ProductListsSortSpec.ORDER_SPEC_MAP`.

Inherited write behaviour is the standard kernel set: `"Missing request params"` on an
empty parameter map, `"No element was found to update"` when the lookup resolves nothing,
**`NO_RECOGNIZED_FILTERING_PARAMS`** on a delete whose predicate has no value (the
Phase-0 D1 fix), and a **silent 204 no-op** when the delete parameter map is entirely
empty.

No empty-parameter rewrite: a bare `GET /data/product_lists` returns **all lists**,
paged.

---

## 10. Route inventory — 9 endpoints

| Route | Methods | Authority |
|---|---|---|
| `/data/product_lists` | GET | **none** |
| `/data/product_lists` | POST / PUT / PATCH / DELETE | `product_lists:create` / `:update` / `:update` / `:delete` |
| `/data/product_list_contents` | GET | **none** |
| `/data/product_list_contents` | POST / PUT / DELETE | **`product_lists:contents` — one authority for all three** |

The contents surface has **no `PATCH`**, because membership rows are immutable
(BR-PRODLIST-019). See `permissions.md` for the single-authority consequence.

---

## 11. Test inventory (G8 — these port with the code)

| Test class | Notes |
|---|---|
| `api/controllers/DataProductListContentsControllerTest` | `@ExtendWith(MockitoExtension)`, `@InjectMocks` with **all seven mocks**; cases `reads_contents_of_list`, `accepts_sorting_order`, `adds_item_to_list_contents`, `updates_list_contents`, `deletes_from_list_contents`, `fails_when_target_list_is_not_specified`, `fails_when_target_list_does_not_exist_or_cannot_be_found` |
| `api/controllers/DataProductListsControllerTest` | the conventional controller |
| `jpa/services/conversion/impl/ProductListConverterServiceImplTest` | |
| `jpa/services/conversion/impl/ProductListItemsConverterServiceImplTest` | |
| `jpa/services/crud/impl/ProductListCrudServiceImplTest` | |
| `jpa/services/patch/impl/ProductListPatchServiceImplTest` | |

**The only shared helper used is `org/trebol/testing/TestConstants`** (`ANY`, and
`NOT_ANY` in the patch test). **No product-list-specific test helper exists**, and
notably `ProductsTestHelper` is *not* used by these tests — so the port needs no new
fixture beyond `TestConstants`.
