# Sequence diagrams — Person Registry (`person`)

## Paged person search (`GET /data/people`)

```mermaid
sequenceDiagram
    actor Operator
    participant DataPeopleController
    participant PaginationServiceImpl
    participant SortSpecParserServiceImpl
    participant PeoplePredicateServiceImpl
    participant PeopleCrudServiceImpl
    participant PeopleRepository
    participant PeopleConverterServiceImpl

    Operator->>DataPeopleController: GET /data/people?pageSize&sortBy&idNumberLike
    Note over DataPeopleController: requires authority people:read
    DataPeopleController->>PaginationServiceImpl: determineRequestedPageIndex(params)
    DataPeopleController->>PaginationServiceImpl: determineRequestedPageSize(params)
    DataPeopleController->>SortSpecParserServiceImpl: parse(PeopleSortSpec.ORDER_SPEC_MAP, params)
    DataPeopleController->>PeoplePredicateServiceImpl: parseMap(params)
    DataPeopleController->>PeopleCrudServiceImpl: readMany(pageIndex, pageSize, sort, filters)
    PeopleCrudServiceImpl->>PeopleRepository: count(filters)
    PeopleCrudServiceImpl->>PeopleRepository: findAll(filters, pageRequest)
    PeopleRepository-->>PeopleCrudServiceImpl: Page<Person>
    loop each Person
        PeopleCrudServiceImpl->>PeopleConverterServiceImpl: convertToPojo(person)
    end
    PeopleCrudServiceImpl-->>DataPeopleController: DataPagePojo<PersonPojo>
    DataPeopleController-->>Operator: 200 page envelope
```

## Person de-duplication during account registration (cross-domain)

```mermaid
sequenceDiagram
    actor Visitor
    participant PublicRegisterController
    participant RegistrationServiceImpl
    participant PeopleConverterServiceImpl
    participant PeopleRepository
    participant UsersRepository
    participant CustomersRepository

    Visitor->>PublicRegisterController: POST /public/register (RegistrationPojo)
    PublicRegisterController->>RegistrationServiceImpl: register(registration)
    RegistrationServiceImpl->>UsersRepository: exists(name == submitted user name)
    alt user name taken
        RegistrationServiceImpl-->>Visitor: 400 EXISTS_01 "That username is taken."
    end
    RegistrationServiceImpl->>PeopleConverterServiceImpl: convertToNewEntity(profile)
    RegistrationServiceImpl->>PeopleRepository: exists(idNumber == profile.idNumber)
    alt id number already registered
        RegistrationServiceImpl-->>Visitor: 400 EXISTS_01 "That ID number is already registered..."
    end
    RegistrationServiceImpl->>PeopleRepository: saveAndFlush(person)
    RegistrationServiceImpl->>UsersRepository: saveAndFlush(user with person + Customer role)
    RegistrationServiceImpl->>CustomersRepository: saveAndFlush(customer for person)
    RegistrationServiceImpl-->>Visitor: 200
```

## Attaching or patching the person behind an account profile (cross-domain)

```mermaid
sequenceDiagram
    actor AccountHolder
    participant AccountProfileController
    participant ProfileServiceImpl
    participant PeopleCrudServiceImpl
    participant PeoplePatchServiceImpl
    participant PeopleRepository
    participant UsersRepository

    AccountHolder->>AccountProfileController: PUT /account/profile (PersonPojo)
    AccountProfileController->>ProfileServiceImpl: updateProfileForUserWithName(principal, profile)
    ProfileServiceImpl->>UsersRepository: findByNameWithProfile(userName)
    alt account has no person
        ProfileServiceImpl->>PeopleCrudServiceImpl: getExisting(profile)
        alt person found and owned by another account
            ProfileServiceImpl-->>AccountHolder: 400 REJECTED_01 "...associated to another account."
        else person found and unowned
            ProfileServiceImpl->>UsersRepository: save(user with attached person)
        else person not found
            ProfileServiceImpl->>PeopleRepository: saveAndFlush(new person)
            ProfileServiceImpl->>UsersRepository: save(user with attached person)
        end
    else account already has a person
        ProfileServiceImpl->>PeoplePatchServiceImpl: patchExistingEntity(changes, person)
        ProfileServiceImpl->>UsersRepository: save(user)
    end
    ProfileServiceImpl-->>AccountHolder: 200
```
