# Domain: Order, Checkout & Fulfilment (`order`)

## Responsibility

Owns the commercial transaction end to end: the order header and its lines, the
status lifecycle, the checkout flow, the Webpay Plus payment integration, the
public receipt lookup, the order-status mail notifications, and the postal
addresses and payment-method catalogue that only orders use.

This is the system's core domain and the only one with a real state machine.

## Bounded context summary

Transaction context. `Order` is the aggregate root: it transactionally governs
its lines (`OrderDetail`) and its status transitions, and it is the operational
owner of `addresses` and `payment_types` — neither of which has a CRUD service,
a controller or any other reader. Customers, products, shippers, billing types
and billing companies are referenced across boundaries and owned elsewhere.

## Owned data

| Table | Entity | Source file | Ownership basis |
|---|---|---|---|
| `orders` | `Order` | `jpa/entities/Order.java` | aggregate root |
| `order_details` | `OrderDetail` | `jpa/entities/OrderDetail.java` | governed by the order's lifecycle |
| `order_statuses` | `OrderStatus` | `jpa/entities/OrderStatus.java` | the order status machine's vocabulary; read only by this domain |
| `addresses` | `Address` | `jpa/entities/Address.java` | **operational owner** — rows are created only during checkout and read only through an order |
| `payment_types` | `PaymentType` | `jpa/entities/PaymentType.java` | **operational owner** — no CRUD service, no controller, read only by the order converter and patch service |

Ownership of `addresses` and `payment_types` is justified in
`domain-decomposition-overview/data-ownership.md`.

### `Order` fields

| Field | Column | Nullable | Notes |
|---|---|---|---|
| `id` | `order_id` | no | exposed to clients as `buyOrder` |
| `date` | `order_date` | no | creation timestamp, indexed |
| `totalItems` | `order_total_items` | no | `int`, computed |
| `netValue` | `order_net_value` | no | `int`, computed |
| `taxesValue` | `order_taxes_value` | no | `int`, computed |
| `totalValue` | `order_total_value` | no | `int`, computed |
| `transportValue` | `order_transport_value` | no | `int`, **never computed or set by any code** |
| `transactionToken` | `order_transaction_token` | yes | `@Size(min=64,max=64)`, indexed |

Associations: `customer` (mandatory), `paymentType` (mandatory, not updatable),
`status` (mandatory, not updatable), `billingType` (mandatory, not updatable),
`billingCompany`, `billingAddress` (not updatable), `shipper` (not updatable),
`shippingAddress` (not updatable), `salesperson`, and `details` as a
unidirectional one-to-many with a persist cascade.

### `OrderDetail` fields

`units` and `unitValue` (both `int`, mandatory), `description`
(`@Size(max=260)`, mandatory), plus mandatory `product` and `order` references.
Both foreign keys are declared with no-constraint mode in the ORM while the DDL
does define them — see the divergence notes in
`database-documentation/00-global-schema.sql`.

### `Address` fields

`city`, `municipality`, `firstLine` (all mandatory), `secondLine`, `postalCode`,
`notes` (all optional), with a six-column composite uniqueness constraint and
three indexes. No relationships.

## Components

### REST surface
| Component | Route | Access |
|---|---|---|
| `DataOrdersController` | `/data/orders` + three action sub-routes | authenticated, `orders:*` |
| `DataOrderStatusesController` | `/data/order_statuses` | authenticated, `order_statuses:read` |
| `PublicCheckoutController` | `/public/checkout`, `/public/checkout/validate` | `checkout` authority on submit; validation routes **public** |
| `PublicReceiptController` | `/public/receipt/{token}` | **public** |

### Services
`CheckoutServiceImpl`, `OrdersProcessServiceImpl` (the state machine),
`ReceiptServiceImpl`, `OrdersCrudServiceImpl`, `OrderStatusesCrudServiceImpl`,
`OrdersConverterServiceImpl` (the largest converter in the codebase, including
all monetary arithmetic), `AddressesConverterServiceImpl`, plus the
patch/predicate/sort components for orders and order statuses.

### Integrations
`WebpayplusPaymentServiceImpl` (Transbank SDK),
`MailgunMailingServiceImpl` (HTTP, profile-gated),
`OrderEntity2ReceiptPojo` and `OrderDetailEntity2ReceiptDetailPojo` converters.

### Repositories
`OrdersRepository` (token lookup, join-fetching read, status setter, token
setter), `OrderDetailsRepository`, `OrderStatusesRepository`,
`AddressesRepository` (six-field lookup), `PaymentTypesRepository`.

## Configuration

| Key | Default | Effect |
|---|---|---|
| `trebol.api.able-to-edit-orders-after-being-processed` | false | unlocks editing of processed orders |
| `trebol.payment.webpayplus.production` | false | switches from the payment sandbox to live credentials |
| `trebol.payment.webpayplus.callback-url` | local validation route | where the payment provider returns the buyer |
| `trebol.payment.webpayplus.browser-redirection-url` | local receipt page | where the buyer is finally redirected |
| `trebol.mailing.*` | mostly blank | notification subjects, owner and sender addresses, date format |
| `trebol.mailing.mailgun.*` | blank | API key, domain and seven template names |

## Dependencies

- **Depends on**: `customer` (mandatory, service-level), `product` (mandatory,
  repository-level), `billing` (mandatory billing type, optional company),
  `shipper` (optional), `salesperson` (modelled but never populated).
- **Depended on by**: nothing. No domain reads orders.

## Shared entity status

`Order`, `OrderDetail`, `OrderStatus`, `Address` and `PaymentType` are **not**
shared — nothing outside this domain reads them. The shared entities consumed
here are `Customer`, `Product`, `BillingType`, `BillingCompany`, `Shipper` and
`Salesperson`.
See `cross-cutting-documentation/shared-entities.md`.
