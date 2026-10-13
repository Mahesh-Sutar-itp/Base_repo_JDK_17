# Person Registry — Class Diagram

A self-contained view of the domain's types and their relationships to the shared-kernel
contracts they bind. Behavioural detail is in documents `01` and `02`; field-level detail
is in `specs/01-data-model.md`.

## Domain types and shared-kernel bindings

```mermaid
classDiagram
    direction TB

    class DBEntity {
        <<interface, shared kernel>>
        +getId() Long
        +setId(Long)
    }

    class Person {
        <<entity, table people>>
        -Long id
        -String firstName
        -String lastName
        -String idNumber
        -String email
        -String phone1
        -String phone2
        +Person()
        +Person(all seven fields)
        +Person(Person source) "copy constructor"
        +getters and setters
        +equals() "all seven fields"
        +hashCode()
    }

    class PersonPojo {
        <<transport model>>
        -String firstName
        -String lastName
        -String idNumber
        -String email
        -String phone1
        -String phone2
        +getters and setters
        note "no identifier field"
    }

    class PersonNotFoundException {
        <<exception, never thrown>>
        +PersonNotFoundException()
        +PersonNotFoundException(String message)
    }

    class EntityNotFoundException {
        <<framework exception>>
    }

    DBEntity <|.. Person
    EntityNotFoundException <|-- PersonNotFoundException
    Person ..> PersonPojo : "converted to and from"
```

## Service layer

```mermaid
classDiagram
    direction TB

    class Repository~E~ {
        <<interface, shared kernel>>
        +findById(Long) Optional~E~
        +findAll(Pageable) Page~E~
        +findAll(Predicate, Pageable) Page~E~
        +findOne(Predicate) Optional~E~
        +count() long
        +count(Predicate) long
        +saveAndFlush(E) E
        +deleteAll(Iterable~E~)
    }

    class PeopleRepository {
        <<interface>>
        +findByIdNumber(String) Optional~Person~
    }

    class ConverterService~P,E~ {
        <<interface, shared kernel>>
        +convertToNewEntity(P) E
        +convertToPojo(E) P
        +applyChangesToExistingEntity(P, E) E
    }

    class PeopleConverterService {
        <<interface, empty binding>>
    }

    class PeopleConverterServiceImpl {
        +convertToNewEntity(PersonPojo) Person
        +convertToPojo(Person) PersonPojo
        +applyChangesToExistingEntity(PersonPojo, Person) Person "always throws"
    }

    class PatchService~P,E~ {
        <<interface, shared kernel>>
        +patchExistingEntity(Map, E) E
        +patchExistingEntity(P, E) E
    }

    class PeoplePatchService {
        <<interface, empty binding>>
    }

    class PeoplePatchServiceImpl {
        +patchExistingEntity(Map, Person) Person
        +patchExistingEntity(PersonPojo, Person) Person "always throws"
    }

    class PredicateService {
        <<interface, shared kernel>>
        +parseMap(Map) Predicate
    }

    class PeoplePredicateService {
        <<interface>>
        +QPerson basePath "query root constant"
    }

    class PeoplePredicateServiceImpl {
        -Logger logger
        +parseMap(Map) Predicate
    }

    Repository <|-- PeopleRepository
    ConverterService <|-- PeopleConverterService
    PeopleConverterService <|.. PeopleConverterServiceImpl
    PatchService <|-- PeoplePatchService
    PeoplePatchService <|.. PeoplePatchServiceImpl
    PredicateService <|-- PeoplePredicateService
    PeoplePredicateService <|.. PeoplePredicateServiceImpl
```

## CRUD orchestration and the controller

```mermaid
classDiagram
    direction TB

    class CrudService~M,E~ {
        <<interface, shared kernel>>
        +create(M) M
        +readMany(int, int, Sort, Predicate) DataPagePojo~M~
        +readOne(Predicate) M
        +getExisting(M) Optional~E~
        +update(M, Long) Optional~M~
        +update(M, Predicate) Optional~M~
        +partialUpdate(Map, Long) Optional~M~
        +partialUpdate(Map, Predicate) Optional~M~
        +delete(Predicate)
    }

    class CrudGenericService~M,E~ {
        <<abstract, shared kernel, transactional>>
        #ITEM_NOT_FOUND "Requested item(s) not found"
        #ITEM_ALREADY_EXISTS "The item already exists"
        -Repository~E~ repository
        -ConverterService~M,E~ converter
        -PatchService~M,E~ patchService
        #flushPartialChanges(Map, E) E
        #validateInputPojoBeforeCreation(M)
    }

    class PeopleCrudService {
        <<interface, empty binding>>
    }

    class PeopleCrudServiceImpl {
        <<transactional>>
        -PeopleRepository peopleRepository
        +getExisting(PersonPojo) Optional~Person~ "the only override"
    }

    class DataController~M~ {
        <<interface, shared kernel>>
        +readMany(Map) DataPagePojo~M~
    }

    class DataGenericController~M,E~ {
        <<abstract, shared kernel, read-only>>
        #PaginationService paginationService
        #SortSpecParserService sortService
        #CrudService~M,E~ crudService
        #PredicateService predicateService
        #getOrderSpecMap() Map
    }

    class DataPeopleController {
        <<REST controller, /data/people>>
        +readMany(Map) DataPagePojo~PersonPojo~ "authority people:read"
        #getOrderSpecMap() Map
    }

    class PeopleSortSpec {
        <<helper, non-instantiable>>
        -QPerson BASE_PATH
        +Map ORDER_SPEC_MAP "7 keys, 6 distinct orderings"
    }

    CrudService <|.. CrudGenericService
    CrudService <|-- PeopleCrudService
    CrudGenericService <|-- PeopleCrudServiceImpl
    PeopleCrudService <|.. PeopleCrudServiceImpl
    DataController <|.. DataGenericController
    DataGenericController <|-- DataPeopleController
    DataPeopleController ..> PeopleSortSpec : "reads allow-list"
    PeopleCrudServiceImpl ..> PeopleRepository : "injected"
    PeopleCrudServiceImpl ..> PeopleConverterService : "injected"
    PeopleCrudServiceImpl ..> PeoplePatchService : "injected"
    DataPeopleController ..> PeopleCrudService : "injected"
    DataPeopleController ..> PeoplePredicateService : "injected"
```

## Reading the structure

Three observations matter more than the shapes:

1. **The four domain-specific contracts are empty.** `PeopleCrudService`,
   `PeopleConverterService`, `PeoplePatchService` and the repository's own binding declare
   no methods of their own beyond `findByIdNumber` — they bind generic shared-kernel
   contracts to this domain's type pair so the injection container can resolve them. A
   rewrite in a language without that constraint can collapse them away.
   `PeoplePredicateService` is the one exception: it carries the query-root constant.
2. **`PeopleCrudServiceImpl` overrides exactly one method.** Everything else the CRUD
   service does is inherited generic behaviour parameterised by that one definition of
   "what identifies a person" (`getExisting`).
3. **The controller extends the read-only base.** `DataGenericController` contributes only
   a list operation — which is the structural reason the domain has no write route
   (BR-PERSON-013), and the reason an extraction must add one.
