# Sequence diagrams — Shipper (`shipper`)

## Public carrier listing (`GET /data/shippers`)

```mermaid
sequenceDiagram
    actor AnyCaller
    participant DataShippersController
    participant PaginationServiceImpl
    participant SortSpecParserServiceImpl
    participant ShippersPredicateServiceImpl
    participant ShippersCrudServiceImpl
    participant ShippersRepository
    participant ShippersConverterServiceImpl

    AnyCaller->>DataShippersController: GET /data/shippers?nameLike=exp
    Note over DataShippersController: no authority required,<br/>no authentication required
    DataShippersController->>PaginationServiceImpl: determineRequestedPageIndex / PageSize
    DataShippersController->>SortSpecParserServiceImpl: parse(ShippersSortSpec.ORDER_SPEC_MAP, params)
    DataShippersController->>ShippersPredicateServiceImpl: parseMap(params)
    DataShippersController->>ShippersCrudServiceImpl: readMany(pageIndex, pageSize, sort, filters)
    ShippersCrudServiceImpl->>ShippersRepository: count(filters)
    ShippersCrudServiceImpl->>ShippersRepository: findAll(filters, pageRequest)
    loop each Shipper
        ShippersCrudServiceImpl->>ShippersConverterServiceImpl: convertToPojo(shipper)
    end
    ShippersCrudServiceImpl-->>DataShippersController: DataPagePojo<ShipperPojo>
    DataShippersController-->>AnyCaller: 200 page envelope
```

## Register a carrier (`POST /data/shippers`)

```mermaid
sequenceDiagram
    actor Operator
    participant DataShippersController
    participant ShippersCrudServiceImpl
    participant ShippersRepository
    participant ShippersConverterServiceImpl

    Operator->>DataShippersController: POST /data/shippers (ShipperPojo)
    Note over DataShippersController: authority shippers:create<br/>name @NotBlank
    DataShippersController->>ShippersCrudServiceImpl: create(shipperPojo)
    ShippersCrudServiceImpl->>ShippersCrudServiceImpl: getExisting(pojo)
    alt name blank
        ShippersCrudServiceImpl-->>Operator: 400 REJECTED_01 "Billing type has no name" (defect)
    end
    ShippersCrudServiceImpl->>ShippersRepository: findByName(name)
    alt carrier exists
        ShippersCrudServiceImpl-->>Operator: 400 EXISTS_01 "The item already exists"
    else new carrier
        ShippersCrudServiceImpl->>ShippersConverterServiceImpl: convertToNewEntity(pojo)
        ShippersCrudServiceImpl->>ShippersRepository: saveAndFlush(shipper)
        ShippersCrudServiceImpl->>ShippersConverterServiceImpl: convertToPojo(saved)
        DataShippersController-->>Operator: 201 Created
    end
```

## Carrier resolution and reassignment driven by the order domain

```mermaid
sequenceDiagram
    participant OrdersConverterServiceImpl
    participant OrdersPatchServiceImpl
    participant ShippersRepository
    participant AddressesRepository
    participant Order as Order entity

    rect rgb(240,240,240)
    Note over OrdersConverterServiceImpl: order creation (checkout)
    OrdersConverterServiceImpl->>ShippersRepository: findByName(submitted shipper name)
    alt unknown carrier
        ShippersRepository-->>OrdersConverterServiceImpl: empty
        OrdersConverterServiceImpl-->>OrdersConverterServiceImpl: BadInputException<br/>"Specified shipper does not exist"
    else known carrier
        ShippersRepository-->>OrdersConverterServiceImpl: Shipper
        OrdersConverterServiceImpl->>AddressesRepository: findByFields(shipping address) or create
        OrdersConverterServiceImpl->>Order: set shipper + shippingAddress<br/>(both mapped updatable=false)
    end
    end

    rect rgb(245,245,245)
    Note over OrdersPatchServiceImpl: PATCH /data/orders with key "shipper"
    OrdersPatchServiceImpl->>ShippersRepository: findByName(value)
    alt unknown carrier
        ShippersRepository-->>OrdersPatchServiceImpl: empty
        Note over OrdersPatchServiceImpl: key discarded silently,<br/>no error, no log
    else known carrier
        OrdersPatchServiceImpl->>Order: setShipper(resolved)
    end
    end
```
