# Features — Order, Checkout & Fulfilment (`order`)

**Checkout and payment**

## F-001 Submit a cart and start a payment
`POST /public/checkout`, authority `checkout`. Creates the order (status
`Pending`), asks the payment provider for a new payment page, copies the returned
token onto the order, advances it to `Payment Started`, and returns the
redirection URL and token to the client.

## F-002 Confirm a successful payment (provider callback)
`GET /public/checkout/validate` — **public**, no authority. Requires the query
parameter `token_ws`. Resolves the order by token, requests the payment result,
and marks the order `Paid, Unconfirmed` on a zero response code or
`Payment Failed` otherwise. Sends the customer notification if mailing is
configured, then responds `303` with a `Location` header pointing at the
storefront receipt page.

## F-003 Record an aborted payment (provider callback)
`POST /public/checkout/validate` — **public**, no authority. Requires the query
parameter `TBK_TOKEN`. Marks the order `Payment Cancelled` and responds `303`
with the same redirection.

## F-004 Resolve an order by payment token
Internal lookup used by both validation routes: a filter of status
`Payment Started` plus the supplied token. No match raises
`No sell matches the filtering conditions`.

## F-005 Sandbox or live payment credentials
The payment adapter defaults to the provider's integration sandbox and switches
to the configured commerce code, API key and live mode only when
`trebol.payment.webpayplus.production` is true.

**Order lifecycle**

## F-006 Seven explicit status transitions
`OrdersProcessServiceImpl` exposes one operation per transition — start, abort,
fail, pay, confirm, reject, complete — each validating the order's current status
before advancing it. See `workflows.md` for the full table.

## F-007 Operator-driven confirmation, rejection and completion
Three dedicated routes under `/data/orders` (`/confirmation`, `/rejection`,
`/completion`), all behind `orders:update`, which drive the corresponding
transitions and trigger notifications.

## F-008 Order-line hydration on payment transitions
Four of the seven transitions re-read the order's lines and populate them on the
returned model so that notification templates have full line detail.

## F-009 Edit locking after processing
`OrdersCrudServiceImpl` refuses partial updates once the order's status code is
3 or above, or negative, unless
`trebol.api.able-to-edit-orders-after-being-processed` is enabled.

**Order administration**

## F-010 Paged order search with a single-item shortcut
`GET /data/orders`, authority `orders:read`. A `buyOrder` parameter is
special-cased into a single-item page. Defaults the sort to `buyOrder`
descending — the only endpoint in the system with a default sort.

## F-011 Order filtering
Keys: `id` and `buyOrder` (aliases, both short-circuiting on the order
identifier), `date` (parsed as an instant, parse failures logged and ignored),
`statusName` and `token`.

## F-012 Order sorting
`OrdersSortSpec` whitelists `buyOrder`, `date`, `status` (by status code),
`customer` (by the customer's last name), `shipper` (by name), `totalValue`,
`netValue`, `totalItems` and `transportValue`.

## F-013 Create, replace, patch and delete an order
Standard generic CRUD behind `orders:create`, `orders:update` and
`orders:delete`, with the edit lock of F-009 applied to patching.

## F-014 Order patching of four fields
`OrdersPatchServiceImpl` handles `date` (parsed as an instant), `status`,
`paymentType` and `shipper` — the last three resolved by name, with unknown names
silently ignored.

## F-015 Order status catalogue read
`GET /data/order_statuses`, authority `order_statuses:read`, authenticated.
Read-only: the status catalogue has no write route.

**Receipt and notification**

## F-016 Public receipt lookup by token
`GET /public/receipt/{token}` — **public**. A blank token raises
`An incorrect receipt token was provided`; an unknown token raises
`The transaction could not be found, no receipt can be created`. The response
carries the order totals, date, status name and trimmed line data.

## F-017 Customer notifications on four statuses
Mail is sent to the customer on `Paid, Unconfirmed`, `Paid, Confirmed`,
`Rejected` and `Delivery Complete`.

## F-018 Owner notifications on three statuses
Mail is sent to the configured owner address on `Paid, Confirmed`, `Rejected`
and `Delivery Complete`.

## F-019 Optional mailing
The mailing component is injected optionally and null-checked at every call site,
so the system runs correctly with no mail provider configured.

**Monetary calculation**

## F-020 Tax-inclusive line arithmetic
`OrdersConverterServiceImpl` computes, per line, a unit tax at 19% of the unit
value and a unit net as the remainder, accumulating integer tax, net and unit
totals, with the order total as tax plus net.

## F-021 Server-side price authority
Each line's unit value is overwritten with the product's current price, and the
line description is generated from the unit count and product name. Client-supplied
values are ignored.

**Address and payment-type handling**

## F-022 Address de-duplication by full field match
Addresses are resolved by matching all six fields; an existing row is reused,
otherwise a new one is created. Applies to both the billing and the shipping
address.

## F-023 Payment type resolution by name
A blank payment type raises `A payment type has to be included`; an unknown one
raises `The payment type does not exist`.

**Features deliberately absent**

| Expected capability | Status |
|---|---|
| stock check, reservation or decrement | **absent** — see `domain-product/business-rules.md` BR-PRODUCT-022 |
| transport/shipping cost calculation | **absent** — `transportValue` is stored, included in the total, and never set by any code |
| discounts, coupons or promotions | **absent** |
| refunds or partial cancellation | **absent** — the four negative-code statuses for delivery failure and return are unreachable |
| the delivery-in-transit transition | **absent** — `Delivery On Route` is seeded and never set |
| order cancellation by the customer | **absent** |
| invoice or receipt document generation | **absent** — the receipt is a JSON projection |
| idempotency on the public validation callbacks | **absent** |
