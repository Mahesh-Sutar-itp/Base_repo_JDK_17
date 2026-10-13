# Business Rules — Order, Checkout & Fulfilment (`order`)

## Order creation

### BR-ORDER-001 A new order always starts as `Pending`
`OrdersConverterServiceImpl.convertToNewEntity` resolves the status by the
literal name `Pending`. Asserting: no API path can create an order in any other
status.
Source: `jpa/services/conversion/impl/OrdersConverterServiceImpl.java`,
`config/Constants.java`.

### BR-ORDER-002 An order must have at least one line
`OrderPojo.details` is marked non-empty with nested validation. Asserting: an
empty cart is rejected with the validation envelope before any service code runs.
Source: `api/models/OrderPojo.java`.

### BR-ORDER-003 Each line must have at least one unit and a product
`OrderDetailPojo.units` has a minimum of 1 and `product` is non-null.
Source: `api/models/OrderDetailPojo.java`.

### BR-ORDER-004 A payment type is mandatory and must exist
A blank value raises `A payment type has to be included`; an unknown one raises
the unchecked `The payment type does not exist`. Asserting: the second case is an
unmapped runtime failure and surfaces as a 500-class response rather than a
business error.
Source: `jpa/services/conversion/impl/OrdersConverterServiceImpl.java`.

### BR-ORDER-005 A billing type is mandatory and must exist
An unknown billing type raises `Specified billing type does not exist`. The
enterprise branch additionally resolves or creates a billing company and a
billing address. See `domain-billing/business-rules.md`.
Source: `jpa/services/conversion/impl/OrdersConverterServiceImpl.java`.

### BR-ORDER-006 A customer is mandatory and is reused when it exists
`orders.customer_id` is not null. The converter resolves the submitted customer
by identification number and reuses the stored row, creating one only when none
matches. Asserting: checkout never conflicts on an existing customer.
Source: `jpa/services/conversion/impl/OrdersConverterServiceImpl.java`,
`jpa/entities/Order.java`.

### BR-ORDER-007 A shipper is optional but must exist when supplied
An unknown shipper raises `Specified shipper does not exist`.
Source: `jpa/services/conversion/impl/OrdersConverterServiceImpl.java`.

### BR-ORDER-008 Each line's product must resolve by barcode
A blank barcode raises `Product barcode must be valid`; an unknown barcode raises
`Unexisting product in sell details`. Asserting: both are wrapped as unchecked
failures inside the detail conversion, so a bad line can surface as a 500-class
response.
Source: `jpa/services/conversion/impl/OrdersConverterServiceImpl.java`.

### BR-ORDER-009 The server sets the unit value, not the client
Each line's unit value is overwritten with the product's current price.
Asserting: a client submitting a unit value of 1 for an expensive product is
charged the catalogue price. This is the system's price-integrity control.
Source: `jpa/services/conversion/impl/OrdersConverterServiceImpl.java`.

### BR-ORDER-010 The line description is generated, not supplied
It is built as the unit count followed by the product name. Asserting: a
client-supplied description is discarded, and the stored description preserves
the product name at the time of purchase even if the product is later renamed or
deleted.
Source: `jpa/services/conversion/impl/OrdersConverterServiceImpl.java`.

### BR-ORDER-011 Order history is price-stable
Because the unit value and description are copied onto the line at creation,
later catalogue changes do not alter past orders.
Source: `jpa/entities/OrderDetail.java`,
`jpa/services/conversion/impl/OrdersConverterServiceImpl.java`.

### BR-ORDER-012 No stock check occurs at any point
Neither the converter nor the process service reads product stock. Asserting: an
out-of-stock product can be ordered, paid for and completed with no warning, and
no stock value changes as a result of a sale. This is the largest functional gap
in the system.
Source: `jpa/services/conversion/impl/OrdersConverterServiceImpl.java`,
`api/services/impl/OrdersProcessServiceImpl.java`.

### BR-ORDER-013 Applying changes to an existing order through the converter is unsupported
`applyChangesToExistingEntity` raises `This method is deprecated`. Asserting:
order modification goes through the patch service or a full replacement, never
through the converter.
Source: `jpa/services/conversion/impl/OrdersConverterServiceImpl.java`.

## Monetary arithmetic

### BR-ORDER-014 Tax is 19% of the unit value, computed per line
`TAX_PERCENT = 0.19`. For each line the unit tax is the unit value multiplied by
that rate and the unit net is the unit value minus the unit tax. Asserting: the
unit value is **tax-inclusive** — tax is extracted from it, not added to it.
Source: `jpa/services/conversion/impl/OrdersConverterServiceImpl.java`.

### BR-ORDER-015 Totals are integers and accumulate truncated per-line values
Tax, net and total are `int` columns and the accumulation truncates each line's
result. Asserting: the stored tax plus net equals the stored total by
construction, but neither necessarily equals an exact percentage of the gross.
A rewrite must reproduce the truncation order or totals will differ.
Source: `jpa/services/conversion/impl/OrdersConverterServiceImpl.java`,
`database-documentation/00-global-schema.sql`.

### BR-ORDER-016 The total is tax plus net and excludes transport
`totalValue = taxesValue + netValue`. Asserting: `transportValue` is **not**
added, so the total never reflects shipping.
Source: `jpa/services/conversion/impl/OrdersConverterServiceImpl.java`.

### BR-ORDER-017 Transport value is mandatory, stored, and never set
`order_transport_value` is not null, is sortable, and is written by no code path.
Asserting: it is always the default zero. Shipping cost is modelled and
unimplemented.
Source: `jpa/entities/Order.java`,
`jpa/services/conversion/impl/OrdersConverterServiceImpl.java`.

### BR-ORDER-018 Total items counts units, not lines
The accumulator adds each line's unit count. Asserting: three units of one
product yield a total of three.
Source: `jpa/services/conversion/impl/OrdersConverterServiceImpl.java`.

### BR-ORDER-019 Monetary values have no decimal places
All six monetary columns are `int(11)`. Asserting: fractional currency is
unrepresentable; a rewrite must not silently widen these without a rounding
policy. See the monetary note in
`database-documentation/00-global-schema.sql`.
Source: `database-documentation/00-global-schema.sql`.

## The status machine

### BR-ORDER-020 Statuses are compared by name, not by code
Every transition resolves the target status with a name lookup and validates the
current status by name. Asserting: renaming any `order_statuses` row breaks the
corresponding transition at runtime. The source carries a note acknowledging that
codes would be the better comparison.
Source: `api/services/impl/OrdersProcessServiceImpl.java`,
`config/Constants.java`.

### BR-ORDER-021 A missing status row is a data-integrity failure
A status name that resolves to nothing raises an illegal-state failure whose
message reads `No status matches the '<STATUS>' name - Is the database empty or
corrupt?`. Asserting: an unmapped 500-class response.
Source: `api/services/impl/OrdersProcessServiceImpl.java`.

### BR-ORDER-022 Each transition validates the exact current status
Start requires `Pending`; abort, fail and pay require `Payment Started`; confirm
and reject require `Paid, Unconfirmed`; complete requires `Paid, Confirmed`. A
mismatch raises the bad-input message
`The transaction is not in a valid state for this api`. Asserting: the machine is
strict — there are no permissive or idempotent transitions.
Source: `api/services/impl/OrdersProcessServiceImpl.java`.

### BR-ORDER-023 Transitions are identified by the order identifier only
Each transition fetches the existing order through the crud service, which
matches on the client-supplied `buyOrder` value. A null value yields no match and
an empty result raises `No transaction matches given input`.
Source: `api/services/impl/OrdersProcessServiceImpl.java`,
`jpa/services/crud/impl/OrdersCrudServiceImpl.java`.

### BR-ORDER-024 Starting a payment also stores the payment token
The start transition writes the token alongside the status change.
Source: `api/services/impl/OrdersProcessServiceImpl.java`.

### BR-ORDER-025 Four seeded statuses are unreachable
`Delivery On Route`, `Delivery Failed`, `Delivery Cancelled` and `Returned` are
seeded with codes 5, -5, -4 and -6 and are set by **no** code path. Asserting:
delivery tracking, delivery failure and returns are modelled in the data and
absent from the behaviour.
Source: `database-documentation/00-seed-data.json`,
`api/services/impl/OrdersProcessServiceImpl.java`.

### BR-ORDER-026 Status changes are applied by a direct update, not by the aggregate
The status is written through a modifying repository operation that clears the
persistence context, then the order is re-read and converted. A null conversion
raises `Converter could not turn Sell into its Pojo equivalent`.
Source: `api/services/impl/OrdersProcessServiceImpl.java`,
`jpa/repositories/OrdersRepository.java`.

### BR-ORDER-027 The status association is not updatable through the ORM
`orders.order_status_id` is mapped not-updatable, which is why transitions use
the direct update of BR-ORDER-026 rather than setting the field. Asserting: a
rewrite that simply assigns the status will silently fail unless the mapping
changes too.
Source: `jpa/entities/Order.java`.

### BR-ORDER-028 The order status IS patchable despite the mapping
`OrdersPatchServiceImpl` accepts a `status` key, resolves it by name and applies
it. Asserting: an operator with `orders:update` can move an order to **any**
status, bypassing the entire state machine and all of its guards — subject only
to the edit lock of BR-ORDER-029. This is the most significant integrity gap in
the domain.
Source: `jpa/services/patch/impl/OrdersPatchServiceImpl.java`.

### BR-ORDER-029 Processed orders are locked against partial updates
`OrdersCrudServiceImpl.flushPartialChanges` raises
`The requested transaction cannot be modified` when the existing status code is
3 or above, or negative, and
`trebol.api.able-to-edit-orders-after-being-processed` is false (the default).
Asserting: paid, confirmed, rejected, failed and cancelled orders are immutable
by default, while `Pending` (1) and `Payment Started` (2) remain editable.
Source: `jpa/services/crud/impl/OrdersCrudServiceImpl.java`,
`config/ApiProperties.java`.

### BR-ORDER-030 The lock is a configuration toggle, not an invariant
Enabling the property makes every processed order freely patchable, including its
status. Asserting: the toggle is effectively a switch that disables order
immutability system-wide.
Source: `config/ApiProperties.java`.

### BR-ORDER-031 The lock does not cover full replacement
The guard lives in the partial-update path only. Asserting: `PUT /data/orders`
with `orders:update` can overwrite a processed order regardless of the toggle.
Source: `jpa/services/crud/impl/OrdersCrudServiceImpl.java`,
`jpa/services/crud/CrudGenericService.java`.

### BR-ORDER-032 Unknown names on an order patch are silently ignored
`status`, `paymentType` and `shipper` are each resolved by name and applied only
when the lookup succeeds. Asserting: patching `status` to a non-existent status
returns success and changes nothing.
Source: `jpa/services/patch/impl/OrdersPatchServiceImpl.java`.

### BR-ORDER-033 The order patch service reports a truncated error message
Its cast-failure handler raises a bad-input condition whose literal message is
`The ` — an unfinished string. Asserting: a client sending a wrongly-typed value
receives a meaningless message. Defect.
Source: `jpa/services/patch/impl/OrdersPatchServiceImpl.java`.

## Payment integration

### BR-ORDER-034 The payment session identifier is derived from an object hash
The adapter sends the order model's hash code as the session identifier.
Asserting: the value is not stable across processes or runs and is not a
meaningful business identifier.
Source: `payment/impl/webpayplus/WebpayplusPaymentServiceImpl.java`.

### BR-ORDER-035 The payment amount is the order total
Which excludes transport (BR-ORDER-016).
Source: `payment/impl/webpayplus/WebpayplusPaymentServiceImpl.java`.

### BR-ORDER-036 A payment-commit failure is silently treated as a declined payment
The adapter catches the commit failure and returns a non-zero response code
rather than propagating a failure. Asserting: an infrastructure problem during
confirmation is indistinguishable from a genuine decline, and the order is moved
to `Payment Failed`.
Source: `payment/impl/webpayplus/WebpayplusPaymentServiceImpl.java`.

### BR-ORDER-037 A transport-level failure during confirmation is a payment-service failure
A communication failure raises `Webpay failed to confirm the transaction`, which
the checkout controller maps to a 500-class response carrying the raw message.
Asserting: the order stays in `Payment Started` and the buyer has no resolution
path.
Source: `payment/impl/webpayplus/WebpayplusPaymentServiceImpl.java`,
`api/controllers/PublicCheckoutController.java`.

### BR-ORDER-038 Payment creation failures are wrapped
A provider creation failure raises `Webpay could not create a new transaction`.
A not-found condition during the start flow is re-raised as
`The server had a problem requesting the transaction`.
Source: `payment/impl/webpayplus/WebpayplusPaymentServiceImpl.java`,
`api/services/impl/CheckoutServiceImpl.java`.

### BR-ORDER-039 A confirmation in the wrong state becomes an opaque failure
A bad-input condition from the process service is re-raised as
`Transaction could not be confirmed`, with the real state logged separately.
Asserting: the client is not told which state the order was in.
Source: `api/services/impl/CheckoutServiceImpl.java`.

### BR-ORDER-040 The validation callbacks are public and unauthenticated
Both validation routes carry no authority requirement, because the payment
provider calls them. Asserting: anyone who learns a payment token can drive the
corresponding order's confirmation or abortion. The token is 64 characters and
indexed, but there is no signature check, no provider-origin check and no replay
protection.
Source: `api/controllers/PublicCheckoutController.java`.

### BR-ORDER-041 The validation lookup requires the `Payment Started` status
The token filter includes the status, so a replayed callback on an
already-confirmed order finds no match and raises
`No sell matches the filtering conditions`. Asserting: this is the de-facto —
and only — replay protection.
Source: `api/services/impl/CheckoutServiceImpl.java`.

### BR-ORDER-042 Both callbacks redirect rather than respond with data
Each responds `303` with a `Location` built from the configured browser
redirection URL plus the token as a query parameter. A malformed URL raises
`Transaction was confirmed, but server had an unexpected malfunction`.
Source: `api/controllers/PublicCheckoutController.java`,
`api/services/impl/CheckoutServiceImpl.java`.

### BR-ORDER-043 The success callback is a `GET` and the abort callback is a `POST`
And they read different parameter names (`token_ws` versus `TBK_TOKEN`), each
raising `No transaction token was provided` when absent. Asserting: the asymmetry
is dictated by the payment provider and must be preserved verbatim.
Source: `api/controllers/PublicCheckoutController.java`,
`config/Constants.java`.

## Receipt and notification

### BR-ORDER-044 The receipt is public and token-addressed
`GET /public/receipt/{token}` has no authority requirement. Asserting: anyone
with a token can read the order's totals, date, status and line descriptions.
Source: `api/controllers/PublicReceiptController.java`.

### BR-ORDER-045 Receipt lookup by token is not status-restricted
Unlike the validation lookup, the receipt resolves on the token alone.
Asserting: a receipt is readable at any point after the token is issued,
including while the order is still `Payment Started`.
Source: `api/services/impl/ReceiptServiceImpl.java`.

### BR-ORDER-046 A blank or unknown receipt token is rejected distinctly
Blank raises `An incorrect receipt token was provided`; unknown raises
`The transaction could not be found, no receipt can be created`.
Source: `api/controllers/PublicReceiptController.java`,
`api/services/impl/ReceiptServiceImpl.java`.

### BR-ORDER-047 The receipt exposes only the product name and barcode per line
Each line carries a trimmed product projection plus the unit value. Asserting:
stock, description, category and images are not exposed on the receipt.
Source: `api/services/impl/ReceiptServiceImpl.java`.

### BR-ORDER-048 The receipt's status is attached separately from the conversion
The framework converter maps the identifier, date, transport value, tax, total
items, total and token but **not** the status or the lines; the service sets both
afterwards. Asserting: a rewrite must not assume the converter is complete.
Source: `common/converters/OrderEntity2ReceiptPojo.java`,
`api/services/impl/ReceiptServiceImpl.java`.

### BR-ORDER-049 Customer mail fires on exactly four statuses
`Paid, Unconfirmed`, `Paid, Confirmed`, `Rejected` and `Delivery Complete`. No
mail is sent for `Pending`, `Payment Started`, `Payment Cancelled` or
`Payment Failed`.
Source: `mailing/impl/mailgun/MailgunMailingServiceImpl.java`.

### BR-ORDER-050 Owner mail fires on exactly three statuses
`Paid, Confirmed`, `Rejected` and `Delivery Complete`. Asserting: the owner is
not notified of a new payment, only of its confirmation.
Source: `mailing/impl/mailgun/MailgunMailingServiceImpl.java`.

### BR-ORDER-051 An unmapped status produces no mail and no error
The notification lookup returns silently when the status has no template.
Source: `mailing/impl/mailgun/MailgunMailingServiceImpl.java`.

### BR-ORDER-052 Mailing is optional at every call site
The mailing component is injected optionally and null-checked before each use.
Asserting: with no mail provider configured the whole order flow works and no
notification is attempted.
Source: `api/controllers/PublicCheckoutController.java`,
`api/controllers/DataOrdersController.java`.

### BR-ORDER-053 Mail subjects are suffixed with the order identifier
The configured subject is followed by the order identifier in brackets.
Source: `mailing/impl/mailgun/MailgunMailingServiceImpl.java`.

### BR-ORDER-054 A mail response without an identifier is a failure
A blank provider identifier raises `Status of the sent e-mail is unknown, Mailgun
did not provide an ID for this api`; a malformed response raises the related
validation failure. Asserting: neither is mapped by the global error advice, so a
notification problem surfaces as a 500-class response **after** the order
transition has already been committed.
Source: `mailing/impl/mailgun/MailgunMailingServiceImpl.java`,
`api/ExceptionsControllerAdvice.java`.

### BR-ORDER-055 Notification is not transactional with the transition
Each action route performs the transition first and then sends mail. Asserting: a
mail failure cannot roll back the status change, and there is no retry — the
notification is simply lost.
Source: `api/controllers/DataOrdersController.java`.

## Addresses and payment types

### BR-ORDER-056 An address is identified by all six of its fields
`UK_ADDR` spans city, municipality, first line, second line, postal code and
notes, and the lookup matches all six. Asserting: two addresses differing only in
their notes are distinct rows, and a reused address is shared by reference
between orders.
Source: `database-documentation/00-global-schema.sql`,
`jpa/repositories/AddressesRepository.java`.

### BR-ORDER-057 Addresses are immutable once referenced
`orders.billing_address_id` and `orders.shipping_address_id` are both mapped
not-updatable, and no route exposes addresses. Asserting: an address row is
created at checkout and never changed; correcting a typo creates a new row.
Source: `jpa/entities/Order.java`.

### BR-ORDER-058 Addresses have no API surface
No controller, crud service, patch service or predicate service exists for
addresses — only a converter and a six-field repository lookup. Asserting:
addresses cannot be listed, searched or corrected, and no authority code covers
them.
Source: absence of any address controller or crud service.

### BR-ORDER-059 Shipping and billing addresses are both optional
Both columns are nullable. Asserting: an order can be placed with no address at
all; the enterprise billing branch is the only path that guarantees a billing
address.
Source: `jpa/entities/Order.java`.

### BR-ORDER-060 Payment types have no API surface either
No controller or crud service exists; the catalogue is read by name from the
order converter and the order patch service only. Asserting: the single seeded
payment type is effectively hardcoded, and no authority code covers payment
types.
Source: absence of any payment-type controller or crud service.

## Access and administration

### BR-ORDER-061 All order administration requires authentication plus an authority
`orders:read`, `orders:create`, `orders:update` (replace, patch and all three
action routes) and `orders:delete`. Seeded holders: Administrator all four;
Manager read and update; Salesperson create and read. Asserting: a Salesperson
can create an order but not change its status; a Manager can change status but
not create.
Source: `api/controllers/DataOrdersController.java`,
`database-documentation/00-seed-data.json`.

### BR-ORDER-062 Checkout requires the standalone `checkout` authority
The only authority code in the catalogue without a resource prefix, declared as a
constant and granted to every seeded role including Customer. Asserting: it is
the one authority a buyer actually holds.
Source: `api/controllers/PublicCheckoutController.java`,
`config/Constants.java`, `database-documentation/00-seed-data.json`.

### BR-ORDER-063 The order search defaults its sort
When no sort parameter is supplied the order listing defaults to `buyOrder`
descending — newest first. Asserting: the only endpoint in the system with a
default sort.
Source: `api/controllers/DataOrdersController.java`.

### BR-ORDER-064 A `buyOrder` parameter is special-cased into a single-item page
Rather than going through the normal paged path. Asserting: the response is still
a page envelope, containing at most one order.
Source: `api/controllers/DataOrdersController.java`.

### BR-ORDER-065 The status catalogue is read-only
`DataOrderStatusesController` maps only a read method, behind
`order_statuses:read` and class-level authentication. Asserting: statuses cannot
be added or renamed through the API — which, given BR-ORDER-020, is a protective
accident rather than a design.
Source: `api/controllers/DataOrderStatusesController.java`.

### BR-ORDER-066 A blank status name is rejected by the status crud service
`OrderStatusesCrudServiceImpl.getExisting` raises `Invalid status name`.
Asserting: reachable only through the unexposed write paths.
Source: `jpa/services/crud/impl/OrderStatusesCrudServiceImpl.java`.

### BR-ORDER-067 Deleting an order does not delete its lines
`Order.details` cascades persist only, and the order delete performs no line
cleanup. Asserting: deleting an order leaves orphaned `order_details` rows —
unlike `product-list`, which cleans up its child table.
Source: `jpa/entities/Order.java`,
`jpa/services/crud/impl/OrdersCrudServiceImpl.java`.

### BR-ORDER-068 Order lines reference their order through a read-only column
`OrderDetail.order` is mapped neither insertable nor updatable; the line is
written through the parent's join column instead. Asserting: a line cannot be
re-pointed at a different order.
Source: `jpa/entities/OrderDetail.java`.

### BR-ORDER-069 The order's single-item read hydrates addresses and lines
`OrdersCrudServiceImpl.readOne` explicitly loads the billing address, the
shipping address and the converted lines; the paged read does not. Asserting: the
paged listing returns orders without line detail.
Source: `jpa/services/crud/impl/OrdersCrudServiceImpl.java`.

### BR-ORDER-070 A malformed date filter is ignored rather than rejected
The order predicate service parses the `date` parameter as an instant and logs a
warning on failure, dropping the condition. Asserting: `?date=yesterday` returns
an unfiltered page.
Source: `jpa/services/predicates/impl/OrdersPredicateServiceImpl.java`.
