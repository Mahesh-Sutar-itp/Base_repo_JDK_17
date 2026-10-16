# Shared Kernel

Components used by **more than one** domain. 36 source files totalling 2,085
lines — about 12% of the main codebase. None of them owns a table.

Paths are relative to `src/main/java/org/trebol/`.

## 1. Persistence contracts (base classes and lifecycle interfaces)

| Component | File | Type | Used by |
|---|---|---|---|
| `DBEntity` | `jpa/DBEntity.java` | base class / interface | all 22 entities in all 12 domains |
| `Repository<E>` | `jpa/Repository.java` | framework | all 23 repositories |
| `CrudGenericService<M,E>` | `jpa/services/crud/CrudGenericService.java` | base class | 14 crud implementations in 11 domains |
| `CrudService<M,E>` | `jpa/services/CrudService.java` | lifecycle interface | same |
| `ConverterService<P,E>` | `jpa/services/ConverterService.java` | lifecycle interface | 15 converters |
| `PatchService<P,E>` | `jpa/services/PatchService.java` | lifecycle interface | 14 patch services |
| `PredicateService` | `jpa/services/PredicateService.java` | lifecycle interface | 15 predicate services |
| `SortSpecParserService` | `jpa/services/SortSpecParserService.java` | lifecycle interface | every listing endpoint |

`CrudGenericService` is the single most consequential shared component: it
supplies the already-exists conflict, the single-row update guards, the
no-op-write short-circuit and the not-found conditions that appear in **every**
domain's rules. Its literals are `Requested item(s) not found` and
`The item already exists`.

## 2. REST contracts (base classes)

| Component | File | Type | Used by |
|---|---|---|---|
| `DataController<M>` | `api/DataController.java` | lifecycle interface | every listing controller |
| `DataCrudController<M>` | `api/DataCrudController.java` | lifecycle interface | every CRUD controller |
| `DataGenericController<M,E>` | `api/DataGenericController.java` | base class | read-only controllers |
| `DataCrudGenericController<M,E>` | `api/DataCrudGenericController.java` | base class | full-CRUD controllers |

`DataCrudGenericController` supplies the `Missing request params` rejection, the
`No element was found to update` not-found, and the deliberate
do-nothing-on-empty-filter delete semantics — again cited in every CRUD domain.

**Not** inherited by: `PublicAboutController`, `PublicCheckoutController`,
`PublicReceiptController`, `PublicRegisterController`, `AccessController`,
`AccountProfileController`, `RootController` and
`DataProductListContentsController`. The last is notable: it is a `/data/`
endpoint that deliberately bypasses the framework and therefore has different
delete semantics (`BR-PRODLIST-017`).

## 3. Error handling

| Component | File | Type | Used by |
|---|---|---|---|
| `ExceptionsControllerAdvice` | `api/ExceptionsControllerAdvice.java` | framework | all domains |
| `AppError` | `api/models/AppError.java` | utility | the error envelope |
| `BadInputException` | `common/exceptions/BadInputException.java` | framework | thrown by every validating service |

Maps exactly four conditions. Everything else surfaces as a 500-class response —
see `../cross-cutting-documentation/error-handling-patterns.md`.

## 4. Pagination, sorting and utilities

| Component | File | Type | Used by |
|---|---|---|---|
| `PaginationService` / `PaginationServiceImpl` | `api/services/PaginationService.java`, `api/services/impl/PaginationServiceImpl.java` | utility | every listing endpoint, including the hand-written contents controller |
| `SortSpecParserServiceImpl` | `jpa/services/impl/SortSpecParserServiceImpl.java` | utility | every listing endpoint |
| `DataPagePojo<T>` | `api/models/DataPagePojo.java` | utility | every listing response |
| `Utils` | `common/Utils.java` | utility | `customer`, `salesperson` (the `person.` prefix convention) |
| `RegexMatcherAdapterService` / `...Impl` | `common/services/RegexMatcherAdapterService.java`, `common/services/impl/RegexMatcherAdapterServiceImpl.java` | utility | **declared but barely used** — its authorization-header validator is not called by the token filter (`BR-USER-021`) and its identification-number validator is called by no domain |

## 5. Security framework

| Component | File | Type |
|---|---|---|
| `SecurityConfig` | `config/SecurityConfig.java` | framework |
| `JwtKeyConfig` | `config/JwtKeyConfig.java` | framework |

These two are listed as shared kernel because they configure the filter chain and
signing key for **every** request in every domain. The filters themselves, the
account-details loader, the authority resolver and the token parser are
implementation of the `user` domain and are documented there — the split is
deliberate: configuration is kernel, identity logic is a domain.

## 6. Configuration property holders

| Component | File | Consumed by |
|---|---|---|
| `ApiProperties` | `config/ApiProperties.java` | `order` (edit lock), `product-category` (recursion depth), every listing endpoint (page sizes) |
| `SecurityProperties` | `config/SecurityProperties.java` | `user` |
| `ValidationProperties` | `config/ValidationProperties.java` | the regular-expression adapter and the unused phone validator |
| `CorsProperties` | `config/CorsProperties.java` | `CorsConfigurationSourceBuilder` only — **dead configuration** |
| `Constants` | `config/Constants.java` | `order` (8 status names, 2 token header names), `billing` (2 type names), `user` (3 token constants), `customer`/`salesperson` (the person prefix) |

`Constants` is a cross-domain coupling point in its own right: it holds literals
that three domains compare against, so it must be split or published as a shared
vocabulary artefact during extraction.

## 7. Validation annotations

| Component | File | Status |
|---|---|---|
| `PhoneNumber` | `common/annotations/PhoneNumber.java` | **applied to zero fields** (`BR-PERSON-006`) |
| `PhoneNumberValidator` | `common/annotations/validator/PhoneNumberValidator.java` | never invoked |

## 8. Dead shared components

| Component | File | Why dead |
|---|---|---|
| `CorsConfigurationSourceBuilder` | `config/CorsConfigurationSourceBuilder.java` | referenced by no bean; the filter chain declares no CORS configuration, so all `trebol.cors.*` configuration is inert |
| `CorsMappingParseException` | `config/exceptions/CorsMappingParseException.java` | thrown only by the above |
| `CorsProperties` | `config/CorsProperties.java` | consumed only by the above |

See `../reference-documentation/dead-code-decomposition-notes.md`.

## 9. Application entry point

| Component | File |
|---|---|
| `BackendApp` | `BackendApp.java` |
| `JpaConfig` | `config/JpaConfig.java` (entity and repository scanning only; declares no data source or transaction manager) |

---

## Shared-kernel tables

**None.** No shared component owns a table. Every one of the 22 tables belongs to
a business domain — see `data-ownership.md`.

## Extraction implications

| Kernel component | What happens on a split |
|---|---|
| `DBEntity`, `Repository<E>` | trivially duplicated per service, or published as a small library |
| `CrudGenericService`, `DataCrudGenericController` | **duplicate per service.** Sharing them would couple every service's release cycle to one library, and their behaviour (the four literal messages, the single-row guards) is what every domain's rules depend on — so the behaviour must be preserved even if the code is not shared |
| `ExceptionsControllerAdvice`, `AppError` | the error envelope is part of the **public API contract** and must stay byte-compatible across services |
| pagination and sorting | duplicate; the defaults (10, cap 1000) and the `pageIndex`/`pageSize`/`sortBy`/`order` parameter names are API contract |
| `Constants` | **must be split by domain** or published as a versioned vocabulary; three domains compare against its literals |
| `SecurityConfig`, `JwtKeyConfig` | become per-service token verification; the signing key becomes shared secret material |
| `ApiProperties` | split per consumer — the edit lock belongs to `order`, the recursion depth to `product-category`, the page sizes to every service |
| the CORS trio | delete, or implement properly; it is inert today |
| `PhoneNumber` + validator | delete, or apply deliberately |
