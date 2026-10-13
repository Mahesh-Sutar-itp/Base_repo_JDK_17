# Common Error Codes

## The four envelope codes

These are the only structured codes the system emits.
Source: `api/ExceptionsControllerAdvice.java`, `api/models/AppError.java`.

| Code | Status | Envelope message (constant) | Retryable |
|---|---|---|---|
| `NOTFOUND_01` | 404 | `The requested resource could not be found` | false |
| `EXISTS_01` | 400 | `There is one such resource with the same identifying properties that already exists` | false |
| `REJECTED_01` | 400 | `The request body did not meet the required criteria` | **true** |
| `REJECTED_02` | 400 | `The request body did not meet the basic required criteria for acceptance` (plus a per-field detail message) | not set |

**Critical for a rewrite:** the specific internal message is **not** returned to
the client for the first three codes. Everything in the catalogue below is
internal — it appears in logs and in tests, never in a response body. Only
`REJECTED_02` carries field-level detail.

---

## Internal message catalogue

Every error literal in `src/main/java`, grouped by the code it maps to.

### Mapped to `REJECTED_01` (bad input)

| Message literal | Raised by | Domain |
|---|---|---|
| `Customer does not have ID card` | `PeopleCrudServiceImpl.getExisting` | `person` |
| `Customer does not have an ID card` | `CustomersCrudServiceImpl.getExisting` | `customer` |
| `Salesperson does not have an ID card` | `SalespeopleCrudServiceImpl.getExisting` | `salesperson` |
| `Invalid filename` | `ImagesCrudServiceImpl.getExisting` | `image` |
| `Invalid category code` | `ProductCategoriesCrudServiceImpl.getExisting` | `product-category` |
| `Invalid product barcode` | `ProductsCrudServiceImpl.getExisting` | `product` |
| `Invalid status name` | `OrderStatusesCrudServiceImpl.getExisting` | `order` |
| `Invalid user name` | `UsersCrudServiceImpl.getExisting` | `user` |
| `Invalid user role name` | `UserRolesCrudServiceImpl.getExisting` | `user` |
| `Billing type has no name` | `BillingTypesCrudServiceImpl.getExisting` | `billing` |
| `Billing type has no name` | `ShippersCrudServiceImpl.getExisting` — **copy-paste defect**, this is the shipper service | `shipper` |
| `Billing company has no id number` | `BillingCompaniesCrudServiceImpl.getExisting` | `billing` |
| `The specified list has no name` | `ProductListsCrudServiceImpl.getExisting` | `product-list` |
| `Missing request params` | `DataCrudGenericController` (update and patch) | shared kernel |
| `listCode query param is required` | `DataProductListContentsController` | `product-list` |
| `A payment type has to be included` | `OrdersConverterServiceImpl` | `order` |
| `Specified billing type does not exist` | `OrdersConverterServiceImpl` | `order` / `billing` |
| `Specified shipper does not exist` | `OrdersConverterServiceImpl` | `order` / `shipper` |
| `Product barcode must be valid` | `OrdersConverterServiceImpl.convertDetailToNewEntity` | `order` / `product` |
| `Unexisting product in sell details` | `OrdersConverterServiceImpl.convertDetailToNewEntity` | `order` / `product` |
| `The transaction is not in a valid state for this api` | `OrdersProcessServiceImpl` — all seven transitions | `order` |
| `The requested transaction cannot be modified` | `OrdersCrudServiceImpl.flushPartialChanges` (the edit lock) | `order` |
| `The ` | `OrdersPatchServiceImpl` cast-failure handler — **truncated string, defect** | `order` |
| `No transaction token was provided` | `PublicCheckoutController`, both callbacks | `order` |
| `An incorrect receipt token was provided` | `PublicReceiptController` | `order` |
| `Person profile is associated to another account. Cannot use it.` | `ProfileServiceImpl` | `user` / `person` |

### Mapped to `EXISTS_01` (already exists)

| Message literal | Raised by | Domain |
|---|---|---|
| `The item already exists` | `CrudGenericService.validateInputPojoBeforeCreation` — **every** CRUD domain | shared kernel |
| `That username is taken.` | `RegistrationServiceImpl` | `user` |
| `That ID number is already registered and associated to an account.` | `RegistrationServiceImpl` | `user` / `person` |

### Mapped to `NOTFOUND_01` (not found)

| Message literal | Raised by | Domain |
|---|---|---|
| `Requested item(s) not found` | `CrudGenericService` (`readOne`, `delete`) and `DataProductListContentsController` | shared kernel, `product-list` |
| `No element was found to update` | `DataCrudGenericController` (update and patch) | shared kernel |
| `No sell matches the filtering conditions` | `OrdersCrudServiceImpl.readOne` | `order` |
| `No transaction matches given input` | `OrdersProcessServiceImpl` — all seven transitions | `order` |
| `The transaction could not be found, no receipt can be created` | `ReceiptServiceImpl` | `order` |

### Mapped to `REJECTED_02` (framework validation)

No literals — the detail message is assembled from the violated field constraints
as `field: message`, newline-separated. The constraint messages themselves are
framework defaults except `Phone number is not valid`
(`common/annotations/PhoneNumber.java`), which is **never emitted** because the
annotation is applied to zero fields (`BR-PERSON-006`).

### Mapped locally to 500 by `PublicCheckoutController`

| Message literal | Raised by |
|---|---|
| `Webpay could not create a new transaction` | `WebpayplusPaymentServiceImpl` |
| `Webpay failed to confirm the transaction` | `WebpayplusPaymentServiceImpl` |

The raw message is returned as the response body — the only place in the system
where an internal message reaches a client.

### Mapped locally to 401 by `AccessController`

Account-not-found and illegal-state conditions, returned with an **empty body**
and deliberately no explanation.

### Unmapped — surface as generic 500 responses

| Message literal | Raised by | Arguably should be |
|---|---|---|
| `There is no account with the specified username` | `ProfileServiceImpl` | 404 |
| `The account does not have an associated profile` | `ProfileServiceImpl` | 404 |
| `Protected account cannot be deleted` | `UsersCrudServiceImpl.delete` | 409 / 403 |
| `The payment type does not exist` | `OrdersConverterServiceImpl` | 400 |
| `Transaction could not be confirmed` | `CheckoutServiceImpl` | 409 |
| `The server had a problem requesting the transaction` | `CheckoutServiceImpl` | 502 |
| `Transaction was confirmed, but server had an unexpected malfunction` | `CheckoutServiceImpl` | 500 (correct) |
| `No status matches the '<STATUS>' name - Is the database empty or corrupt?` | `OrdersProcessServiceImpl` | 500 (correct — data fault) |
| `Converter could not turn Sell into its Pojo equivalent` | `OrdersProcessServiceImpl` | 500 (correct) |
| `No user role matches 'Customer', database might be compromised` | `RegistrationServiceImpl` | 500 (correct) |
| `Cannot update more than one item at a time` | `CrudGenericService` | 400 / 409 |
| `This method signature has been deprecated` | `CrudGenericService.update(M)` | unreachable by design |
| `This method is deprecated` | `OrdersConverterServiceImpl.applyChangesToExistingEntity` | unreachable by design |
| `This method [signature] has been deprecated` | every patch service's deprecated overload | unreachable by design |
| `Status of the sent e-mail is unknown, Mailgun did not provide an ID for this api` | `MailgunMailingServiceImpl` | 502 — and it fires **after** the order transition is committed |
| `Could not stringify transaction object` | `MailgunMailingServiceImpl` | 500 |
| `Request body is not a login request` | `JwtLoginAuthenticationFilter` | 400 (surfaces as a credentials failure) |
| `Invalid request body for guest session` | `JwtGuestAuthenticationFilter` | 400 (same) |
| `Insufficient or invalid profile data for guest` | `JwtGuestAuthenticationFilter` | 400 (same) |
| `Token %s can't be trusted` | `ClaimsAuthorizationHeaderParserServiceImpl` — **embeds the token value** | 401 |
| `Could not parse CORS mapping. Format must be 'METHODS /path'.` | `CorsMappingParseException` | unreachable — the CORS builder is never wired |

---

## Log-message literals worth noting

| Literal | Component | Concern |
|---|---|---|
| `Access denied: '{}' '{}' used an invalid token '{}'` | `JwtTokenVerifierFilter` | **writes the full token value to the log** (`BR-USER-020`) |
| `New user created with name '{}' and idNumber '{}'` | `RegistrationServiceImpl` | logs personal data |
| `Guest with idNumber={} is already registered in the database` | `JwtGuestAuthenticationFilter` | logs personal data |
| `An image was not linked to product with barcode '{}'` | `ProductsCrudServiceImpl` | debug level; the client is never told an image was dropped |
| `Param '{}' couldn't be parsed as number (value: '{}')` | every predicate service | info level; the filter condition is silently dropped |
| `Incorrect state of sell, was: {}` | `CheckoutServiceImpl` | the real state is logged but hidden from the client |
| `Exception raised while creating transaction: ` | `WebpayplusPaymentServiceImpl` | — |

---

## Patterns a rewrite should preserve or fix deliberately

1. **Four codes only.** Clients today can distinguish not-found, conflict,
   bad-input and validation-failure — nothing more. Adding codes is a
   wire-contract improvement; removing the generic masking is a bigger one.
2. **`canRetry` is set only on `REJECTED_01`,** and set to `true`, which is
   semantically odd for a rejected body. Preserve or correct deliberately.
3. **Five custom exception types are unmapped** and produce 500 responses for
   client-meaningful conditions.
4. **Three message defects** should be fixed: the truncated `The `, the
   shipper service's `Billing type has no name`, and the two near-identical
   person/customer identification-card messages.
5. **One message leaks a credential** into the log and into an exception message.
6. **Two messages reach the client verbatim** (the payment failures) while every
   other internal message is masked — an inconsistency worth resolving.

## Related documentation

- the handling layers and the silent-tolerance set: `error-handling-patterns.md`
- per-domain validation tables: each `../detailed-domain-bre/domain-*/validations.md`
