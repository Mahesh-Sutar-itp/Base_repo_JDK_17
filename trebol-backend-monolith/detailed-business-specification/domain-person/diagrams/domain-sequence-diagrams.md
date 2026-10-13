# Person Registry — Sequence Diagrams

Four primary flows: the one HTTP read path, the internal creation path, the internal patch
path, and the failure paths worth calling out. Step-level behaviour for every participant
is specified in document `02`.

---

## 1. List people — `GET /data/people`

The only HTTP flow in the domain.

```mermaid
sequenceDiagram
    actor Caller as Staff user
    participant Sec as Security layer
    participant Ctl as DataPeopleController
    participant Pag as PaginationService
    participant Srt as SortSpecParserService
    participant Spec as PeopleSortSpec
    participant Pred as PeoplePredicateServiceImpl
    participant Crud as PeopleCrudServiceImpl
    participant Repo as PeopleRepository
    participant Conv as PeopleConverterServiceImpl
    participant DB as people table

    Caller->>Sec: GET /data/people?filters,paging,sort
    Sec->>Sec: authenticate
    alt not authenticated
        Sec-->>Caller: 401
    else lacks authority people:read
        Sec-->>Caller: 403
    else authorized
        Sec->>Ctl: readMany(allRequestParams)
        Ctl->>Pag: determineRequestedPageIndex(params)
        Pag-->>Ctl: pageIndex (0 if absent)
        Ctl->>Pag: determineRequestedPageSize(params)
        Pag-->>Ctl: pageSize (default, capped at max)

        opt params not empty
            Ctl->>Spec: ORDER_SPEC_MAP
            Spec-->>Ctl: allow-list of 7 sort keys
            Ctl->>Srt: parse(allowList, params)
            Srt-->>Ctl: Sort (unsorted if no sortBy)
            Ctl->>Pred: parseMap(params)
            Pred->>Pred: accumulate conditions per recognised param
            Pred-->>Ctl: Predicate (matches all if none recognised)
        end

        Ctl->>Crud: readMany(pageIndex, pageSize, sort, predicate)
        Crud->>Repo: count(predicate)
        Repo->>DB: SELECT COUNT(*)
        DB-->>Repo: totalCount
        Repo-->>Crud: totalCount
        Crud->>Repo: findAll(predicate, pageable)
        Repo->>DB: SELECT ... LIMIT/OFFSET
        DB-->>Repo: entity rows
        Repo-->>Crud: Page of Person

        loop for each Person in page
            Crud->>Conv: convertToPojo(person)
            Conv->>Conv: copy 4 fields; set phones only if non-null
            Conv-->>Crud: PersonPojo (no identifier)
        end

        Crud-->>Ctl: DataPagePojo(items, pageIndex, totalCount, pageSize)
        Ctl-->>Caller: 200 + page
    end
```

**Notes.** Two database round trips per request — the count and the fetch — not executed
atomically. The identifier is never emitted (BR-PERSON-020). An out-of-range page yields an
empty list with a non-zero total, not a 404.

---

## 2. Create a person — internal path

No HTTP route reaches this flow. The caller is always another domain: `user` during
self-registration, or `customer` / `salesperson` through their own creation flows.

```mermaid
sequenceDiagram
    participant Cons as Consumer domain<br/>(user / customer / salesperson)
    participant Crud as PeopleCrudServiceImpl<br/>(transactional)
    participant Repo as PeopleRepository
    participant Conv as PeopleConverterServiceImpl
    participant DB as people table

    Cons->>Crud: create(personPojo)
    Crud->>Crud: validateInputPojoBeforeCreation(input)
    Crud->>Crud: getExisting(input)

    alt idNumber blank (null, empty or whitespace)
        Crud-->>Cons: BadInputException<br/>"Customer does not have ID card" → 400
    else idNumber present
        Crud->>Repo: findByIdNumber(idNumber)
        Repo->>DB: SELECT ... WHERE person_id_number = ?
        DB-->>Repo: row or none
        Repo-->>Crud: Optional~Person~

        alt person already exists
            Crud-->>Cons: EntityExistsException<br/>"The item already exists" → 409
        else no match
            Crud->>Conv: convertToNewEntity(personPojo)
            Conv->>Conv: build entity; phones default to ""<br/>identifier left null
            Conv-->>Crud: unsaved Person
            Crud->>Repo: saveAndFlush(person)
            Repo->>DB: INSERT INTO people

            alt unique constraint violated (concurrent create)
                DB-->>Repo: constraint violation
                Repo-->>Cons: raw persistence failure → 500
            else insert succeeds
                DB-->>Repo: row with generated person_id
                Repo-->>Crud: saved Person
                Crud->>Conv: convertToPojo(saved)
                Conv-->>Crud: PersonPojo (identifier NOT included)
                Crud-->>Cons: PersonPojo
            end
        end
    end
```

**Notes.** The duplicate check and the insert are separate statements with no lock between
them, so the concurrent branch is reachable (BR-PERSON-019). The returned model carries no
identifier, so a consumer that needs the key must re-query by identification number
(BR-PERSON-020).

---

## 3. Patch a person — internal path

```mermaid
sequenceDiagram
    participant Cons as Consumer domain
    participant Crud as PeopleCrudServiceImpl<br/>(transactional)
    participant Repo as PeopleRepository
    participant Patch as PeoplePatchServiceImpl
    participant Conv as PeopleConverterServiceImpl
    participant DB as people table

    Cons->>Crud: partialUpdate(changes, id)
    Crud->>Repo: findById(id)
    Repo->>DB: SELECT ... WHERE person_id = ?
    DB-->>Repo: row or none
    Repo-->>Crud: Optional~Person~

    alt no such person
        Crud-->>Cons: empty Optional (no exception)
    else person found
        Crud->>Crud: flushPartialChanges(changes, existing)
        Crud->>Patch: patchExistingEntity(changes, existing)
        Patch->>Patch: clone via copy constructor<br/>(identifier copied)

        loop idNumber, firstName, lastName, email
            Patch->>Patch: if key present AND value not blank → set<br/>else leave unchanged
        end
        loop phone1, phone2
            Patch->>Patch: if key present → set unconditionally<br/>(blank DOES clear)
        end
        Patch->>Patch: unknown keys silently ignored

        alt a value is not a string
            Patch-->>Crud: ClassCastException
            Crud-->>Cons: wrapped RuntimeException → 500
        else patch applied
            Patch-->>Crud: modified clone
            Crud->>Crud: existing.equals(clone)?

            alt equal — nothing changed
                Crud->>Conv: convertToPojo(existing)
                Conv-->>Crud: PersonPojo
                Crud-->>Cons: PersonPojo<br/>NO DATABASE WRITE
            else differs
                Crud->>Repo: saveAndFlush(clone)
                Repo->>DB: UPDATE people WHERE person_id = ?
                DB-->>Repo: saved row
                Repo-->>Crud: saved Person
                Crud->>Conv: convertToPojo(saved)
                Conv-->>Crud: PersonPojo
                Crud-->>Cons: PersonPojo
            end
        end
    end
```

**Notes.** The equality comparison covers all seven entity fields, which is what makes the
no-write branch reachable (BR-PERSON-009). Redefining equality by identifier alone would
make *every* patch take the no-write branch — see document `01` §1.5. The blank-guard
asymmetry between the four identity fields and the two phone fields is BR-PERSON-007 /
BR-PERSON-008.

---

## 4. Failure paths worth calling out

```mermaid
sequenceDiagram
    actor Caller
    participant Ctl as DataPeopleController
    participant Srt as SortSpecParserService
    participant Spec as PeopleSortSpec
    participant Pred as PeoplePredicateServiceImpl

    rect rgb(245, 235, 235)
    note over Caller,Spec: Unknown sort key — shared-kernel defect (BR-PERSON-018)
    Caller->>Ctl: GET /data/people?sortBy=id&order=asc
    Ctl->>Srt: parse(allowList, params)
    Srt->>Spec: lookup "id"
    Spec-->>Srt: nothing (not in allow-list)
    Srt->>Srt: build sort from null — unchecked
    Srt-->>Caller: 500 unhandled failure
    end

    rect rgb(245, 235, 235)
    note over Caller,Srt: sortBy without order — same defect
    Caller->>Ctl: GET /data/people?sortBy=email
    Ctl->>Srt: parse(allowList, params)
    Srt->>Srt: switch on absent direction value — unchecked
    Srt-->>Caller: 500 unhandled failure
    end

    rect rgb(235, 240, 245)
    note over Caller,Pred: Non-numeric id filter — swallowed, NOT rejected (BR-PERSON-011)
    Caller->>Ctl: GET /data/people?id=abc
    Ctl->>Pred: parseMap(params)
    Pred->>Pred: parse "abc" as number — fails
    Pred->>Pred: catch, log at info level, continue loop
    Pred-->>Ctl: Predicate matching everything
    Ctl-->>Caller: 200 + unfiltered first page
    end

    rect rgb(235, 240, 245)
    note over Caller,Pred: Identity short-circuit discards other filters (BR-PERSON-010)
    Caller->>Ctl: GET /data/people?idNumber=X&email=wrong
    Ctl->>Pred: parseMap(params)
    Pred->>Pred: reach idNumber case → return immediately
    Pred-->>Ctl: Predicate on idNumber ONLY<br/>(email condition discarded)
    Ctl-->>Caller: 200 + the person with idNumber X
    end
```

**Why these are grouped here.** The two red paths are defects that produce a 500 where a
400 or a silent default is intended — a rewrite should fix them, accepting that the
observable status code changes. The two blue paths are *silent* behaviours: the caller
receives a 200 and cannot distinguish "filter applied" from "filter ignored". Preserving
them keeps compatibility; fixing them changes results clients may already depend on. Either
way the choice must be deliberate.

---

## 5. Flows that do not exist

For completeness, and because their absence is load-bearing:

- **No delete flow.** Nothing in the running system deletes a person row (BR-PERSON-013).
- **No event publication.** No flow emits a domain event, message or notification.
- **No external integration.** No flow calls an external system.
- **No background job.** No scheduled or asynchronous flow touches this domain.
- **No cache interaction.** No flow reads or writes a cache.
