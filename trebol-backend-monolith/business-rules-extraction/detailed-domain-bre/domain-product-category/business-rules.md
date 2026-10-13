# Business Rules — Product Category Tree (`product-category`)

## BR-PRODCAT-001 Category codes are globally unique

`product_categories.product_category_code` carries UNIQUE KEY
`UK_PROD_CAT_CODE`. Asserting: two categories anywhere in the tree cannot share a
code, regardless of depth or parent.
Source: `jpa/entities/ProductCategory.java`,
`database-documentation/00-global-schema.sql`.

## BR-PRODCAT-002 Category names are unique only among siblings

The uniqueness constraint `UK_PROD_CAT_IF_CHILD` is composite over
`(parent_product_category_id, product_category_name)`. Asserting: two categories
named `Accessories` may coexist under different parents, but not under the same
parent. Because a null parent participates in the composite key, the uniqueness
guarantee at the root level depends on the database's null-handling in unique
indexes and is not enforced by application code.
Source: `jpa/entities/ProductCategory.java`,
`database-documentation/00-global-schema.sql`.

## BR-PRODCAT-003 The code is the business key for existence checks

`ProductCategoriesCrudServiceImpl.getExisting` resolves exclusively through
`ProductsCategoriesRepository.findByCode`. Asserting: creating a category with a
new code but a sibling-colliding name passes the service check and fails at the
composite database constraint instead.
Source: `jpa/services/crud/impl/ProductCategoriesCrudServiceImpl.java`.

## BR-PRODCAT-004 A blank category code is rejected at the service layer

`getExisting` throws a bad-input error with the literal message
`Invalid category code`.
Source: `jpa/services/crud/impl/ProductCategoriesCrudServiceImpl.java`.

## BR-PRODCAT-005 Code and name are both mandatory on the transport model

`ProductCategoryPojo` marks `code` and `name` `@NotBlank`. The `parent` field,
which is itself a category transport model, carries no nested-validation marker,
so a submitted parent is never validated.
Source: `api/models/ProductCategoryPojo.java`.

## BR-PRODCAT-006 A patch can never detach a category from its parent

`ProductCategoriesCrudServiceImpl` overrides the flush step and re-attaches
`existingEntity.getParent()` onto the patched copy before saving. Asserting:
there is no request that moves a category to the root level or to a different
parent via `PATCH` — the parent is always restored to its stored value.
Source: `jpa/services/crud/impl/ProductCategoriesCrudServiceImpl.java`.

## BR-PRODCAT-007 The patch service itself ignores parent changes

`ProductCategoriesPatchServiceImpl` applies only `code` and `name`, each when
non-blank. There is no `parent` key. Asserting: re-parenting is impossible
through the patch surface at two independent layers.
Source: `jpa/services/patch/impl/ProductCategoriesPatchServiceImpl.java`.

## BR-PRODCAT-008 Re-parenting is only possible through full replacement

The generic replace path converts the body into a new entity (including whatever
parent the body carries) and assigns the existing identifier. Asserting: `PUT` is
the only way to change a category's position in the tree.
Source: `jpa/services/crud/CrudGenericService.java`.

## BR-PRODCAT-009 Nothing prevents a category cycle

No code path checks whether a submitted parent is the category itself or one of
its own descendants. Asserting: a `PUT` that sets a category's parent to its own
child creates a cycle, which the database accepts (the self-referencing foreign
key permits it) and which the recursive resolver will then traverse until the
depth limit stops it.
Source: absence of any ancestry check in
`jpa/services/crud/impl/ProductCategoriesCrudServiceImpl.java` and
`jpa/services/impl/ProductCategoryTreeResolverServiceImpl.java`.

## BR-PRODCAT-010 Recursive descendant resolution is depth-bounded

The tree resolver limits recursion using
`trebol.api.max-category-fetching-recursion-depth` (default 20). Asserting: a
hierarchy deeper than the configured limit silently returns a truncated
descendant set rather than failing — a category-filtered product search would
then miss products in the deepest branches.
Source: `jpa/services/impl/ProductCategoryTreeResolverServiceImpl.java`,
`config/ApiProperties.java`.

## BR-PRODCAT-011 An unresolvable root code yields no filter rather than no results

`getBranchIdsFromRootCode` returns an empty list when the code resolves to
nothing, and the product-category predicate service applies the descendant
condition **only if the list is non-empty**. Asserting:
`?rootCode=DOES_NOT_EXIST` returns the **unfiltered** page, not an empty page.
Source: `jpa/services/impl/ProductCategoryTreeResolverServiceImpl.java`,
`jpa/services/predicates/impl/ProductCategoriesPredicateServiceImpl.java`.

## BR-PRODCAT-012 A blank `parentId` means "root categories only"

The predicate service treats a blank `parentId` value as a null-parent
condition. Asserting: `?parentId=` (empty value) lists exactly the top-level
categories, whereas omitting the key entirely lists everything.
Source: `jpa/services/predicates/impl/ProductCategoriesPredicateServiceImpl.java`.

## BR-PRODCAT-013 Identity filters discard all other filters

`id` and `code` both return immediately from the predicate service.
Source: `jpa/services/predicates/impl/ProductCategoriesPredicateServiceImpl.java`.

## BR-PRODCAT-014 The category tree is world-readable

`DataProductCategoriesController` declares no authentication requirement and its
read method no authority requirement. Asserting: the full hierarchy is
anonymously enumerable — intended, since it is storefront navigation data.
Source: `api/controllers/DataProductCategoriesController.java`.

## BR-PRODCAT-015 The seeded `product_categories:read` authority is never enforced

It exists and is granted to Administrator, Manager and Salesperson, but no code
path checks it.
Source: `database-documentation/00-seed-data.json`,
`api/controllers/DataProductCategoriesController.java`.

## BR-PRODCAT-016 Deleting a category with children or products is blocked by the database

`FK_PROD_CAT_PARENT` (self-referencing) and `FK_PRODUCT_CAT_ID` (from `products`)
both have no cascade, and the domain performs no pre-delete cleanup. Asserting:
deleting a category that has any child category or any product aborts on a
constraint and surfaces as a 500-class error. The `orphanizeByCategories`
repository operation that would make this safe exists but is never called.
Source: `database-documentation/00-global-schema.sql`,
`jpa/repositories/ProductsRepository.java`,
absence of any caller of `orphanizeByCategories`.

## BR-PRODCAT-017 A product may have no category

`products.product_category_id` is nullable and `Product.productCategory` has no
`optional=false`. Asserting: classification is optional for products.
Source: `jpa/entities/Product.java`,
`database-documentation/00-global-schema.sql`.

## BR-PRODCAT-018 Category-filtered product search includes the whole branch

The product predicate service combines an exact category-code match with a
descendant-identifier match. Asserting: searching products by a parent category's
code returns products classified in any descendant category too, bounded by
BR-PRODCAT-010.
Source: `jpa/services/predicates/impl/ProductsPredicateServiceImpl.java`.

## BR-PRODCAT-019 Blank values never clear a stored code or name

`ProductCategoriesPatchServiceImpl` applies both fields only when non-blank.
Source: `jpa/services/patch/impl/ProductCategoriesPatchServiceImpl.java`.

## BR-PRODCAT-020 Update and patch are strictly single-row

Empty selecting parameters → `Missing request params`; more than one match →
`Cannot update more than one item at a time`; no match →
`No element was found to update`.
Source: `api/DataCrudGenericController.java`,
`jpa/services/crud/CrudGenericService.java`.
