# Order — The Payment Adapter

Porting-depth behavioural specification. Subsystem 4 of 7. Paths relative to
`src/main/java/org/trebol/`.

This subsystem holds the system's **only outbound third-party call** and was changed by three
register items in Phase 3: **D18** (testability), **D3** (a failure is no longer a decline)
and **D4** (callbacks are authenticated and idempotent). Register item **D9** had already
been fixed before this phase.

---

## 1. The port — `payment/PaymentService`

```java
public interface PaymentService {
    PaymentRedirectionDetailsPojo requestNewPaymentPageDetails(OrderPojo transaction, String callbackNonce)
        throws PaymentServiceException;
    int requestPaymentResult(String transactionToken) throws PaymentServiceException;
    String getPaymentResultPageUrl();
}
```

A three-method hexagonal port with one adapter, `WebpayplusPaymentServiceImpl`. The
`callbackNonce` parameter was **added by D4**; the signature change is internal, since the
only implementation and the only caller are both in-repo.

**`requestPaymentResult` returns an `int`** — the provider's response code, where `0` means
approved. That raw integer is what makes **D3** possible to get wrong, because `1` is both
"declined" and, formerly, "we could not reach the provider".

---

## 2. Register item D18 — the testability seam

**Ruling: FIX before extraction, as its own reviewed commit. Implemented in step 3.1.**

The adapter constructed its Transbank client inline, so **four `catch` branches were
unreachable in a test** and the suite carried **three permanent environmental failures** —
tests that only reached the code under test by failing to reach the network, taking ~270s to
assert the wrong thing.

The fix is a **one-interface seam**:

```java
public interface WebpayTransactionFactory {
    WebpayPlus.Transaction createWebpayTransaction() throws TransactionCreateException;
}
```

`DefaultWebpayTransactionFactory` holds the former `createWebpayTransaction()` body
**verbatim**. No behavioural change and **no new dependency**.

**Outcome:** the adapter test went from 5 tests to **12**, with no network, covering all four
previously-untestable catch branches — and the monolith became green for the first time in
the programme (424 tests, 0 failures).

**This sequencing mattered.** D18 landed *first* precisely so that D3's change would be a
visible one-line diff in a test named for what it proves, rather than an invisible change in
a test that was never really testing.

---

## 3. `payment/impl/webpayplus/WebpayplusPaymentServiceImpl`

### 3.1 `requestNewPaymentPageDetails`

```java
String buyOrder = transaction.getBuyOrder().toString();
String sessionId = newSessionId();
double amount = transaction.getTotalValue();
String returnUrl = appendCallbackNonce(properties.getCallbackUrl(), callbackNonce);
```

| Rule | Behaviour |
|---|---|
| **BR-ORDER-035** | the payment amount is `transaction.getTotalValue()` — the order total, which per `03` §4 **excludes transport** |
| **BR-ORDER-034** | **corrected.** The rule says the session id is "derived from an object hash". **Register item D9 already fixed that** before this phase |
| **BR-ORDER-038** | payment creation failures are wrapped — a `TransactionCreateException` becomes `PaymentServiceException` |

```java
static String newSessionId() {
    return UUID.randomUUID().toString().replace("-", "");
}
```

The javadoc records the history: *"Register item D9: the session identifier used to be
`transaction.hashCode()`, which is…"* — not stable across processes or runs, and not a
meaningful business identifier. `WebpayplusPaymentSessionIdTest` (3 tests) pins the
replacement.

### 3.2 `appendCallbackNonce` — added by D4

```java
static String appendCallbackNonce(String callbackUrl, String nonce) {
    if (callbackUrl==null || nonce==null || nonce.isBlank()) {
        return callbackUrl;
    }
    String separator = callbackUrl.contains("?") ? "&":"?";
    return callbackUrl + separator + CHECKOUT_CALLBACK_NONCE_PARAM_NAME + "=" + nonce;
}
```

Appends with `?` or `&` depending on whether the operator's configured URL already carries a
query string, so existing configuration keeps working either way. **A blank nonce leaves the
URL untouched** — a caller with no nonce cannot silently produce a URL carrying an empty
parameter; the callback would then be rejected by the verification step, which is the correct
outcome.

---

## 4. Register item D3 — a failure is no longer a decline

**Ruling: FIX at the owning phase. Implemented in step 3.2.**

**Before:**

```java
} catch (TransactionCommitException exc) {
    return 1;        // the magic value
}
```

A `TransactionCommitException` — raised when the provider **could not be reached or could not
commit** — returned the literal `1`, which is indistinguishable from a provider-reported
decline. `CheckoutServiceImpl` then moved the order to **`Payment Failed`**.

> **BR-ORDER-036 described this as current behaviour: "a payment-commit failure is silently
> treated as a declined payment". It is now fixed**, and the rule is stale (`00` §3).

**After:**

```java
} catch (TransactionCommitException | IOException exc) {
    logger.error(...);
    throw new PaymentServiceException("Webpay failed to confirm the transaction");
}
```

The commit exception is merged into the `IOException` branch beside it, **reusing the existing
literal** so no new client-visible string was introduced. **BR-ORDER-037** — a transport-level
failure during confirmation is a payment-service failure — now describes both cases.

### 4.1 The intentional observable change

| | Before | After |
|---|---|---|
| Transbank outage during confirmation | HTTP **200**, order moved to **`Payment Failed`** | request fails through `PublicCheckoutController`'s existing `@ExceptionHandler` (**500**), order keeps **`Payment Started`** |

Keeping `Payment Started` is the truthful state, and it is what makes the order **eligible for
the later legitimate callback** that D4's idempotency fix makes safe to receive. The two
fixes are complementary by design.

**A genuine provider decline is untouched.** The two existing tests covering a real decline —
`acknowledges_successful_transaction_payment_integration_returns_1` and its
`BadInputException` variant — required **no change**, because D3 altered only the exception
branch, not the non-zero-response-code branch. An order declined by Transbank still moves to
`Payment Failed`.

### 4.2 The assertion that was inverted

| Was | Now |
|---|---|
| `currently_swallows_a_commit_exception_and_returns_one` | `raises_an_infrastructure_failure_rather_than_fabricating_a_decline` |

The old test name asserted the defect as correct behaviour. Renaming it with the inversion
keeps the change visible in a diff rather than silent. A second test was added to
`CheckoutServiceImplTest` — `infrastructure_failure_does_not_mark_the_order_as_failed` —
asserting both that the exception propagates **and** that no transition is attempted.

---

## 5. Register item D4 — authenticated, idempotent callbacks

**Ruling: FIX at the owning phase. Implemented in step 3.3. Closes exit criterion X9.**

The register called this the largest security gap in the system. Investigation found it
**worse than recorded**.

### 5.1 The gap

Both `/public/checkout/validate` mappings carry **no `@PreAuthorize`**, and `SecurityConfig`
declares **no request-matcher rules at all** (the Gate D finding, `ADR-002`). So they were
reachable by anyone who could guess or observe a token — and **BR-ORDER-040** records that as
intended, because the provider cannot authenticate.

**Transbank sends no callback signature.** There is nothing to verify cryptographically, so no
HMAC check is possible.

### 5.2 The control — a per-order nonce

Origin control is achieved by making the callback URL itself unguessable and single-purpose:

1. `CheckoutServiceImpl.requestTransactionStart` generates a nonce **before** requesting a
   redirection URL (`03` §1.1);
2. the adapter appends it to the configured callback URL (§3.2);
3. **`markAsStarted` persists it in the same transaction as the status transition** (`02`
   §5.2), so an order can never sit in `Payment Started` without the nonce that authenticates
   its callback;
4. both callback mappings require it, and a missing, blank or mismatched value is rejected
   **before any transition is attempted**.

```java
private void assertCallbackNonceMatches(String transactionToken, String presentedNonce)
    throws BadInputException {
    String expectedNonce = ordersProcessService.findCallbackNonce(transactionToken);
    if (expectedNonce==null || expectedNonce.isBlank()
        || presentedNonce==null || !expectedNonce.equals(presentedNonce)) {
        // Deliberately does not say WHICH check failed, and does not echo either value.
        logger.warn("Rejected a payment callback presenting an invalid nonce");
        throw new BadInputException(CALLBACK_NONCE_REJECTED);
    }
}
```

**One new client-visible literal** was unavoidable, because this is a new error condition:
`"The transaction callback could not be authenticated"` → **400**. It deliberately does not
say which check failed and does not echo either value.

**An order with no nonce recorded cannot be confirmed by callback** — which is the correct
outcome for orders predating the column and for orders that never began a checkout. The
alternative, where a null expected value matches a null presented value, would be a bypass.

A plain equality check is used rather than a constant-time comparison: the nonce is a
32-character random value, not a MAC, and there is no oracle here that a timing attack could
exploit — an attacker who can guess the nonce has already won. The code says so.

### 5.3 Idempotency

Payment providers **do** re-send callbacks. Previously a duplicate reached the `markAs*`
precondition, which raised `BadInputException`; `confirmTransaction` converted that into
`IllegalStateException` and the caller received **HTTP 500 for a normal event**.

A replay is now a **no-op**: if the order is already in one of the three statuses a callback
can itself produce — `Payment Cancelled`, `Payment Failed`, `Paid, Unconfirmed` — the order is
returned unchanged, the controller issues the **same 303 redirect** as the first time, and
**no second transition and no second provider commit** occur.

### 5.4 Why the nonce is not in the response

`PaymentRedirectionDetailsPojo` carries `url` and `token` only. **The nonce is deliberately
absent**: it travels to the provider inside the `returnUrl` and nowhere else. Adding it to the
response would both change the API shape and destroy the property that makes it evidence of
origin.

### 5.5 Tests — the X9 evidence

| Test | Proves |
|---|---|
| `rejects_a_callback_with_no_nonce` | no nonce → 400, and **no `markAs*` call of any kind** |
| `rejects_a_callback_with_the_wrong_nonce` | a guessed or stale nonce is no better than none |
| `rejects_a_callback_for_an_order_with_no_recorded_nonce` | null expected does not match null presented — no bypass |
| `a_replayed_callback_is_a_no_op` | the order comes back unchanged, no transition, **no second provider commit** |

**Three existing tests were updated, named rather than quietly adjusted:** the `"statusCode"`
entry was removed from 7 lookup maps (it asserted a filter that never applied); the nonce was
threaded through the `markAsStarted` and `requestNewPaymentPageDetails` call sites; and the
step-3.2 D3 assertion was **narrowed from `verifyNoInteractions` to three explicit `never()`
checks**, because D4 legitimately added a `findCallbackNonce` interaction before any
transition.

---

## 6. `payment/PaymentServiceException`

A checked exception with a message-only constructor, raised by all three port methods.
`PaymentServiceExceptionTest` has 2 tests.

`PublicCheckoutController` handles it with
`@ResponseStatus(INTERNAL_SERVER_ERROR) @ExceptionHandler`, **returning `ex.getMessage()` as
the response body** — so every payment-service message is client-visible and ports verbatim:

- `"Webpay failed to confirm the transaction"` — D3's reused literal
- the `TransactionCreateException` wrapper message — BR-ORDER-038

---

## 7. Configuration

`WebpayplusPaymentProperties` supplies the commerce code, API key, environment and
**`callbackUrl`** — the base the nonce is appended to. All are externalised; **no secret is
hardcoded**, and the Phase-3 services inherit that (X11).

---

## 8. Tests (G8)

| Tests | Class | Note |
|---|---|---|
| **12** | `WebpayplusPaymentServiceImplTest` | **rewritten in D18** — 5 → 12, no network, all four previously-untestable catch branches covered, one assertion inverted by D3 |
| 3 | `WebpayplusPaymentSessionIdTest` | pins D9's UUID session id |
| 2 | `PaymentServiceExceptionTest` | |
| 15 | `CheckoutServiceImplTest` | includes D4's four new tests and D3's one |

The adapter test runs **without network access**, which is the whole point of D18 and the
reason the suite is now deterministic.

---

## 9. Rules covered

BR-ORDER-034 (corrected), 035, 036 (corrected), 037, 038, 039, 040, 041 (corrected), 042,
043.

**Three of the ten rules in this subsystem are stale** — 034, 036 and 041 — which is the
highest concentration in the domain, and unsurprising: this is where Phase 3 did most of its
remediation. All three are listed in `00` §3.
