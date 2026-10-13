# Sequence diagrams — Customer (`customer`)

## Register a customer directly (`POST /data/customers`)

```mermaid
sequenceDiagram
    actor Operator
    participant DataCustomersController
    participant CustomersCrudServiceImpl
    participant CustomersRepository
    participant CustomersConverterServiceImpl
    participant Person as Person row (cascade)

    Operator->>DataCustomersController: POST /data/customers (PersonPojo)
    Note over DataCustomersController: isAuthenticated() + customers:create<br/>body is a PERSON model: there is no CustomerPojo
    DataCustomersController->>CustomersCrudServiceImpl: create(personPojo)
    CustomersCrudServiceImpl->>CustomersCrudServiceImpl: validateInputPojoBeforeCreation
    CustomersCrudServiceImpl->>CustomersRepository: findByPersonIdNumber(idNumber)<br/>(JOIN FETCH person)
    alt idNumber blank
        CustomersCrudServiceImpl-->>Operator: 400 REJECTED_01 "Customer does not have an ID card"
    else customer already exists
        CustomersCrudServiceImpl-->>Operator: 400 EXISTS_01 "The item already exists"
    else new customer
        CustomersCrudServiceImpl->>CustomersConverterServiceImpl: convertToNewEntity(personPojo)
        CustomersCrudServiceImpl->>CustomersRepository: saveAndFlush(customer)
        CustomersRepository->>Person: cascade PERSIST writes the people row<br/>in the SAME transaction
        CustomersCrudServiceImpl->>CustomersConverterServiceImpl: convertToPojo(saved)
        Note over CustomersConverterServiceImpl: returns a PersonPojo:<br/>the customer_id is never exposed
        DataCustomersController-->>Operator: 201 Created
    end
```

## Correct a customer's person data (`PUT /data/customers`) and the unreachable patch path

```mermaid
sequenceDiagram
    actor Operator
    participant DataCustomersController
    participant CustomersPredicateServiceImpl
    participant CustomersCrudServiceImpl
    participant CustomersPatchServiceImpl
    participant Utils
    participant PeoplePatchServiceImpl
    participant CustomersRepository

    Operator->>DataCustomersController: PUT /data/customers?idNumber=111111111<br/>(full PersonPojo)
    Note over DataCustomersController: authority customers:update.<br/>There is NO PATCH route: the patch service<br/>below is wired but unreachable, so every<br/>mandatory person field must be re-sent.
    alt request params empty
        DataCustomersController-->>Operator: 400 REJECTED_01 "Missing request params"
    end
    DataCustomersController->>CustomersPredicateServiceImpl: parseMap(requestParams)
    Note over CustomersPredicateServiceImpl: every condition traverses<br/>basePath.person
    DataCustomersController->>CustomersCrudServiceImpl: update(personPojo, filters)
    CustomersCrudServiceImpl->>CustomersRepository: count(filters)
    alt more than one match
        CustomersCrudServiceImpl-->>Operator: 500 "Cannot update more than one item at a time"
    end
    CustomersCrudServiceImpl->>CustomersRepository: findOne(filters)
    alt no match
        DataCustomersController-->>Operator: 404 NOTFOUND_01 "No element was found to update"
    else matched
        Note over CustomersCrudServiceImpl: FULL OVERWRITE: body becomes a new entity,<br/>existing id assigned, saveAndFlush
        rect rgb(255,245,245)
        Note over CustomersPatchServiceImpl,PeoplePatchServiceImpl: UNREACHABLE today - shown because the<br/>logic is implemented and wired as a bean
        CustomersCrudServiceImpl--xCustomersPatchServiceImpl: patchExistingEntity(changes, customer)
        CustomersPatchServiceImpl->>Utils: copyMapWithUnprefixedEntries(changes, "person.")
        Note over Utils: replaces EVERY occurrence of the prefix,<br/>not just the leading one (defect)
        alt sub-map empty
            Note over CustomersPatchServiceImpl: nested person untouched;<br/>the customer has no patchable field,<br/>so the whole patch is a no-op
        else sub-map non-empty
            CustomersPatchServiceImpl->>PeoplePatchServiceImpl: patchExistingEntity(subMap, person)
            Note over PeoplePatchServiceImpl: names / idNumber / email only when non-blank;<br/>phone1 / phone2 unconditionally
        end
        end
        CustomersCrudServiceImpl->>CustomersRepository: saveAndFlush(replacement)
        DataCustomersController-->>Operator: 204 No Content
    end
```

## Customer resolution at checkout and during guest authentication (cross-domain)

```mermaid
sequenceDiagram
    participant OrdersConverterServiceImpl
    participant JwtGuestAuthenticationFilter
    participant CustomersCrudServiceImpl
    participant CustomersRepository
    participant Order as Order entity

    rect rgb(240,240,240)
    Note over OrdersConverterServiceImpl,Order: checkout - order creation
    OrdersConverterServiceImpl->>CustomersCrudServiceImpl: getExisting(submitted customer)
    CustomersCrudServiceImpl->>CustomersRepository: findByPersonIdNumber(idNumber)
    alt customer found
        CustomersRepository-->>OrdersConverterServiceImpl: existing Customer
        Note over OrdersConverterServiceImpl: submitted NAMES are discarded
    else not found
        OrdersConverterServiceImpl->>CustomersCrudServiceImpl: create new customer (+ cascaded person)
    end
    OrdersConverterServiceImpl->>Order: setCustomer(resolved)
    Note over Order: orders.customer_id is NOT NULL:<br/>this step cannot be skipped
    end

    rect rgb(248,248,248)
    Note over JwtGuestAuthenticationFilter,CustomersRepository: POST /public/guest
    JwtGuestAuthenticationFilter->>CustomersCrudServiceImpl: create(guest PersonPojo)
    alt already registered
        CustomersCrudServiceImpl-->>JwtGuestAuthenticationFilter: EntityExists
        Note over JwtGuestAuthenticationFilter: logged "Guest with idNumber={} is<br/>already registered in the database",<br/>flow CONTINUES
    else bad input (e.g. blank idNumber)
        CustomersCrudServiceImpl-->>JwtGuestAuthenticationFilter: BadInput
        JwtGuestAuthenticationFilter-->>JwtGuestAuthenticationFilter: BadCredentials<br/>"Insufficient or invalid profile data for guest"
    else created
        Note over JwtGuestAuthenticationFilter: a PERMANENT person + customer<br/>row now exists for the "guest"
    end
    JwtGuestAuthenticationFilter->>JwtGuestAuthenticationFilter: authenticate as the configured guest account
    end
```
