# Baseline Record — pre-conversion

Established before any source modification. This is the comparison point for every
subsequent phase of `microservices-conversion`.

## Toolchain

Nothing was installed system-wide; `sudo` requires interactive authentication in
this environment, so both tools were installed into userspace under `~/opt`.

| Tool | Version | Source | Verified |
|---|---|---|---|
| JDK | Amazon Corretto 21.0.12.12.1 | `https://corretto.aws/downloads/latest/amazon-corretto-21-x64-linux-jdk.tar.gz` (official AWS) | `java -version` reports `21.0.12.1` |
| Maven | Apache Maven 3.9.16 | `https://dlcdn.apache.org/maven/maven-3/3.9.16/...` (official Apache) | **SHA-512 checksum verified `OK`** against the published `.sha512` |

Environment script — **source this before every build**:

```sh
. ~/opt/env.sh      # sets JAVA_HOME, M2_HOME, PATH
```

Project targets `java.version 21` on Spring Boot `3.2.12`, so Corretto 21 is the
matching runtime.

## Build command

```sh
. ~/opt/env.sh && cd trebol-backend-monolith && mvn -B clean verify
```

**Wall time ≈ 10 minutes** per full run (dependencies now cached; the first run was
9m47s including the full download). This is the feedback loop for every change.

## Baseline result

```
Tests run: 363, Failures: 1, Errors: 2, Skipped: 0
BUILD FAILURE
```

Compilation succeeds. 115 test classes execute. **360 of 363 tests pass.**

### The 3 failures are environmental, not code defects

All three are in `WebpayplusPaymentServiceImplTest`:

| Test | Symptom |
|---|---|
| `TestMode.fetches_payment_result` | `JsonSyntaxException: Expected BEGIN_OBJECT but was STRING at line 1 column 1` |
| `TestMode.fetches_payment_page_for_started_transactions` | same |
| `ProductionModeWithoutConfig.will_not_fetch_transaction_result` | `PaymentService: Webpay failed to confirm the transaction` |

**Root cause, diagnosed:** the test mocks only `WebpayplusPaymentProperties` — the
configuration object. It does **not** mock the Transbank SDK. The SDK therefore
makes a **real HTTP call** to the sandbox at `webpay3gint.transbank.cl`, which from
this network returns **HTTP 403 with `content-type: text/html`**. Gson receives an
HTML error page instead of JSON, which is exactly the reported error.

Verified directly:

```
curl -sI https://webpay3gint.transbank.cl
HTTP/2 403
content-type: text/html
```

### Why this matters beyond the build

This is a **test-design defect** and belongs in the defect register as a new item:
an integration test presented as a unit test, with the external SDK unmocked. Its
consequence is that **the payment adapter has no genuine unit-test coverage** — the
single most critical and least safely-changeable component in the system, and the
one Phase 3 must port with the D3, D4 and D9 fixes applied.

**These tests were not modified, disabled or deleted** (guardrail G3). Excluding
the class via `-Dtest='!WebpayplusPaymentServiceImplTest'` had no effect — the pom
declares no surefire configuration, inheriting it from the Spring Boot parent — and
rather than fight that, the gate below compares against the known failure set.

## The verification gate for every subsequent change

> **No new failures beyond the 3 known environmental ones.**
> Expected after any conforming change: `Tests run: 363, Failures: 1, Errors: 2`,
> with all three in `WebpayplusPaymentServiceImplTest`.
> Any other failing test is a regression introduced by the change.

Per-class baseline for diffing: `test-baseline.txt` (115 lines, one per test class,
all reporting `Failures: 0, Errors: 0` at class level).

## Open question for the project owner

Should `WebpayplusPaymentServiceImplTest` be rewritten to mock the Transbank SDK?

- **For:** it would give a genuinely green baseline, remove a network dependency
  from the build, and provide real coverage of the payment adapter before Phase 3
  touches it.
- **Against:** it changes test semantics, and guardrail G3 permits modifying tests
  only for compatibility. Rewriting them is an improvement, not a compatibility
  fix, so it needs an explicit ruling.

Recommended: **yes, but as its own reviewed change**, before Phase 3 — not folded
silently into a refactor.
