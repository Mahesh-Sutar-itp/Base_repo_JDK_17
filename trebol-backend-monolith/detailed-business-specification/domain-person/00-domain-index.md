# Person Registry — Domain Index

Domain slug: `person` · Complexity: small · 14 source files · 755 LOC · 1 owned table ·
1 entity · 1 HTTP endpoint · 28 business rules · 9 user stories

Manifest entry: `business-rules-extraction/domain-decomposition-overview/domain-manifest.json`
→ `domains[]` where `slug == "person"`.

---

## 1. Domain summary

**Purpose.** The canonical record of a natural person: legal first and last name, national
identification number, e-mail address and two phone numbers.

**Core invariant.** One national identification number identifies at most one person
(BR-PERSON-001), backed by a unique database key on `people.person_id_number`. This is the
contract three other bounded contexts depend on.

**Boundaries.** The domain is a pure leaf in the dependency graph: it `depends_on_domains`
nothing, holds no foreign key, and declares no association. All coupling is inbound, from
`user`, `customer` and `salesperson`.

**Key entity.** `Person` → table `people`. Declared **shared**; this domain is its owner.
Canonical field definition: `cross-cutting-documentation/shared-entities.md#person`.

**Key services.** One repository, one converter, one patch service, one CRUD service, one
predicate service, one sort-spec helper, one read-only controller. Four of the five
domain-specific service *contracts* are empty type bindings — the behaviour lives in the
shared-kernel generic bases, parameterised by this domain's single CRUD override
(`getExisting`).

---

## 2. Boundary warnings — read before extracting

Four findings from call-site analysis that change how this domain must be treated. Each is
specified in detail at the reference given.

| # | Finding | Where |
|---|---|---|
| W1 | **This domain does not control writes to its own table.** Three consumer domains write person rows, and they do so by calling the **repository directly**, bypassing the CRUD service. `create` has no caller anywhere. | BR-PERSON-026, `specs/06-integration-points.md` §2.2 |
| W2 | **Registration's atomicity depends on a shared transaction.** `POST /public/register` inserts a person, a user and a customer in one database transaction. Extracting this domain destroys that atomicity, and there is **no delete path** to compensate with. | `specs/06-integration-points.md` §4 |
| W3 | **A live defect: profile updates always fail** for any account that already has a profile, because the `user` domain calls a patch overload that throws unconditionally. | BR-PERSON-027, document `02` §5.2 |
| W4 | **`customer` has no representation of its own** — its transport model *is* the person model. The `customer`/`person` boundary is largely artificial, which argues for merging them in any service grouping. | `specs/06-integration-points.md` §2.3 |

---

## 3. File coverage

All 14 files in the manifest's `source_files[]` are documented. No file is excluded.

| # | Source file | LOC | Role | Covered in |
|---|---|---|---|---|
| 1 | `jpa/entities/Person.java` | 93 | entity | `01` §1 — ✅ Full |
| 2 | `api/models/PersonPojo.java` | 51 | transport model | `01` §2 — ✅ Full |
| 3 | `jpa/exceptions/PersonNotFoundException.java` | 34 | exception | `01` §3 — ✅ Full |
| 4 | `jpa/repositories/PeopleRepository.java` | 34 | repository | `02` §2 — ✅ Full |
| 5 | `jpa/services/conversion/PeopleConverterService.java` | 29 | contract | `02` §1 — ✅ Full |
| 6 | `jpa/services/conversion/impl/PeopleConverterServiceImpl.java` | 72 | service | `02` §4 — ✅ Full |
| 7 | `jpa/services/crud/PeopleCrudService.java` | 29 | contract | `02` §1 — ✅ Full |
| 8 | `jpa/services/crud/impl/PeopleCrudServiceImpl.java` | 64 | service | `02` §3 — ✅ Full |
| 9 | `jpa/services/patch/PeoplePatchService.java` | 29 | contract | `02` §1 — ✅ Full |
| 10 | `jpa/services/patch/impl/PeoplePatchServiceImpl.java` | 87 | service | `02` §5 — ✅ Full |
| 11 | `jpa/services/predicates/PeoplePredicateService.java` | 29 | contract | `02` §6.1 — ✅ Full |
| 12 | `jpa/services/predicates/impl/PeoplePredicateServiceImpl.java` | 88 | service | `02` §6.2 — ✅ Full |
| 13 | `jpa/sortspecs/PeopleSortSpec.java` | 43 | helper | `02` §7.1 — ✅ Full |
| 14 | `api/controllers/DataPeopleController.java` | 73 | controller | `02` §7.3 — ✅ Full |

Paths are relative to `src/main/java/org/trebol/`. **Coverage: 14/14 (100%).**

### 3.1 Shared-kernel files read but not owned

Read to specify inherited behaviour; **not** documented as this domain's own, and not part
of the coverage count. Their authoritative specification is
`domain-decomposition-overview/shared-kernel.md`.

| File | Why read | Where its behaviour is specified |
|---|---|---|
| `jpa/services/crud/CrudGenericService.java` | supplies all CRUD behaviour except `getExisting` | `02` §3.2–§3.10 |
| `api/DataGenericController.java` | supplies the list-request pipeline; read-only, hence no write route | `02` §7.3 |
| `jpa/services/CrudService.java`, `ConverterService.java`, `PatchService.java`, `PredicateService.java` | the generic contracts the domain binds | `02` §1 |
| `jpa/Repository.java`, `jpa/DBEntity.java` | generic persistence contract and entity marker | `02` §2, `01` §1 |
| `api/services/impl/PaginationServiceImpl.java`, `jpa/services/impl/SortSpecParserServiceImpl.java` | page/sort resolution, including the 500-causing defects | `02` §7.2 |
| `config/Constants.java` | the `person.` patch key prefix | BR-PERSON-028 |

### 3.2 Consumer files read to establish call boundaries

Read to census the inbound coupling. Owned by the `user`, `customer` and `salesperson`
domains and documented in **their** specifications.

`api/services/impl/ProfileServiceImpl.java`, `api/services/impl/RegistrationServiceImpl.java`,
`api/controllers/PublicRegisterController.java`,
`jpa/services/conversion/impl/UsersConverterServiceImpl.java`,
`jpa/services/conversion/impl/CustomersConverterServiceImpl.java`,
`jpa/services/patch/impl/CustomersPatchServiceImpl.java`,
`jpa/services/conversion/impl/SalespeopleConverterServiceImpl.java`,
`jpa/services/patch/impl/SalespeoplePatchServiceImpl.java`.

Findings recorded in `specs/06-integration-points.md` §2.2.

---

## 4. Entities

| Entity | Table | Ownership | Columns | Relationships |
|---|---|---|---|---|
| `Person` | `people` | owned by this domain; declared **shared** (used by `person`, `user`, `customer`, `salesperson`) | 7 | 0 declared — all three relationships are held by the consumer side |

No abstract base types, no inheritance hierarchy, no enumerations, no state field.

---

## 5. Spec inventory

| File | Provides |
|---|---|
| `00-domain-index.md` | this document — coverage, boundary warnings, inventory |
| `01-person-entity-and-transport.md` | behavioural spec: entity, transport model, exception type |
| `02-person-services.md` | behavioural spec: repository, converter, CRUD, patch, predicate, sort helper, controller |
| `specs/00-plan.md` | reading order, coverage, omitted specs with reasons |
| `specs/01-data-model.md` | columns, types, indexes, ORM/DDL divergences, shared-entity status |
| `specs/03-business-rules.md` | 28 numbered testable rules, plus corrections to the Mode A catalog |
| `specs/04-user-stories.md` | 9 stories with GIVEN/WHEN/THEN for happy, alternate and error paths |
| `specs/05-api-contract.md` | the one HTTP endpoint in full; the live/dead service surface; extraction gaps |
| `specs/06-integration-points.md` | external integrations (none, with evidence); the 17-site inbound call census |
| `diagrams/domain-er-diagram.md` | ER diagram, index layout, integrity notes |
| `diagrams/domain-sequence-diagrams.md` | read, create, patch and failure-path sequences |
| `diagrams/domain-class-diagram.md` | self-contained class diagram of domain types and kernel bindings |
| `errors.md` | 9 error conditions with literal messages; the deliberate non-errors |
| `permissions.md` | `people:read`; the absent write authorities; extraction considerations |
| `reference/api-endpoints.md` | endpoint inventory, plus the other domains' person-writing routes |
| `reference/background-jobs.md` | none, with the orphan-reclamation consequence |
| `reference/integrations.md` | none, with two notable absences |
| `reference/feature-flags.md` | none; the two kernel config values that do affect behaviour |

**18 files.** Omitted standard specs — `02-state-machines.md`, `07-query-specs.md`,
`08-config-parameters.md` and `events.md` — are each justified in `specs/00-plan.md` §4.

---

## 6. Absorbed manifest domains

**None.** This domain absorbs no other manifest slug. Its 14 source files are exclusively
its own, and every other manifest slug either has or will have its own output directory.

---

## 7. Coverage summary

| Category | Files | Source covered | Status |
|---|---|---|---|
| Behavioural specs | 2 | 14/14 files, all public and protected members | Complete |
| Specs suite | 5 | — | Complete (4 justified omissions) |
| Diagrams | 3 | — | Complete |
| Domain references | 4 | — | Complete |
| Errors and permissions | 2 | — | Complete |
| Index and plan | 2 | — | Complete |
| **Total** | **18** | **100%** | **Complete** |

Rewrite-readiness: every public and protected member of all 14 files has a documented
purpose, parameters, return value or side effect, and at least one named error path or an
explicit statement that none exists. Every literal error message, authority string, sort
key, filter parameter and column type is recorded verbatim.
