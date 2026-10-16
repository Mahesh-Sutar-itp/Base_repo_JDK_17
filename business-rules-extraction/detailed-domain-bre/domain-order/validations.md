# Validations — Order, Checkout & Fulfilment (`order`)

## Transport-model validation

### `OrderPojo`

| Field | Constraint |
|---|---|
| `details` | `@Valid @NotEmpty` — non-empty **and** each line validated |
| `paymentType` | `@NotBlank` |
| `customer` | `@Valid` — validated when present, but **not** required |
| `buyOrder`, `token`, `date`, `status`, `billingType`, `salesperson`, `shipper`, `billingCompany`, `billingAddress`, `shippingAddress`, and all five monetary fields | **none** |

### `OrderDetailPojo`

| Field | Constraint |
|---|---|
| `units` | `@Min(1)` |
| `product` | `@NotNull` |
| `unitValue`, `description` | none — both are server-generated anyway |

### `OrderStatusPojo`

| Field | Constraint |
|---|---|
| `code` | `@NotBlank` applied to an `Integer` — **invalid**: the non-blank constraint does not support a numeric type and will fail at validator resolution if ever exercised |
| `name` | `@NotBlank` |

### `AddressPojo`

| Field | Constraint |
|---|---|
| `firstLine`, `municipality`, `city` | `@NotBlank` |
| `secondLine`, `postalCode`, `notes` | none |

Critically, `OrderPojo` does **not** cascade validation into its address or
billing blocks, so those non-blank constraints never run during checkout.

### `ReceiptPojo`, `ReceiptDetailPojo`, `PaymentRedirectionDetailsPojo`

No constraints — all outbound only.

Sources: `api/models/`.

## Persistence-layer validation

| Column | Constraint |
|---|---|
| `order_date` | not null, creation timestamp, indexed |
| `order_total_items`, `order_net_value`, `order_taxes_value`, `order_total_value`, `order_transport_value` | not null, `int(11)`, **no range check** |
| `order_transaction_token` | nullable, `varchar(64)`, ORM requires **exactly** 64 characters |
| `orders.customer_id`, `payment_type_id`, `order_status_id`, `billing_type_id` | not null |
| `orders.billing_company_id`, `billing_address_id`, `shipper_id`, `shipping_address_id`, `salesperson_id` | nullable |
| `order_detail_units`, `order_detail_unit_value` | not null, `int(11)` |
| `order_detail_description` | not null, max 260 |
| `order_status_code`, `order_status_name` | not null, each unique |
| `addresses` six business columns | composite unique; city, municipality and first line not null |
| `payment_type_name` | not null, unique, max 100 |

Mapping restrictions that act as validation: `payment_type_id`,
`order_status_id`, `billing_type_id`, `billing_address_id`, `shipper_id` and
`shipping_address_id` are all **not updatable**, and the order reference on a
line is neither insertable nor updatable.

## Service-layer validation

| Check | Component | Failure |
|---|---|---|
| payment type present | `OrdersConverterServiceImpl` | bad input, `A payment type has to be included` |
| payment type exists | same | **unchecked** failure, `The payment type does not exist` |
| billing type exists | same | bad input, `Specified billing type does not exist` |
| shipper exists when supplied | same | bad input, `Specified shipper does not exist` |
| line barcode present | same | bad input, `Product barcode must be valid` |
| line product exists | same | bad input, `Unexisting product in sell details` (re-wrapped as unchecked) |
| applying changes via the converter | same | unsupported, `This method is deprecated` |
| order exists for a transition | `OrdersProcessServiceImpl` | not found, `No transaction matches given input` |
| current status is valid for the transition | same | bad input, `The transaction is not in a valid state for this api` |
| target status row exists | same | illegal state, `No status matches the '<STATUS>' name - Is the database empty or corrupt?` |
| conversion after a transition succeeds | same | illegal state, `Converter could not turn Sell into its Pojo equivalent` |
| order is not locked for patching | `OrdersCrudServiceImpl` | bad input, `The requested transaction cannot be modified` |
| order resolvable by filter | same | not found, `No sell matches the filtering conditions` |
| patched value types are correct | `OrdersPatchServiceImpl` | bad input, literal message `The ` (**truncated — defect**) |
| status name present | `OrderStatusesCrudServiceImpl` | bad input, `Invalid status name` |
| payment token present on a callback | `PublicCheckoutController` | bad input, `No transaction token was provided` |
| receipt token present | `PublicReceiptController` | bad input, `An incorrect receipt token was provided` |
| receipt token resolvable | `ReceiptServiceImpl` | not found, `The transaction could not be found, no receipt can be created` |
| payment page creation | `WebpayplusPaymentServiceImpl` | payment failure, `Webpay could not create a new transaction` |
| payment confirmation transport | same | payment failure, `Webpay failed to confirm the transaction` |
| payment start wrapping | `CheckoutServiceImpl` | illegal state, `The server had a problem requesting the transaction` |
| confirmation in a wrong state | same | illegal state, `Transaction could not be confirmed` |
| redirection URL well-formed | same | illegal state, `Transaction was confirmed, but server had an unexpected malfunction` |
| mail response carries an identifier | `MailgunMailingServiceImpl` | mailing failure, `Status of the sent e-mail is unknown, Mailgun did not provide an ID for this api` |

## Error-mapping coverage

The global advice maps four conditions only (not-found, already-exists,
bad-input, framework validation). Everything else in the table above surfaces as
a 500-class response, with one local exception: the checkout controller maps the
payment failure to a 500 carrying the raw provider message.

Conditions that are arguably client errors but return 500:

- `The payment type does not exist`
- `Transaction could not be confirmed`
- `No status matches the '<STATUS>' name ...`
- every mailing failure — raised **after** the status change is committed

## Gaps

- **No stock or availability validation anywhere.** An out-of-stock product is
  ordered, paid for and completed silently (BR-ORDER-012).
- **The status machine is bypassable by patching.** A `status` key on a patch
  request moves an order to any status with no guard, subject only to the edit
  lock (BR-ORDER-028).
- **The edit lock does not cover full replacement**, so a processed order can be
  overwritten with `PUT` regardless of the configuration toggle.
- **The public validation callbacks have no authenticity check.** No signature
  verification, no provider-origin check and no idempotency key; the only replay
  protection is the incidental status condition in the lookup (BR-ORDER-040,
  BR-ORDER-041).
- **The receipt is readable by token with no status restriction**, exposing order
  totals and line descriptions to anyone holding a token.
- **Addresses and billing blocks are never validated during checkout**, because
  `OrderPojo` does not cascade validation into them — the non-blank constraints
  on the address model are dead.
- **`transportValue` is mandatory, sortable and never set**, and is excluded from
  the total — shipping cost is modelled and unimplemented.
- **A payment-commit infrastructure failure is indistinguishable from a decline**
  and silently marks the order `Payment Failed`.
- **Notification failures are not transactional** with the transition and are
  never retried.
- **Status comparison is by name**, so renaming a status row breaks transitions
  at runtime; the status catalogue being read-only is the only thing preventing
  this.
- **Monetary truncation order is load-bearing.** The integer accumulation of
  per-line truncated values must be reproduced exactly or totals will differ.
- **`OrderStatusPojo.code` carries an invalid constraint** for its type.
- **The patch service's type-failure message is truncated** to `The `.
- **Deleting an order orphans its lines** — no cleanup and no cascade.
