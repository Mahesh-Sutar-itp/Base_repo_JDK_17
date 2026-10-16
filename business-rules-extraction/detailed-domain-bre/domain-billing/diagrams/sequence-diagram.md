# Sequence diagrams — Billing (`billing`)

## Billing-type listing (`GET /data/billing_types`)

```mermaid
sequenceDiagram
    actor Caller
    participant DataBillingTypesController
    participant PaginationServiceImpl
    participant SortSpecParserServiceImpl
    participant BillingTypesPredicateServiceImpl
    participant BillingTypesCrudServiceImpl
    participant BillingTypesRepository
    participant BillingTypesConverterServiceImpl

    Caller->>DataBillingTypesController: GET /data/billing_types
    Note over DataBillingTypesController: PUBLIC: no authentication requirement,<br/>no authority requirement,<br/>no route-matching rules in SecurityConfig
    DataBillingTypesController->>PaginationServiceImpl: determineRequestedPageIndex / PageSize
    DataBillingTypesController->>SortSpecParserServiceImpl: parse(BillingTypesSortSpec.ORDER_SPEC_MAP, params)
    DataBillingTypesController->>BillingTypesPredicateServiceImpl: parseMap(params)
    DataBillingTypesController->>BillingTypesCrudServiceImpl: readMany(pageIndex, pageSize, sort, filters)
    BillingTypesCrudServiceImpl->>BillingTypesRepository: count(filters) + findAll(filters, pageRequest)
    loop each BillingType
        BillingTypesCrudServiceImpl->>BillingTypesConverterServiceImpl: convertToPojo(billingType)
    end
    BillingTypesCrudServiceImpl-->>DataBillingTypesController: DataPagePojo<BillingTypePojo>
    DataBillingTypesController-->>Caller: 200 page envelope
```

## Enterprise-invoice billing resolution during checkout

```mermaid
sequenceDiagram
    participant OrdersConverterServiceImpl
    participant BillingTypesRepository
    participant BillingCompaniesRepository
    participant AddressesRepository
    participant Order as Order entity

    OrdersConverterServiceImpl->>OrdersConverterServiceImpl: convertBillingInformationForEntity(orderPojo, order)
    OrdersConverterServiceImpl->>BillingTypesRepository: findByName(submitted billing type)
    alt unknown billing type
        BillingTypesRepository-->>OrdersConverterServiceImpl: empty
        OrdersConverterServiceImpl-->>OrdersConverterServiceImpl: BadInputException<br/>"Specified billing type does not exist"
    else name == "Bill" (individual)
        BillingTypesRepository-->>OrdersConverterServiceImpl: BillingType(Bill)
        OrdersConverterServiceImpl->>Order: setBillingType(Bill)
        Note over Order: billing_company_id and<br/>billing_address_id stay NULL
    else name == "Enterprise Invoice"
        BillingTypesRepository-->>OrdersConverterServiceImpl: BillingType(Enterprise Invoice)
        OrdersConverterServiceImpl->>BillingCompaniesRepository: findByIdNumber(company idNumber)
        alt company exists
            BillingCompaniesRepository-->>OrdersConverterServiceImpl: BillingCompany
            Note over OrdersConverterServiceImpl: submitted company NAME is discarded
        else company unknown
            OrdersConverterServiceImpl->>BillingCompaniesRepository: save(new BillingCompany)
            Note over BillingCompaniesRepository: duplicate company name with a new<br/>idNumber aborts on UK_BILL_CY_NAME
        end
        OrdersConverterServiceImpl->>AddressesRepository: findByFields(billing address)
        alt address exists
            AddressesRepository-->>OrdersConverterServiceImpl: Address
        else address unknown
            OrdersConverterServiceImpl->>AddressesRepository: save(new Address)
        end
        OrdersConverterServiceImpl->>Order: setBillingType + setBillingCompany + setBillingAddress
    end
```

## Billing-company service stack (wired but unreachable)

```mermaid
sequenceDiagram
    participant NoController as "(no controller exists)"
    participant BillingCompaniesCrudServiceImpl
    participant BillingCompaniesPredicateServiceImpl
    participant BillingCompaniesPatchServiceImpl
    participant BillingCompaniesRepository

    Note over NoController: No class in api/controllers/ injects any<br/>billing-company bean, so none of the calls<br/>below is reachable over HTTP.
    NoController--xBillingCompaniesCrudServiceImpl: create / readMany / update / delete
    BillingCompaniesCrudServiceImpl->>BillingCompaniesRepository: findByIdNumber(idNumber)
    Note over BillingCompaniesCrudServiceImpl: blank idNumber -><br/>"Billing company has no id number"
    NoController--xBillingCompaniesPredicateServiceImpl: parseMap(id, idNumber, name, idNumberLike, nameLike)
    NoController--xBillingCompaniesPatchServiceImpl: patchExistingEntity(idNumber, name when non-blank)
```
