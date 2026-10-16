# Error Handling Patterns

## The global error envelope

`api/ExceptionsControllerAdvice.java` maps exactly **four** conditions to a
structured response. Every other failure in the system falls through to the
framework default and surfaces as a 500-class response.

| Condition | Status | Code | Message | Retryable |
|---|---|---|---|---|
| entity not found | 404 | `NOTFOUND_01` | `The requested resource could not be found` | false |
| entity already exists | 400 | `EXISTS_01` | `There is one such resource with the same identifying properties that already exists` | false |
| bad input | 400 | `REJECTED_01` | `The request body did not meet the required criteria` | **true** |
| framework validation failure | 400 | `REJECTED_02` | `The request body did not meet the basic required criteria for acceptance` + a detail message listing `field: message` per violation, newline-separated | not set |

The envelope shape is `api/models/AppError.java`:
`{ code, message, detailMessage, canRetry }`.

**Important for a rewrite:** the generic messages are *constant* — the specific
reason (`Invalid product barcode`, `That username is taken.`, etc.) is **not**
returned to the client for the first three codes. Only the validation code
carries field-level detail. Clients therefore cannot distinguish between
different bad-input causes from the response alone.

## Local, per-controller handlers

Two controllers add their own handlers, bypassing the global advice:

| Controller | Condition | Status | Body |
|---|---|---|---|
| `PublicCheckoutController` | payment service failure | 500 | **the raw exception message**, not an envelope |
| `AccessController` | account-not-found, illegal state | 401 | **empty** — deliberately no explanation |

## Unmapped conditions (everything surfaces as 500)

This is the largest error-handling gap. Each of these is a condition the code
raises deliberately, with a meaningful message, that the client never sees as
anything other than a generic server error:

| Condition | Raised by | Should arguably be |
|---|---|---|
| `There is no account with the specified username` | profile read and replace | 404 |
| `The account does not have an associated profile` | profile read | 404 |
| `Protected account cannot be deleted` | account delete | 409 or 403 |
| `The payment type does not exist` | order creation | 400 |
| `Transaction could not be confirmed` | payment confirmation | 409 |
| `The server had a problem requesting the transaction` | payment start | 502 |
| `Transaction was confirmed, but server had an unexpected malfunction` | redirection building | 500 (correct) |
| `No status matches the '<STATUS>' name - Is the database empty or corrupt?` | every status transition | 500 (correct — a data fault) |
| `Converter could not turn Sell into its Pojo equivalent` | every status transition | 500 (correct) |
| `No user role matches 'Customer', database might be compromised` | self-registration | 500 (correct) |
| `Cannot update more than one item at a time` | every generic update and patch | 400 or 409 |
| `This method signature has been deprecated` / `This method is deprecated` | deprecated overloads | 500 (unreachable by design) |
| `Status of the sent e-mail is unknown, Mailgun did not provide an ID for this api` | notification | 502 — **and it fires after the order transition is already committed** |
| number-format failure on `pageIndex` / `pageSize` | the pagination service | 400 |
| null-dereference on an unknown `sortBy`, or a missing `order` with a present `sortBy` | the sort parser | 400 |
| null-dereference on an explicit null `price` / `currentStock` / `criticalStock` | the product patch service | 400 |

## Error-handling patterns by layer

### 1. Transport validation (before any service code)

Bean validation on the request body produces the `REJECTED_02` envelope with
field-level detail. Applied only where the enclosing method marks the body for
validation — notably **not** on `PUT /account/profile` (`BR-USER-051`), **not**
on the product-list contents replacement collection (`BR-PRODLIST-018`), and
**never** on any `PATCH`, which takes a raw change map.

### 2. Service-level rejection

Services throw a checked bad-input condition with a specific message. The message
is **swallowed** by the global advice, which returns its own constant text. Every
such literal is catalogued in `common-error-codes.md`.

### 3. Silent tolerance (the dominant pattern in this codebase)

A large and consistent set of conditions is **deliberately ignored**:

| Condition | Behaviour |
|---|---|
| a filter value that will not parse as a number | logged at info, the condition is dropped from the filter — so `?id=abc` returns an **unfiltered** page |
| a filter value that will not parse as a date | logged at warning, condition dropped |
| an unknown `rootCode` on a category search | the descendant condition is dropped, so the response is **unfiltered** rather than empty (`BR-PRODCAT-011`) |
| an unknown status, payment type or shipper name on an order patch | applied only if resolvable; otherwise discarded with no error and no log (`BR-ORDER-032`) |
| an unknown role name on an account patch | same (`BR-USER-045`) |
| a blank value for a string field on any patch | skipped — blanks never clear stored data |
| an unresolvable image during product image linking | caught, logged at debug, iteration continues (`BR-PRODUCT-013`) |
| an unresolvable product when adding to a product list | silently skipped; the request still succeeds (`BR-PRODLIST-015`) |
| an already-registered guest | caught, logged at info, the flow continues (`BR-CUSTOMER-018`) |
| an unrecognised company-profile parameter name | ignored by a do-nothing default branch (`BR-COMPANY-004`) |
| an order status with no mail template | returns silently (`BR-ORDER-051`) |
| a payment commit failure inside the adapter | converted to a non-zero response code, i.e. **treated as a decline** (`BR-ORDER-036`) |
| an empty filter on a generic delete | nothing is deleted, success is returned |

**The consequence is systemic: a success response does not mean the request was
fully applied.** A rewrite must decide, per case, whether to preserve the
tolerance or to start reporting partial application.

### 4. Infrastructure constraint violations as business errors

Several business rules are enforced **only** by database constraints, so
violating them produces an infrastructure error instead of a clean envelope:

| Rule | Surfaces as |
|---|---|
| product name uniqueness (the service checks barcode only) | constraint violation |
| image code and URL uniqueness (the service checks filename only) | constraint violation |
| billing-company name uniqueness during checkout | constraint violation **mid-checkout** |
| category sibling-name uniqueness (the service checks code only) | constraint violation |
| deleting a referenced image, category, carrier or customer | constraint violation |
| carrier name longer than 255 characters (no length annotation exists) | constraint violation |

## Exception inventory

| Type | File | Checked? | Mapped? |
|---|---|---|---|
| `BadInputException` | `common/exceptions/BadInputException.java` | yes | yes → `REJECTED_01` |
| entity-not-found (framework) | — | no | yes → `NOTFOUND_01` |
| entity-exists (framework) | — | no | yes → `EXISTS_01` |
| method-argument-not-valid (framework) | — | no | yes → `REJECTED_02` |
| `PaymentServiceException` | `payment/PaymentServiceException.java` | yes | locally, in the checkout controller → 500 |
| `MailingServiceException` | `mailing/MailingServiceException.java` | yes | **no** → 500 |
| `AccountProtectionViolationException` | `security/exceptions/AccountProtectionViolationException.java` | no | **no** → 500 |
| `UserNotFoundException` | `jpa/exceptions/UserNotFoundException.java` | no | **no** → 500 |
| `PersonNotFoundException` | `jpa/exceptions/PersonNotFoundException.java` | no | **no** → 500 |
| `CorsMappingParseException` | `config/exceptions/CorsMappingParseException.java` | yes | unreachable — the CORS builder is never wired |

## Recommendations for a rewrite

1. **Map the five unmapped custom exception types.** Account-not-found,
   profile-not-found, protected-account and mailing failures are all
   client-meaningful conditions currently reported as server errors.
2. **Decide whether to surface the specific message.** Today the global advice
   discards it; clients get a constant string. Surfacing it is a wire-contract
   change but a large usability gain.
3. **Separate notification failure from transition success.** The order status is
   committed before mail is attempted, so a notification failure returns an error
   for an operation that actually succeeded (`BR-ORDER-055`).
4. **Make partial application visible.** The silent-tolerance set above is the
   most likely source of unnoticed data loss, particularly product-list contents
   replacement and product image linking.
5. **Pull the database-only rules into the application** so that uniqueness and
   referential failures arrive as business errors rather than constraint
   violations.
6. **Guard the three null-dereference paths** (unknown `sortBy`, missing `order`,
   explicit null numeric patch values), which currently produce 500 responses for
   plain client mistakes.

## Related documentation

- every error literal in the codebase: `common-error-codes.md`
- per-domain validation layers and gaps: each `../detailed-domain-bre/domain-*/validations.md`
