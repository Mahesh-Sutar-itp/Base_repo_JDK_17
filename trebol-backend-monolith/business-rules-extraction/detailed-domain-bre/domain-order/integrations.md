# Integrations — Order, Checkout & Fulfilment (`order`)

This is the only domain in the system with **outbound network integrations**.
Everywhere else in this codebase, "integration" means a call to another domain.

## External system 1 — Transbank Webpay Plus (payment)

| Aspect | Detail |
|---|---|
| Component | `WebpayplusPaymentServiceImpl` implementing `PaymentService` |
| Transport | the provider's Java SDK (`transbank-sdk-java`), synchronous |
| Configuration | `trebol.payment.webpayplus.*` — `production`, `commerceCode`, `apiKey`, `callbackUrl`, `browserRedirectionUrl` |
| Default mode | the provider's **integration sandbox** with its published test commerce code and API key; live credentials and live mode are used only when `production` is true |
| Operation 1 | create a payment page — sends the order identifier as the buy order, the order model's **hash code** as the session identifier, the order total as the amount, and the configured callback URL; returns a redirection URL and a token |
| Operation 2 | commit a payment — sends the token, returns a response code; **zero means paid** |
| Operation 3 | expose the browser redirection URL used to build the final `303` `Location` |
| Failure: creation | logged, then raised as `Webpay could not create a new transaction` |
| Failure: commit | **swallowed** — a commit failure returns a non-zero response code, so an infrastructure problem is treated as a declined payment and the order is marked `Payment Failed` |
| Failure: transport | raised as `Webpay failed to confirm the transaction`, mapped locally to a 500-class response with the raw message |
| Retry policy | **none** |
| Idempotency | **none** on the outbound calls; the inbound callbacks are protected only incidentally (see below) |
| Amount basis | the order total, which **excludes** transport value |

### Inbound callbacks from the provider

| Route | Method | Parameter | Effect |
|---|---|---|---|
| `/public/checkout/validate` | `GET` | `token_ws` | confirm: paid or failed |
| `/public/checkout/validate` | `POST` | `TBK_TOKEN` | abort: payment cancelled |

Both are **public and unauthenticated** because the provider calls them. There
is no signature verification, no provider-origin check and no idempotency key.
The only replay protection is that the order lookup filters on status
`Payment Started`, so a second callback for an already-confirmed order finds
nothing and raises `No sell matches the filtering conditions`.

## External system 2 — Mailgun (notification)

| Aspect | Detail |
|---|---|
| Component | `MailgunMailingServiceImpl` implementing `MailingService` |
| Transport | plain HTTP via the Unirest client, synchronous |
| Endpoint | `https://api.mailgun.net/v3/{domain}/messages`, basic authentication with the API key |
| Activation | gated on the `mailgun` profile; the service bean exists only when that profile is active |
| Optionality | injected optionally and null-checked at every call site, so the order flow works with no mail provider at all |
| Request shape | multipart fields `from`, `to`, `subject`, `template` and a Mailgun variables header |
| Variables payload | a JSON object carrying a receipt projection of the order and the customer's person data |
| Configuration | `trebol.mailing.*` for sender, owner, date format and seven subjects; `trebol.mailing.mailgun.*` for the API key, domain and seven template names — **all blank in the shipped files** |
| Recipient (customer) | assembled as first name, last name and e-mail from the order's customer |
| Recipient (owner) | the configured owner address |
| Success check | the provider response must contain an identifier; a blank one raises `Status of the sent e-mail is unknown, Mailgun did not provide an ID for this api`, a malformed response raises the related validation failure |
| Retry policy | **none** |
| Transactionality | **none** — the status transition is committed before mail is attempted, so a failure loses the notification and cannot roll the transition back |

### Notification trigger matrix

| Order status | Customer | Owner |
|---|---|---|
| `Pending` | — | — |
| `Payment Started` | — | — |
| `Payment Cancelled` | — | — |
| `Payment Failed` | — | — |
| `Paid, Unconfirmed` | payment template | — |
| `Paid, Confirmed` | confirmation template | confirmation template |
| `Rejected` | rejection template | rejection template |
| `Delivery Complete` | completion template | completion template |

An unmapped status produces no mail and no error.

Note a discrepancy between configuration and call sites: owner rejection and
owner completion subjects are configured (and marked "not used" in the
properties file), and the only route that actually notifies the owner is the
confirmation route. Rejection and completion notify the customer only.

## Inbound integrations from other domains

**None.** No domain reads orders, order lines, order statuses, addresses or
payment types. This domain is a pure consumer.

## Outbound integrations to other domains

| Target domain | Entry point | Coupling style |
|---|---|---|
| `customer` | `CustomersCrudService.getExisting` / create | **service-level** (clean) |
| `product` | `ProductsRepository.findByBarcode` | **repository-level** (leak) |
| `billing` | `BillingTypesRepository.findByName`, `BillingCompaniesRepository` | **repository-level** (leak) |
| `shipper` | `ShippersRepository.findByName` | **repository-level** (leak) |
| `salesperson` | the order entity's association with a persist/merge cascade | **modelled but never populated** |

Four of the five are repository-level and are the main extraction work for this
domain. Recorded in `reference-documentation/coupling-hotspots.md`.

| Shared-kernel target | Nature |
|---|---|
| generic CRUD base | order and status create/read/update/patch/delete, with four overridden hooks |
| generic controller bases | the two data controllers |
| pagination service, sort-spec parser | listing behaviour |
| framework conversion service | the receipt projection converters |
| configuration holders | the edit toggle, payment and mailing properties |
| error advice | the four mapped conditions |

## Events

**None.** No event is published when an order is created, paid, confirmed,
rejected or completed. The notification mails are sent **synchronously and
inline** from the controller methods, which is precisely the pattern an event
bus would replace. A rewrite introducing order events has no existing pattern to
extend — see `cross-cutting-documentation/event-publishing-patterns.md`.

## Scheduled work

**None.** There is no job to:

- expire orders stuck in `Payment Started` after an abandoned checkout;
- reconcile against the payment provider;
- retry failed notifications;
- advance delivery states (which is consistent — those states are unreachable).

Abandoned checkouts therefore remain in `Payment Started` indefinitely, and
because that status is editable by default, they stay patchable.

## Caching

**None.**
