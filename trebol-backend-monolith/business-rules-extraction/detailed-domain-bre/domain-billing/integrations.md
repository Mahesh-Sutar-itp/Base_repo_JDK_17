# Integrations — Billing (`billing`)

## External systems

**None.** This is the most consequential absence in the whole system for anyone
reading "billing" as "invoicing": there is no tax-authority integration, no
electronic invoice submission, no accounting-system export, no document (PDF or
otherwise) generation and no numbering sequence beyond the order identifier. The
`Enterprise Invoice` billing type records an intent; nothing acts on it.

A modernisation effort must treat invoice issuance as **new** functionality.

## Inbound integrations

| Caller domain | Entry point | Purpose |
|---|---|---|
| `order` | `BillingTypesRepository.findByName` via `OrdersConverterServiceImpl` | resolve the billing branch for a new order |
| `order` | `BillingCompaniesRepository.findByIdNumber` / save via `OrdersConverterServiceImpl` | resolve or create the invoiced company |
| `order` | `AddressesRepository.findByFields` via `OrdersConverterServiceImpl` | resolve or create the company's billing address (address rows are owned by `order`) |

All three reach repositories directly rather than going through this domain's crud
services — recorded in `reference-documentation/coupling-hotspots.md`.

## Outbound integrations

| Target | Nature |
|---|---|
| shared pagination service | page index/size resolution (billing types only) |
| shared sort-spec parser | `sortBy` / `order` resolution (billing types only) |
| shared generic CRUD base | create/read/update/patch/delete behaviour |
| shared persistence abstraction | paged + predicate query execution |

No business-domain dependency.

## Events

**None.** Creating a billing company during checkout publishes nothing. No
accounting or finance component is notified.

## Scheduled work

**None.** No periodic invoice batch, no end-of-day close, no reconciliation job.

## Downstream data consumers

The receipt-lookup feature in the `order` domain and the Mailgun notification
templates serialise an order (including its totals and tax value) for the
customer. Neither includes billing-company or billing-type information:
`ReceiptPojo` carries only buy-order id, date, status, token, totals and tax
value. Asserting: the invoiced company never reaches the customer-facing receipt.

## Anti-corruption surface

Both entities have dedicated converters and patch services, giving a clean
transport/persistence separation **on paper**. In practice the only live consumer
(`order`) bypasses them and uses the repositories, so there is no effective
anti-corruption layer on the inbound side. Extracting `billing` requires
introducing a billing-classification lookup contract plus a company-registration
contract that the `order` domain calls instead. See
`domain-decomposition-overview/anti-corruption-layers.md`.
