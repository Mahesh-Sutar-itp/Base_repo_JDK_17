# Sequence diagrams — Product List (`product-list`)

## Browse a list's contents (`GET /data/product_list_contents?listCode=...`)

```mermaid
sequenceDiagram
    actor Visitor
    participant DataProductListContentsController
    participant ProductListsRepository
    participant PaginationServiceImpl
    participant SortSpecParserServiceImpl
    participant ProductListItemsPredicateServiceImpl
    participant ProductListItemsRepository
    participant ProductListItemsConverterServiceImpl

    Visitor->>DataProductListContentsController: GET /data/product_list_contents?listCode=FEATURED&productNameLike=drill
    Note over DataProductListContentsController: PUBLIC: no authentication, no authority.<br/>This controller extends NEITHER generic<br/>controller base class.
    DataProductListContentsController->>DataProductListContentsController: fetchProductListByCode(requestParams)
    alt listCode blank or missing
        DataProductListContentsController-->>Visitor: 400 REJECTED_01 "listCode query param is required"
    end
    DataProductListContentsController->>ProductListsRepository: findByCode(listCode)
    alt list not found
        DataProductListContentsController-->>Visitor: 404 NOTFOUND_01 "Requested item(s) not found"
    end
    DataProductListContentsController->>PaginationServiceImpl: determineRequestedPageIndex / PageSize
    alt params contain "sortBy"
        DataProductListContentsController->>SortSpecParserServiceImpl: parse(ProductListItemsSortSpec.ORDER_SPEC_MAP, params)
        Note over DataProductListContentsController: sorting applied ONLY when sortBy is present
    end
    DataProductListContentsController->>ProductListItemsPredicateServiceImpl: parseMap(params)
    DataProductListContentsController->>ProductListItemsRepository: findAll(predicate, pagination)
    Note over ProductListItemsRepository: list and product associations are EAGER,<br/>so each product is loaded with its row
    loop each ProductListItem
        DataProductListContentsController->>ProductListItemsConverterServiceImpl: convertToPojo(item)
        Note over ProductListItemsConverterServiceImpl: returns a ProductPojo:<br/>membership ids are never exposed
    end
    DataProductListContentsController->>ProductListItemsRepository: count(list.id == resolved list id)
    Note over DataProductListContentsController: the TOTAL is the list's full size,<br/>NOT the filtered match count -<br/>items and total disagree on a filtered page
    DataProductListContentsController-->>Visitor: 200 DataPagePojo<ProductPojo>
```

## Add one product versus replace all contents

```mermaid
sequenceDiagram
    actor Operator
    participant DataProductListContentsController
    participant ProductListsRepository
    participant ProductsCrudServiceImpl
    participant ProductListItemsRepository

    rect rgb(240,248,240)
    Note over Operator,ProductListItemsRepository: POST - add one product (body IS validated)
    Operator->>DataProductListContentsController: POST /data/product_list_contents?listCode=FEATURED (ProductPojo)
    Note over DataProductListContentsController: authority product_lists:contents
    DataProductListContentsController->>ProductListsRepository: findByCode(listCode)
    DataProductListContentsController->>ProductsCrudServiceImpl: getExisting(productPojo)
    alt product not resolvable
        ProductsCrudServiceImpl-->>DataProductListContentsController: empty
        Note over DataProductListContentsController: SILENTLY SKIPPED
        DataProductListContentsController-->>Operator: 201 Created - NOTHING WAS ADDED
    else resolvable
        DataProductListContentsController->>ProductListItemsRepository: exists(Example.of(listItem))
        alt equivalent row already present
            Note over DataProductListContentsController: save skipped, no signal
        else new membership
            DataProductListContentsController->>ProductListItemsRepository: save(listItem)
        end
        DataProductListContentsController-->>Operator: 201 Created
    end
    end

    rect rgb(255,245,245)
    Note over Operator,ProductListItemsRepository: PUT - replace all contents (collection body NOT validated)
    Operator->>DataProductListContentsController: PUT /data/product_list_contents?listCode=FEATURED (collection)
    DataProductListContentsController->>ProductListsRepository: findByCode(listCode)
    DataProductListContentsController->>ProductListItemsRepository: deleteByListId(list id)
    Note over ProductListItemsRepository: DESTRUCTIVE FIRST:<br/>every membership row is removed
    loop each submitted ProductPojo
        DataProductListContentsController->>ProductsCrudServiceImpl: getExisting(p)
        alt not resolvable
            Note over DataProductListContentsController: silently skipped
        else resolvable
            DataProductListContentsController->>ProductListItemsRepository: exists + save
        end
    end
    Note over DataProductListContentsController: an empty collection - or one whose<br/>products ALL fail to resolve - leaves<br/>the list EMPTY, reported as success
    DataProductListContentsController-->>Operator: 204 No Content
    end
```

## Delete a list with membership cleanup (`DELETE /data/product_lists`)

```mermaid
sequenceDiagram
    actor Operator
    participant DataProductListsController
    participant ProductListsPredicateServiceImpl
    participant ProductListsCrudServiceImpl
    participant ProductListsRepository
    participant ProductListItemsRepository

    Operator->>DataProductListsController: DELETE /data/product_lists?code=FEATURED
    Note over DataProductListsController: authority product_lists:delete
    alt request params empty
        DataProductListsController-->>Operator: 204, nothing deleted
    end
    DataProductListsController->>ProductListsPredicateServiceImpl: parseMap(params)
    Note over ProductListsPredicateServiceImpl: no "id" key exists:<br/>selection is always by code or name
    DataProductListsController->>ProductListsCrudServiceImpl: delete(filters)
    ProductListsCrudServiceImpl->>ProductListsRepository: count(filters)
    alt count == 0
        ProductListsCrudServiceImpl-->>Operator: 404 NOTFOUND_01 "Requested item(s) not found"
    else matched
        loop each matched ProductList
            ProductListsCrudServiceImpl->>ProductListItemsRepository: deleteByListId(list.getId())
            Note over ProductListItemsRepository: PRE-DELETE CLEANUP of the child table -<br/>the ONLY domain in the system that does this
        end
        ProductListsCrudServiceImpl->>ProductListsRepository: deleteAll(matched lists)
        DataProductListsController-->>Operator: 204 No Content
    end
```

## Product deletion shrinking lists (cross-domain, database-driven)

```mermaid
sequenceDiagram
    participant ProductsCrudServiceImpl
    participant ProductsRepository
    participant Database
    participant ProductListItemsRepository

    ProductsCrudServiceImpl->>ProductsRepository: deleteAll(matched products)
    ProductsRepository->>Database: DELETE FROM products ...
    Database->>Database: ON DELETE CASCADE on<br/>product_list_items.product_id
    Note over Database: every membership row for that product<br/>is removed from EVERY list, silently.<br/>The only cascade-on-delete rule in the<br/>entire schema.
    ProductsCrudServiceImpl--xProductListItemsRepository: deleteByProductId(id)
    Note over ProductListItemsRepository: this application-level equivalent EXISTS<br/>and is NEVER called - a rewrite that<br/>splits databases must implement it,<br/>because the cascade will not survive<br/>a service boundary
```
