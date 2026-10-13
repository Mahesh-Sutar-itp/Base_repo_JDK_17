# Phase 0 exit record — gates and fix-before-extraction defects

Comparison point: `BASELINE.md` (pre-conversion). Everything below was verified by
running the build, not by inspection.

## Build result

```sh
. ~/opt/env.sh && cd trebol-backend-monolith && mvn -B clean verify
```

| | Baseline | After Phase 0 |
|---|---|---|
| Tests run | 363 | **414** |
| Failures | 1 | 1 |
| Errors | 2 | 2 |
| Test classes | 115 | 125 |

**Pass condition met: no new failures.** The three failures are the same three
environmental ones as the baseline, all in `WebpayplusPaymentServiceImplTest`
(register item **D18** — the test mocks only the configuration properties, not the
Transbank SDK, so it makes a real HTTP call to the sandbox and gets HTTP 403
off-network).

```
WebpayplusPaymentServiceImplTest$TestMode.fetches_payment_result
WebpayplusPaymentServiceImplTest$TestMode.fetches_payment_page_for_started_transactions
WebpayplusPaymentServiceImplTest$ProductionModeWithoutConfig.will_not_fetch_transaction_result
```

**Per-class diff against `test-baseline.txt`: all 115 baseline test classes are
still present and reporting.** No test was deleted or disabled (G3). Net +51 tests
across 10 new test classes.

## Gate state — verified mechanically

### Gate B — authority catalogue

```sh
grep -rhoE "hasAuthority\('[a-zA-Z:_-]*'\)" trebol-backend-monolith/src/main/java --include=*.java | wc -l
# 0        (baseline: 50)
```

The catalogue is generated from
`business-rules-extraction/database-documentation/00-seed-data.json` into
`org.trebol.security.authorities.AuthorityCodes` (54 codes) and committed.
Enforcement:

- `AuthorityCatalogueIntegrityTest` regenerates the file on every build and fails on
  **any** drift, on a count other than 54, and on a code that is not seeded — this
  is the "build fails on an unknown code" mechanism;
- `NoAuthorityLiteralsArchitectureTest` fails the build if a raw literal reappears
  in main source, and resolves every `@PreAuthorize` value reflectively to confirm
  each demanded authority exists in the catalogue (51 checks).

`Constants.AUTHORITY_CHECKOUT` is retained as a delegating alias so the three
security tests that assert the token's authority value keep compiling (G2/G3).

### Gate A — cross-domain repository reads replaced by contracts

The real exit criterion — *no class in the `order` domain imports another domain's
repository* — holds:

```sh
grep -rn "import org.trebol.jpa.repositories" trebol-backend-monolith/src/main/java \
  --include="*Orders*" --include="*Checkout*" --include="*Receipt*" \
  --include="*OrderStatuses*" --include="*OrderDetails*" \
  | grep -vE "OrdersRepository|OrderDetailsRepository|OrderStatusesRepository|AddressesRepository"
# (no output)
```

`OrderDomainCouplingArchitectureTest` asserts this continuously, plus that each
resolution goes through its contract and that each contract implementation is the
one holder of the underlying repository.

**Note on the published grep.** The command printed in the transformation
definition also matches order-domain classes importing their **own** repositories
(`OrdersProcessServiceImpl` → `Orders`/`OrderDetails`/`OrderStatuses`,
`ReceiptServiceImpl` → `Orders`), which is correct code. It therefore still returns
four lines, and would only return nothing if own-domain repository use were banned.
Reported here rather than silently redefined.

| Removed from the order domain | Now reached through |
|---|---|
| `ProductsRepository.findByBarcode` (per order line) | `ProductLookupContract` |
| `BillingTypesRepository.findByName` | `BillingContract` |
| `PaymentTypesRepository.findByName` (converter **and** patch service) | `BillingContract` |
| `BillingCompaniesCrudService` + `BillingCompaniesConverterService` (resolve-**or-create**) | `BillingContract` |
| `ShippersRepository.findByName` (converter **and** patch service) | `ShipperLookupContract` |

`AddressesRepository` and `OrderStatusesRepository` stay: `addresses` and
`order_statuses` are order-domain tables in the approved ownership map.

**What Gate A does and does not buy, stated plainly.** The contracts return the
target domain's JPA entity, because `Order` holds real `@ManyToOne` associations to
`Product`, `BillingType`, `BillingCompany`, `PaymentType` and `Shipper`. The seam —
one owner, one call site — now exists. Converting those associations to detached
references is ORM and schema work that belongs to Phases 1 and 3.

### Gates C and D — committed decisions

| Artefact | Ruling |
|---|---|
| `decisions/ADR-001-gate-c-consistency-model.md` | Keep co-located. Records the verified finding that neither `RegistrationServiceImpl` nor `CheckoutServiceImpl` is transactional today, so partial state already exists. |
| `decisions/ADR-002-gate-d-auth-propagation.md` | Shared signing key, local verification per service, method-level enforcement retained, gateway for routing/TLS only, `iss`/`aud`/`jti` added in Phase 2. Records that `SecurityConfig` has no request-matcher rules, so an unannotated endpoint is public and a gateway cannot derive authorization from paths. |
| `decisions/ADR-003-target-architecture.md` | Three services vs modular monolith — **open, deliberately deferred to this review**, per definition §2.3. |
| `decisions/defect-register-rulings.md` | All 18 register items ruled, plus two new items found while establishing the baseline (D19, D20). |

## Fix-before-extraction defects — landed with tests

| # | Fix | Tests |
|---|---|---|
| D1 | `DataCrudGenericController.delete` rejects a parameter map with no recognised selector instead of applying a match-everything predicate | guard test in `DataCrudGenericControllerTest` invoked by all 10 `Data*ControllerTest` classes, plus two predicate-level tests proving the value-less builder |
| D2 | token value removed from the malformed-token log | existing filter tests |
| D6 | profile update calls the implemented `Map` patch overload with only the submitted fields | 3 tests in `ProfileServiceImplTest` |
| D7 | image duplicate detection covers filename, code and url | 5 tests in `ImagesCrudServiceImplTest` |
| D9 | payment session identifier is a UUID | `WebpayplusPaymentSessionIdTest` (deliberately separate from the D18 test class so it never calls the sandbox) |
| D14 | guest login authenticates **before** provisioning; the write moved to `GuestCustomerProvisioningService` | `JwtGuestAuthenticationFilterOrderingTest`, `GuestCustomerProvisioningServiceImplTest` |
| D17 | unique constraint on `(user_role_id, permission_id)` | `UserRolePermissionUniquenessTest` |

### Intentional observable-behaviour changes

D1, D6, D7 and D14 change what a client sees; all four are recorded rulings. Two
existing tests asserted the defective behaviour and were updated — named here rather
than quietly adjusted:

- `DataCrudGenericControllerTest.deletes_data_parsing_predicate_filters_from_map`
  stubbed an empty `BooleanBuilder`, i.e. the match-all predicate that made D1
  destructive; it now stubs a predicate carrying a condition.
- `ProfileServiceImplTest.updates_existing_profile_data` stubbed the throwing
  `PersonPojo` patch overload, which is why it passed while the real flow always
  returned HTTP 500; it now stubs the `Map` overload.

### Known limitations recorded rather than worked around

- **D17 has no database round-trip test.** A `@DataJpaTest` slice cannot bootstrap
  this project (`org.trebol.jpa.Repository` is not `@NoRepositoryBean`, so scanning
  fails with "Not a managed type: interface org.trebol.jpa.DBEntity"), and
  Hibernate's `SchemaExport` is absent from the 6.4 line in use. Fixing the
  repository base interface is unrelated to D17 and was not bundled in.
- **D1 is fixed for `DELETE` only.** `update` and `partialUpdate` share the same
  match-all exposure; the register scopes D1 to `DELETE`, so this is registered as
  **D19** instead of widening the fix silently.
- **D14 is a partial fix.** Guest-name-as-password and the endpoint being a filter
  remain; Phase 2 requires porting `/public/guest` as a filter, so removing it is
  out of scope here. Bean validation was deliberately not added — it would convert a
  rejected guest profile from HTTP 401 to HTTP 500.
- **D18 is still open** and is a prerequisite for Phase 3, not for Phase 0.

## Phase 0 exit: met

No extraction has begun. All Phase 0 work landed in `trebol-backend-monolith`
source, green (G6).

**The next action is the ADR-003 decision — three services or a modular monolith —
and it is a decision for the project owner, not a mechanical next step.** Phase 0
was identical for both targets; from here the paths diverge.
