# Sequence diagrams — Salesperson (`salesperson`)

## Register a salesperson (`POST /data/salespeople`)

```mermaid
sequenceDiagram
    actor Operator
    participant DataSalespeopleController
    participant SalespeopleCrudServiceImpl
    participant SalespeopleRepository
    participant SalespeopleConverterServiceImpl
    participant Person as Person row (cascade)

    Operator->>DataSalespeopleController: POST /data/salespeople (PersonPojo)
    Note over DataSalespeopleController: isAuthenticated() + salespeople:create<br/>body is a PERSON model:<br/>there is no SalespersonPojo
    DataSalespeopleController->>SalespeopleCrudServiceImpl: create(personPojo)
    SalespeopleCrudServiceImpl->>SalespeopleCrudServiceImpl: validateInputPojoBeforeCreation
    SalespeopleCrudServiceImpl->>SalespeopleRepository: findByPersonIdNumber(idNumber)<br/>(JOIN FETCH person)
    alt idNumber blank
        SalespeopleCrudServiceImpl-->>Operator: 400 REJECTED_01 "Salesperson does not have an ID card"
    else salesperson already exists
        SalespeopleCrudServiceImpl-->>Operator: 400 EXISTS_01 "The item already exists"
    else new salesperson
        SalespeopleCrudServiceImpl->>SalespeopleConverterServiceImpl: convertToNewEntity(personPojo)
        SalespeopleCrudServiceImpl->>SalespeopleRepository: saveAndFlush(salesperson)
        SalespeopleRepository->>Person: cascade PERSIST writes the people row<br/>in the SAME transaction
        SalespeopleCrudServiceImpl->>SalespeopleConverterServiceImpl: convertToPojo(saved)
        Note over SalespeopleConverterServiceImpl: returns a PersonPojo:<br/>the salesperson_id is never exposed
        DataSalespeopleController-->>Operator: 201 Created
    end
    Note over SalespeopleRepository: salespeople.person_id has NO unique constraint,<br/>so two concurrent creates for the same idNumber<br/>can BOTH pass the check and BOTH insert
```

## Replace a salesperson (`PUT /data/salespeople`) — the only correction path

```mermaid
sequenceDiagram
    actor Operator
    participant DataSalespeopleController
    participant SalespeoplePredicateServiceImpl
    participant SalespeopleCrudServiceImpl
    participant SalespeopleConverterServiceImpl
    participant SalespeopleRepository

    Operator->>DataSalespeopleController: PUT /data/salespeople?idNumber=333333333<br/>(full PersonPojo)
    Note over DataSalespeopleController: authority salespeople:update.<br/>There is NO PATCH route, so every<br/>mandatory person field must be re-sent.
    alt request params empty
        DataSalespeopleController-->>Operator: 400 REJECTED_01 "Missing request params"
    end
    DataSalespeopleController->>SalespeoplePredicateServiceImpl: parseMap(requestParams)
    Note over SalespeoplePredicateServiceImpl: every condition traverses<br/>basePath.person
    DataSalespeopleController->>SalespeopleCrudServiceImpl: update(personPojo, filters)
    SalespeopleCrudServiceImpl->>SalespeopleRepository: count(filters)
    alt more than one match
        SalespeopleCrudServiceImpl-->>Operator: 500 "Cannot update more than one item at a time"
    else no match
        DataSalespeopleController-->>Operator: 404 NOTFOUND_01 "No element was found to update"
    else exactly one match
        SalespeopleCrudServiceImpl->>SalespeopleConverterServiceImpl: convertToNewEntity(personPojo)
        SalespeopleCrudServiceImpl->>SalespeopleCrudServiceImpl: assign the existing id
        SalespeopleCrudServiceImpl->>SalespeopleRepository: saveAndFlush
        Note over SalespeopleRepository: FULL OVERWRITE of the person data
        DataSalespeopleController-->>Operator: 204 No Content
    end
```

## The two unreachable paths

```mermaid
sequenceDiagram
    participant NoRoute as "(no PATCH route)"
    participant SalespeoplePatchServiceImpl
    participant Utils
    participant PeoplePatchServiceImpl
    participant OrdersConverterServiceImpl
    participant OrdersPatchServiceImpl
    participant Order as Order entity
    participant Salesperson as Salesperson row

    rect rgb(255,245,245)
    Note over NoRoute,PeoplePatchServiceImpl: unreachable path 1: partial update
    NoRoute--xSalespeoplePatchServiceImpl: patchExistingEntity(changes, salesperson)
    SalespeoplePatchServiceImpl->>Utils: copyMapWithUnprefixedEntries(changes, "person.")
    Note over SalespeoplePatchServiceImpl: prefix is a HARDCODED LITERAL here,<br/>unlike the customer patch service which<br/>uses the shared constant
    SalespeoplePatchServiceImpl->>PeoplePatchServiceImpl: patchExistingEntity(subMap, person)
    Note over PeoplePatchServiceImpl: fully implemented and wired as a bean -<br/>exposing it is a ROUTING change,<br/>not a logic change
    end

    rect rgb(245,245,255)
    Note over OrdersConverterServiceImpl,Salesperson: unreachable path 2: order attribution
    OrdersConverterServiceImpl--xOrder: no code sets order.salesperson at checkout
    OrdersPatchServiceImpl--xOrder: the patch service exposes no "salesperson" key
    Note over Order: orders.salesperson_id is therefore<br/>ALWAYS NULL in a shipped deployment
    Order -. "cascade PERSIST + MERGE exists" .-> Salesperson
    Note over Salesperson: if a route ever populated the field,<br/>order persistence would write this table<br/>WITHOUT passing the salesperson<br/>service's validation
    end
```
