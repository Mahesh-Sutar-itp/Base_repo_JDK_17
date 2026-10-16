# 02 — Product Services

Source files covered: three repositories, the four service contracts and their
implementations, the sort spec and the controller — 13 of the domain's 16 files (the
other three are specified in document `01`).

Every literal message, parameter name, sort key, JPQL string and authority constant
below is **verbatim** and is client-observable contract under invariant G2.

---

## 1. The four service contracts

| Contract | Declares |
|---|---|
| `ProductsCrudService extends CrudService<ProductPojo, Product>` | nothing |
| `ProductsConverterService extends ConverterService<ProductPojo, Product>` | **one extra method:** `Collection<ImagePojo> convertImagesToPojo(Collection<ProductImage> productImages);` |
| `ProductsPatchService extends PatchService<ProductPojo, Product>` | nothing |
| `ProductsPredicateService extends PredicateService` | one constant: `QProduct basePath = QProduct.product;` |

The converter's extra method is the only domain-specific contract addition in the
catalogue. All five product test classes mock against these interfaces, so under G3 and
G8 they port as-is.

---

## 2. Repositories

### 2.1 `ProductsRepository extends Repository<Product>`

```java
@Query(value = "SELECT p FROM Product p JOIN FETCH p.productCategory",
       countQuery = "SELECT p FROM Product p")
Page<Product> deepReadAll(Pageable pageable);

@Query(value = "SELECT p FROM Product p JOIN FETCH p.productCategory",
       countQuery = "SELECT p FROM Product p")
Page<Product> deepReadAll(Predicate filters, Pageable pageable);

Optional<Product> findByBarcode(String barcode);

@Modifying
@Transactional
@Query("UPDATE Product p SET p.productCategory.id = :categoryId WHERE p.id = :id")
void setProductCategoryById(@Param("id") Long productId, @Param("categoryId") Long categoryId);

@Modifying
@Transactional
@Query("UPDATE Product p SET p.productCategory = null WHERE p.productCategory IN (:categories)")
void orphanizeByCategories(@Param("categories") Collection<ProductCategory> categories);
```

| Method | Caller | Notes |
|---|---|---|
| `findByBarcode` | `getExisting` (§4.4) | The domain's only identity lookup, and the basis of the Gate A contract. |
| `deepReadAll(Pageable)` | **none** | Dead. |
| `deepReadAll(Predicate, Pageable)` | **none** | **The `Predicate` parameter is never referenced by the query** (BR-PRODUCT-032, W8). It would also silently drop uncategorised products, because `JOIN FETCH` without `LEFT` is an inner join. A rewrite must not "wire it up" — it is dead code with wrong semantics, and switching the service to it would change results. |
| `setProductCategoryById` | **none** | Dead. |
| `orphanizeByCategories` | **none** | Dead. This is the operation that would make category deletion safe (BR-PRODUCT-020); nothing calls it. |

Four of the five declared methods have no caller. They port verbatim anyway —
`@Modifying @Transactional @Query` strings included — because deleting them is a public
API change and the architecture tests do not permit silent removals.

### 2.2 `ProductImagesRepository extends Repository<ProductImage>`

```java
List<ProductImage> findByProductId(long productId);

@Query("SELECT pi FROM ProductImage pi JOIN FETCH pi.image WHERE pi.product.id = :id")
List<ProductImage> deepFindProductImagesByProductId(@Param("id") long id);

@Modifying
@Query("DELETE FROM ProductImage pi WHERE pi.product.id = :id")
int deleteByProductId(@Param("id") long id);
```

- `deepFindProductImagesByProductId` is used by `readOne` only (§4.3) — the `JOIN FETCH`
  is what makes the images resolvable after the session closes.
- **`deleteByProductId` carries `@Modifying` but no `@Transactional`**, so it relies on
  the caller's transaction. Its sole caller is `update` (§4.2), which is `@Transactional`
  at class level, so the bulk delete participates in that transaction. A rewrite that
  calls it outside a transaction changes the failure semantics of `PUT`.
- `findByProductId` has no production caller.

### 2.3 `ProductsCategoriesRepository` — read cross-domain

Owned by `product-category` and fully specified in that domain's `02` §2. The product
converter uses exactly one of its methods, `findByCode`, for category resolution
(§5.2). Both domains move into the same service, so this read needs no contract seam.

---

## 3. `ProductsCrudServiceImpl` — shape

`@Transactional @Service`, `extends CrudGenericService<ProductPojo, Product> implements
ProductsCrudService`.

Constructor passes `productsRepository`, `productsConverterService` and
`productsPatchService` to `super`, and retains `productsRepository`,
`productsConverterService`, `productImagesRepository` and `imagesCrudService` as fields.
Holds `Logger logger = LoggerFactory.getLogger(ProductsCrudServiceImpl.class)`.

It is the only CRUD service in the catalogue with **four overrides** — `create`,
`update(M, Long)`, `readOne` and `getExisting` — all four because of images.

Inherited literals from the base:
```java
protected static final String ITEM_NOT_FOUND = "Requested item(s) not found";
protected static final String ITEM_ALREADY_EXISTS = "The item already exists";
```

---

## 4. The four CRUD overrides

### 4.1 `create(ProductPojo input)`

`@Transactional @Override public ProductPojo create(ProductPojo input) throws BadInputException, EntityExistsException`

1. `this.validateInputPojoBeforeCreation(input)` — base implementation: if
   `getExisting(input).isPresent()` → **`throw new EntityExistsException("The item already exists")`**
   (BR-PRODUCT-005).
2. `convertToNewEntity(input)`.
3. `productsRepository.saveAndFlush(prepared)`.
4. `convertToPojo(persistent)` — the response body.
5. If `input.getImages() != null && !isEmpty(...)`:
   `makeProductImageRelationships(persistent, images)` → `productImagesRepository.saveAll(...)`
   → `convertImagesToPojo(...)` → `target.setImages(...)`.
6. Return `target`.

Only the **barcode** is pre-checked in step 1; a `name` collision reaches step 3 and
fails at the database as a 500.

### 4.2 `update(ProductPojo input, Long id)`

`@Override public Optional<ProductPojo> update(ProductPojo input, Long id) throws EntityNotFoundException, BadInputException`

1. `convertToNewEntity(input)`; `prepared.setId(id)`.
2. `productsRepository.saveAndFlush(prepared)` — **full replacement.** Every field absent
   from the input is written as null or primitive 0 (BR-PRODUCT-018, W4).
3. `convertToPojo(persistent)`.
4. **`productImagesRepository.deleteByProductId(id)`** — all links deleted wholesale,
   **unconditionally**, before any check of whether the payload carries images
   (BR-PRODUCT-011, W3).
5. If the input images are non-empty, rebuild exactly as in `create` step 5.
6. **`return Optional.of(target)` — never empty.**

Step 6 matters for the error contract: because this method never returns empty, the
controller's `"No element was found to update"` can only originate from the **predicate
lookup** in `update(M, Predicate)`, never from here. The two declared exceptions are
never thrown by this body.

**The ordering of steps 2 and 4 is observable.** The product row is replaced first, so a
failure during the image rebuild leaves the product updated and its images deleted.
There is no compensating action; the surrounding `@Transactional` is the only protection.

### 4.3 `readOne(Predicate filters)`

`@Override public ProductPojo readOne(Predicate filters) throws EntityNotFoundException`

1. `productsRepository.findOne(filters)`.
2. If empty → **`throw new EntityNotFoundException("Requested item(s) not found")`**.
3. `convertToPojo(found)`.
4. `productImagesRepository.deepFindProductImagesByProductId(found.getId())`.
5. `convertImagesToPojo(...)` → `target.setImages(...)`; return.

**This is the only read path that resolves images** (BR-PRODUCT-016). `readMany` is not
overridden and therefore never populates them.

### 4.4 `getExisting(ProductPojo input)`

```java
String barcode = input.getBarcode();
if (StringUtils.isBlank(barcode)) {
    throw new BadInputException("Invalid product barcode");
} else {
    return productsRepository.findByBarcode(barcode);
}
```

Exact message: **`"Invalid product barcode"`** (BR-PRODUCT-003). The barcode — not the
id, not the name — is the existence key (BR-PRODUCT-002).

**This method is the semantic core of the Gate A `ProductLookupContract`**, which the
order domain calls once **per order line** during checkout. Its behaviour, including the
blank-barcode exception and the message text, is cross-service contract from Phase 3
onward.

### 4.5 Inherited CRUD behaviour, not overridden

| Operation | Behaviour |
|---|---|
| `readMany(int, int, Sort, Predicate)` | Counts, then `findAll`, then `convertToPojo` per entity. **List reads never include images.** |
| `update(M input)` | **`throw new UnsupportedOperationException("This method signature has been deprecated")`** — unconditional. |
| `update(M, Predicate)` | `count > 1` → **`throw new RuntimeException("Cannot update more than one item at a time")`** (BR-PRODUCT-031); `findOne` empty → `Optional.empty()`; else delegates to the overridden `update(input, id)`. |
| `partialUpdate(Map, Long)` | Loads by id, delegates to `flushPartialChanges`. A `BadInputException` from below is rewrapped as `new RuntimeException(e)`. |
| `partialUpdate(Map, Predicate)` | Same single-row guard, same message. |
| `flushPartialChanges(Map, entity)` | `patchService.patchExistingEntity(changes, existing)`; if `existingEntity.equals(updatedEntity)` return the original **with no write**; else `saveAndFlush`. Depends on the all-fields equality of document `01` §1.6. **Not overridden here** — unlike the category domain, no parent/category restore happens at this level, because the patch service itself restores the category (§7.1). |
| `delete(Predicate)` | `count == 0` → **`throw new EntityNotFoundException("Requested item(s) not found")`**; else `repository.deleteAll(repository.findAll(filters))`. **Image links are not removed**, so the FK blocks the delete (BR-PRODUCT-028). |

---

## 5. `ProductsConverterServiceImpl`

`@Transactional @Service`. Injected: `ProductImagesRepository` (**held as a field but
never used in this class** — a dead dependency that ports as-is),
`ImagesConverterService`, `ProductsCategoriesRepository`,
`ProductCategoriesConverterService`.

Two of the four dependencies are cross-domain reads, **both inside the catalogue**.

### 5.1 `convertToPojo(Product source)`

1. Builder: `name`, `barcode`, `price`, `description`,
   `currentStock = source.getStockCurrent()`, `criticalStock = source.getStockCritical()`
   — the three-way field renaming of document `01` §3.2.
2. If `source.getProductCategory() != null` →
   `productCategoriesConverterService.convertToPojo(category)` → `target.setCategory(...)`.
3. **Images are never populated here** (BR-PRODUCT-016) — only `readOne` fills them.
4. `id` is not carried, because the pojo has no id field.

No exception path.

### 5.2 `convertToNewEntity(ProductPojo source)`

1. Builder: `name`, `barcode`, `price`, `description`. **`price` is unboxed from
   `Integer`**, so a null would NPE — guarded only by `@NotNull` at the controller, which
   means any non-HTTP caller can trigger it.
2. `if (source.getCurrentStock() != null) target.setStockCurrent(...)` — otherwise the
   primitive default **0** stands.
3. `if (source.getCriticalStock() != null) target.setStockCritical(...)` — same.
4. If `sourceCategory != null && !StringUtils.isBlank(sourceCategory.getCode())` →
   `productsCategoriesRepository.findByCode(code).ifPresent(target::setProductCategory)`.
5. The entity `id` is never set here — the caller does it on the update path.

**Category resolution is fail-silent in every failing case** (BR-PRODUCT-010, W5):

| Input | Result |
|---|---|
| `category` absent | Uncategorised. Intended. |
| `category.code` blank | Guard skips the lookup entirely → uncategorised, no error. |
| `category.code` unknown | `findByCode` empty → `ifPresent` no-op → **uncategorised, HTTP 201, no error.** |

A category is **only ever looked up, never created**. There is no auto-provisioning,
unlike the billing-company resolve-or-create in the order domain.

### 5.3 `convertImagesToPojo(Collection<ProductImage> productImages)`

```java
productImages.stream()
    .map(ProductImage::getImage)
    .map(imagesConverterService::convertToPojo)
    .distinct()
    .collect(toList());
```

**`distinct()` collapses duplicate links on read**, which is what hides the missing
`(product_id, image_id)` unique constraint (BR-PRODUCT-015, W10). The equality used is
`ImagePojo`'s, i.e. all three fields.

### 5.4 `applyChangesToExistingEntity(ProductPojo source, Product target)`

**`throw new UnsupportedOperationException("This method is deprecated")`** —
unconditional. Note the text differs from the patch service's (§7.2); both port verbatim.

---

## 6. The product → image write path

```java
private List<ProductImage> makeProductImageRelationships(Product existingProduct, Collection<ImagePojo> inputImages)
```

Javadoc: *"Creates transient instances of the ProductImages entity. Does NOT persist
said instances."* The caller persists them with `saveAll`.

Per input `ImagePojo img`:

1. `try { Optional<Image> match = imagesCrudService.getExisting(img); }`
2. ```java
   Image image = match.orElseGet(() -> Image.builder()
       .code(img.getCode()).filename(img.getFilename()).url(img.getUrl()).build());
   ```
   **A brand-new transient `Image` is built inline, bypassing `ImagesCrudService.create`
   entirely** — no `validateInputPojoBeforeCreation`, no `EntityExistsException` on a
   duplicate, no bean validation of `ImagePojo` (the parent collection carries no
   cascading `@Valid`), no normalization, and no `images:create` authority check
   (BR-PRODUCT-014, W1).
3. `ProductImage relationship = ProductImage.builder().product(existingProduct).image(image).build();`
4. `allRelationships.add(relationship);`
5. ```java
   catch (BadInputException ex) {
       logger.debug("An image was not linked to product with barcode '{}'", existingProduct.getBarcode());
   }
   ```
   **The image is silently skipped and the whole request still succeeds** — logged at
   **DEBUG**, which is below the default level, so in production there is no trace at all
   (BR-PRODUCT-013, W2).
6. Returns the accumulated list.

**The blank-filename case, end to end.** `ImagesCrudServiceImpl.getExisting` does
`if (StringUtils.isBlank(name)) throw new BadInputException("Invalid filename");`. That
exception is caught in step 5, the image is dropped, and `POST`/`PUT` returns 201/204 as
if nothing happened.

**One Phase 0 change touches this path.** The D7 fix made
`ImagesCrudServiceImpl.getExisting` check filename, then `code`, then `url`, first match
wins. So a payload reusing an existing `code` or `url` now **resolves to the existing
`Image`** where it previously fell through to `orElseGet` and produced a second row that
collided at the database. That is an intentional, already-ruled behaviour change; it
ports with the fix in place.

**No duplicate check across the input collection**, so the same image appearing twice in
one payload yields two `product_images` rows (W10).

Since no cascade is declared on `ProductImage.image`, persistence of a genuinely new
`Image` depends on the surrounding transaction's reachability and flush behaviour — it
surfaces as a placeholder row rather than an error (BR-PRODUCT-014).

---

## 7. `ProductsPatchServiceImpl`

`@Service @NoArgsConstructor` — no dependencies, pure in-memory transformation.

### 7.1 `patchExistingEntity(Map<String, Object> changes, Product existing)`

Declared `throws BadInputException`, but **never actually throws it**.

1. `Product target = new Product(existing);` — the clone, **with `productCategory`
   dropped to null**.
2. `target.setProductCategory(existing.getProductCategory());` — **the category is
   restored.** This is the mechanism of BR-PRODUCT-017: **a patch can never declassify a
   product.** Note this restore lives in the *patch service* here, whereas the category
   domain does the equivalent restore in its *CRUD service* — the two domains solve the
   same problem at different layers, and a rewrite that unifies them must keep both
   effects.
3. `barcode` — applied only if the key is present and `!StringUtils.isBlank(value)`.
4. `name` — same blank guard.
5. **`price` — applied unconditionally when the key is present:**
   `target.setPrice((Integer) changes.get("price"))`. A null value is an **NPE on
   unboxing**; a non-integer is a **`ClassCastException`**. Both are 500s (W9).
6. `description` — blank-guarded.
7. **`currentStock` — unconditional cast and set**, same hazards as `price`.
8. **`criticalStock` — unconditional cast and set**, same hazards.
9. Return `target`.

**The asymmetry in steps 3–8 is the rule:** the three string fields ignore blanks and
never clear a stored value (BR-PRODUCT-024), while the three numeric fields are set
unconditionally (BR-PRODUCT-023) and are the only patchable route to a negative price or
negative stock.

**Not patchable at all:** `images` (BR-PRODUCT-012) and `category`
(BR-PRODUCT-017/BR-PRODUCT-018). Unknown keys are ignored without error.

### 7.2 `patchExistingEntity(ProductPojo changes, Product existing)`

**`throw new UnsupportedOperationException("This method signature has been deprecated")`**
— unconditional. Text differs from §5.4; preserve both exactly.

---

## 8. `ProductsPredicateServiceImpl`

`@Service`; injects `ProductCategoryTreeResolverService categoryTreeResolver`; logger
named for the class.

### 8.1 `parseMap(Map<String, String> queryParamsMap)`

Builds a `BooleanBuilder`, iterating entries and switching on the parameter name. **The
accepted names are contract.**

| Parameter | Behaviour |
|---|---|
| `id` | **Returns immediately** with `basePath.id.eq(Long.valueOf(v))` — all other parameters discarded. |
| `barcode` | **Returns immediately** with `basePath.barcode.eq(v)`. |
| `name` | **Returns immediately** with `basePath.name.eq(v)`. |
| `barcodeLike` | `and(basePath.barcode.likeIgnoreCase("%" + v + "%"))` — value **not escaped**, so `%` and `_` act as wildcards. |
| `nameLike` | `and(basePath.name.likeIgnoreCase("%" + v + "%"))` — same. |
| `categoryCode` | `List<Long> branchIds = categoryTreeResolver.getBranchIdsFromRootCode(v);` then `and(basePath.productCategory.code.eq(v).or(basePath.productCategory.id.in(branchIds)))` — **the whole branch**, not just the named category (BR-PRODUCT-019, cross-domain feature CDF-05). |
| `categoryCodeLike` | `and(basePath.productCategory.code.likeIgnoreCase("%" + v + "%"))`. |
| anything else | `default: break` — ignored silently. |

**Three identity parameters short-circuit here, versus two in the category domain.**

`catch (NumberFormatException exc)` →
`logger.info("Param '{}' couldn't be parsed as number (value: '{}')", paramName, stringValue)`
and the loop continues: a malformed numeric filter is **silently dropped**, widening the
result set rather than returning 400.

The returned `BooleanBuilder` may carry no condition — a match-everything predicate. On
reads that is a full listing; on the delete path the Phase-0 D1 guard rejects it (§10.3).

**`categoryCode` is the only filter that performs database work during predicate
construction** — the recursive branch walk, one query per node (see the category
domain's `02` §8.2 for its cost and its missing cycle detection). A product search by
category therefore inherits the category tree's N+1 behaviour and its depth bound.

---

## 9. `ProductsSortSpec`

```java
@NoArgsConstructor(access = PRIVATE)
public class ProductsSortSpec {
    private static final QProduct BASE_PATH = QProduct.product;
    public static final Map<String, OrderSpecifier<?>> ORDER_SPEC_MAP = Map.of(
        "name",     BASE_PATH.name.asc(),
        "barcode",  BASE_PATH.barcode.asc(),
        "price",    BASE_PATH.price.asc(),
        "category", BASE_PATH.productCategory.name.asc()
    );
}
```

**Exactly four accepted sort keys.** `id`, `currentStock`, `criticalStock` and
`description` are **not sortable**. Direction is baked in as `.asc()`; the shared
`SortSpecParserService` flips it for a descending request. Sorting by `category` orders
by the category **name**, and because the underlying join is on a nullable FK, the
placement of uncategorised products depends on the database's null ordering — not
specified anywhere, and preserved by porting the same expression.

This whitelist is the **mitigation evidence for the QueryDSL CVE** (register item D21):
`orderBy` only ever receives an `OrderSpecifier` from this map, so no raw client string
reaches it.

---

## 10. `DataProductsController`

`@RestController`, `@RequestMapping("/data/products")`,
`@Tag(name = "Products management")`; extends
`DataCrudGenericController<ProductPojo, Product>`. `@Autowired` constructor injects
`PaginationService`, `SortSpecParserService`, `ProductsCrudService` and
`ProductsPredicateService`, all passed to `super`.

Authority constants are statically imported from
`org.trebol.security.authorities.AuthorityCodes`: `PRODUCTS_CREATE`, `PRODUCTS_UPDATE`,
`PRODUCTS_DELETE` (Phase 0, Gate B — no raw literals remain).

### 10.1 Route table

| Method | Mapping | Response status | `@PreAuthorize` | Signature |
|---|---|---|---|---|
| `readMany` | `@GetMapping` → `GET /data/products` | 200 | **none — publicly reachable** | `DataPagePojo<ProductPojo> readMany(@RequestParam Map<String,String> allRequestParams)` |
| `create` | `@PostMapping` | `201 CREATED` | `hasAuthority('` + `PRODUCTS_CREATE` + `')` | `void create(@Valid @RequestBody ProductPojo input) throws BadInputException, EntityExistsException` |
| `update` | `@PutMapping` | `204 NO_CONTENT` | `PRODUCTS_UPDATE` | `void update(@Valid @RequestBody ProductPojo input, @RequestParam Map<String,String> requestParams) throws BadInputException, EntityNotFoundException` |
| `partialUpdate` | `@PatchMapping` | `204 NO_CONTENT` | `PRODUCTS_UPDATE` | `void partialUpdate(@RequestBody Map<String,Object> input, @RequestParam Map<String,String> requestParams) throws BadInputException, EntityNotFoundException` — **no `@Valid`** |
| `delete` | `@DeleteMapping` | `204 NO_CONTENT` | `PRODUCTS_DELETE` | `void delete(@RequestParam Map<String,String> requestParams) throws BadInputException, EntityNotFoundException` |

`@Override protected Map<String, OrderSpecifier<?>> getOrderSpecMap()` returns
`ProductsSortSpec.ORDER_SPEC_MAP`.

**All five write responses are `void`.** `create` returns 201 with **no body**, so a
client never learns the generated id — consistent with the pojo having no id field.

`PRODUCTS_READ` (`"products:read"`) is seeded in the catalogue but **enforced nowhere**
(BR-PRODUCT-025, BR-PRODUCT-026). See `permissions.md`.

### 10.2 `readMany` has no empty-parameter rewrite

Unlike `DataProductCategoriesController`, this controller does **not** substitute a
default filter. A bare `GET /data/products` therefore returns the **whole catalogue**,
paged — which is the storefront's main query and the reason for the `product_name` index.

### 10.3 Inherited write behaviour (`DataCrudGenericController`)

```java
public static final String NO_RECOGNIZED_FILTERING_PARAMS = "No recognized filtering parameters were provided";
```

- `update` / `partialUpdate`: if `requestParams.isEmpty()` →
  **`throw new BadInputException("Missing request params")`**; then
  `crudService.update(input, predicate)` /
  `partialUpdate(...)`, with
  `orElseThrow(() -> new EntityNotFoundException("No element was found to update"))`.
- `delete`: acts only when `!requestParams.isEmpty()` — **an empty parameter map is a
  silent no-op returning 204**. Otherwise, if the predicate is a `BooleanBuilder` with
  `!hasValue()` → **`throw new BadInputException(NO_RECOGNIZED_FILTERING_PARAMS)`** (the
  Phase-0 D1 fix), else `crudService.delete(predicate)`.

**Writes are addressed by query parameters.** The three short-circuiting identity
parameters, the unescaped `like` metacharacters and the silently dropped malformed
numerics therefore apply to `PUT`, `PATCH` and `DELETE`, not only to searches. There is
no path-identifier route form.

---

## 11. Test inventory (G8 — these port with the code)

| Test class | Mocks / helpers |
|---|---|
| `api/controllers/DataProductsControllerTest` | the four injected services |
| `jpa/services/conversion/impl/ProductsConverterServiceImplTest` | `ProductsTestHelper`, `ImagesTestHelper`, `ProductCategoriesTestHelper` |
| `jpa/services/crud/impl/ProductsCrudServiceImplTest` | `ProductsTestHelper`, `ImagesTestHelper`, mocks both repositories, the converter, the patch service and `ImagesCrudService` |
| `jpa/services/patch/impl/ProductsPatchServiceImplTest` | `ProductsTestHelper` |
| `jpa/services/predicates/impl/ProductsPredicateServiceImplTest` | mocks `ProductCategoryTreeResolverService` |
| `api/services/impl/CompanyServiceImplTest` | mocks `ParamsRepository` (appendix) |
| `api/controllers/PublicAboutControllerTest` | mocks `CompanyService` (appendix) |

Shared helpers that must port with them: `org/trebol/testing/ProductsTestHelper`
(constants `PRODUCT_ID = 1L`, `PRODUCT_NAME = "test product name"`,
`PRODUCT_BARCODE = "TESTPROD1"`, `PRODUCT_DESCRIPTION = "test product description"`,
`PRODUCT_PRICE = 100`, `PRODUCT_STOCK = 10`, `PRODUCT_STOCK_CRITICAL = 1`; factories
`productPojoForFetch()`, `productPojoBeforeCreationWithoutCategory()`,
`productPojoAfterCreationWithoutCategory()`,
`productEntityBeforeCreationWithoutCategory()`,
`productEntityAfterCreationWithoutCategory()`, `resetProducts()`),
`org/trebol/testing/ImagesTestHelper`, `org/trebol/testing/ProductCategoriesTestHelper`
and `org/trebol/testing/TestConstants` (`ANY = "ANY"`, `NOT_ANY = "NOT ANY"`, `ONE = 1`).
