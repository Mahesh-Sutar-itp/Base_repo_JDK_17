# Sequence diagrams — Product Category Tree (`product-category`)

## Whole-branch category browsing (`GET /data/product_categories?rootCode=...`)

```mermaid
sequenceDiagram
    actor Visitor
    participant DataProductCategoriesController
    participant ProductCategoriesPredicateServiceImpl
    participant ProductCategoryTreeResolverServiceImpl
    participant ProductsCategoriesRepository
    participant ProductCategoriesCrudServiceImpl
    participant ProductCategoriesConverterServiceImpl

    Visitor->>DataProductCategoriesController: GET /data/product_categories?rootCode=TOOLS
    Note over DataProductCategoriesController: public: no authentication,<br/>no authority
    DataProductCategoriesController->>ProductCategoriesPredicateServiceImpl: parseMap(params)
    ProductCategoriesPredicateServiceImpl->>ProductCategoryTreeResolverServiceImpl: getBranchIdsFromRootCode("TOOLS")
    ProductCategoryTreeResolverServiceImpl->>ProductsCategoriesRepository: findByCode("TOOLS")
    alt code unknown
        ProductsCategoriesRepository-->>ProductCategoryTreeResolverServiceImpl: empty
        ProductCategoryTreeResolverServiceImpl-->>ProductCategoriesPredicateServiceImpl: empty list
        Note over ProductCategoriesPredicateServiceImpl: condition SKIPPED entirely,<br/>response is UNFILTERED
    else code resolves
        ProductCategoryTreeResolverServiceImpl->>ProductsCategoriesRepository: findIdsByParentId(rootId)
        loop recursively per child, until<br/>trebol.api.max-category-fetching-recursion-depth
            ProductCategoryTreeResolverServiceImpl->>ProductsCategoriesRepository: findIdsByParentId(childId)
        end
        ProductCategoryTreeResolverServiceImpl-->>ProductCategoriesPredicateServiceImpl: descendant id list
        Note over ProductCategoriesPredicateServiceImpl: parent.id IN (descendants)
    end
    DataProductCategoriesController->>ProductCategoriesCrudServiceImpl: readMany(pageIndex, pageSize, sort, filters)
    ProductCategoriesCrudServiceImpl->>ProductsCategoriesRepository: count + findAll
    ProductCategoriesCrudServiceImpl->>ProductCategoriesConverterServiceImpl: convertToPojo(each)
    DataProductCategoriesController-->>Visitor: 200 page envelope
```

## Patch a category with parent preservation (`PATCH /data/product_categories`)

```mermaid
sequenceDiagram
    actor Operator
    participant DataProductCategoriesController
    participant ProductCategoriesPredicateServiceImpl
    participant ProductCategoriesCrudServiceImpl
    participant ProductCategoriesPatchServiceImpl
    participant ProductsCategoriesRepository

    Operator->>DataProductCategoriesController: PATCH /data/product_categories?code=TOOLS (change map)
    Note over DataProductCategoriesController: authority product_categories:update
    alt request params empty
        DataProductCategoriesController-->>Operator: 400 REJECTED_01 "Missing request params"
    end
    DataProductCategoriesController->>ProductCategoriesPredicateServiceImpl: parseMap(requestParams)
    DataProductCategoriesController->>ProductCategoriesCrudServiceImpl: partialUpdate(changes, filters)
    ProductCategoriesCrudServiceImpl->>ProductsCategoriesRepository: count(filters)
    alt more than one match
        ProductCategoriesCrudServiceImpl-->>Operator: 500 "Cannot update more than one item at a time"
    end
    ProductCategoriesCrudServiceImpl->>ProductsCategoriesRepository: findOne(filters)
    alt no match
        DataProductCategoriesController-->>Operator: 404 NOTFOUND_01 "No element was found to update"
    else matched
        ProductCategoriesCrudServiceImpl->>ProductCategoriesPatchServiceImpl: patchExistingEntity(changes, category)
        Note over ProductCategoriesPatchServiceImpl: applies code and name only when<br/>non-blank; NO parent key exists
        ProductCategoriesPatchServiceImpl-->>ProductCategoriesCrudServiceImpl: patched copy
        ProductCategoriesCrudServiceImpl->>ProductCategoriesCrudServiceImpl: re-attach existingEntity.getParent()
        alt patched equals existing
            ProductCategoriesCrudServiceImpl-->>DataProductCategoriesController: existing, no write
        else changed
            ProductCategoriesCrudServiceImpl->>ProductsCategoriesRepository: saveAndFlush(patched)
        end
        DataProductCategoriesController-->>Operator: 204 No Content
    end
```

## Branch-aware product search (cross-domain, consumed by `product`)

```mermaid
sequenceDiagram
    actor Visitor
    participant DataProductsController
    participant ProductsPredicateServiceImpl
    participant ProductCategoryTreeResolverServiceImpl
    participant ProductsCategoriesRepository
    participant ProductsCrudServiceImpl
    participant ProductsRepository

    Visitor->>DataProductsController: GET /data/products?categoryCode=TOOLS
    DataProductsController->>ProductsPredicateServiceImpl: parseMap(params)
    ProductsPredicateServiceImpl->>ProductCategoryTreeResolverServiceImpl: getBranchIdsFromRootCode("TOOLS")
    ProductCategoryTreeResolverServiceImpl->>ProductsCategoriesRepository: findByCode + recursive findIdsByParentId
    ProductCategoryTreeResolverServiceImpl-->>ProductsPredicateServiceImpl: descendant id list
    Note over ProductsPredicateServiceImpl: productCategory.code == "TOOLS"<br/>OR productCategory.id IN (descendants)
    DataProductsController->>ProductsCrudServiceImpl: readMany(..., filters)
    ProductsCrudServiceImpl->>ProductsRepository: count + findAll
    DataProductsController-->>Visitor: 200 products in the whole branch
```
