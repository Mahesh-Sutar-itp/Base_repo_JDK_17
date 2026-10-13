# Workflows — Order, Checkout & Fulfilment (`order`)

## The status machine

### Transition table

| Operation | Required current status | New status | Side effects |
|---|---|---|---|
| start payment | `Pending` (1) | `Payment Started` (2) | stores the payment token |
| abort payment | `Payment Started` (2) | `Payment Cancelled` (-1) | — |
| fail payment | `Payment Started` (2) | `Payment Failed` (-2) | — |
| mark paid | `Payment Started` (2) | `Paid, Unconfirmed` (3) | re-reads and attaches the order lines |
| confirm | `Paid, Unconfirmed` (3) | `Paid, Confirmed` (4) | re-reads and attaches the order lines |
| reject | `Paid, Unconfirmed` (3) | `Rejected` (-3) | re-reads and attaches the order lines |
| complete | `Paid, Confirmed` (4) | `Delivery Complete` (6) | re-reads and attaches the order lines |

Every operation follows the same five steps:

1. fetch the existing order by the client-supplied `buyOrder` identifier; an
   empty result raises `No transaction matches given input`;
2. validate the current status **by name**; a mismatch raises
   `The transaction is not in a valid state for this api`;
3. resolve the target status by name; a missing row raises
   `No status matches the '<STATUS>' name - Is the database empty or corrupt?`;
4. write the status through a modifying repository update that clears the
   persistence context;
5. re-read the order, convert it, and attach the status name to the returned
   model; a null conversion raises
   `Converter could not turn Sell into its Pojo equivalent`.

### Unreachable statuses

`Delivery On Route` (5), `Delivery Failed` (-5), `Delivery Cancelled` (-4) and
`Returned` (-6) are seeded and set by no code path. Delivery tracking, delivery
failure and returns are modelled in the data and absent from the behaviour.

### The patch bypass

`PATCH /data/orders` with a `status` key moves an order to **any** status with no
transition validation (BR-ORDER-028), limited only by the edit lock below.

## W-ORDER-01 Checkout: submit a cart

1. Buyer `POST`s an order payload to `/public/checkout`; authority `checkout`.
2. Transport validation enforces a non-empty validated line collection, a
   non-blank payment type and a validated customer block.
3. The order is created through the crud service, which runs the converter:
   - status resolved to `Pending`;
   - payment type resolved by name (blank → `A payment type has to be included`;
     unknown → `The payment type does not exist`);
   - customer resolved by identification number, reused or created;
   - billing type resolved; the enterprise branch resolves or creates the billing
     company and the billing address;
   - shipper resolved when supplied (unknown → `Specified shipper does not
     exist`), shipping address resolved or created;
   - each line's product resolved by barcode (blank → `Product barcode must be
     valid`; unknown → `Unexisting product in sell details`), unit value
     overwritten with the catalogue price, description generated;
   - totals computed (see below).
4. The checkout service asks the payment provider for a new payment page.
5. The returned token is copied onto the order and the start transition runs,
   persisting both the token and the `Payment Started` status.
6. The redirection URL and token are returned with status `200`.

**No stock check occurs at any step.**

## W-ORDER-02 Totals calculation

```text
for each line:
    unitTax  = unitValue * 0.19        (truncated to int on accumulation)
    unitNet  = unitValue - unitTax
    taxesValue += unitTax  * units
    netValue   += unitNet  * units
    totalItems += units

totalValue = taxesValue + netValue     (transportValue is NOT added)
transportValue stays at its default of zero - no code ever sets it
```

The unit value is **tax-inclusive**: tax is extracted from it rather than added
to it.

## W-ORDER-03 Payment confirmation (provider callback, success)

1. The provider redirects the buyer to `GET /public/checkout/validate` with
   `token_ws`. **No authority is required.**
2. A missing parameter raises `No transaction token was provided`.
3. The order is resolved by a filter of status `Payment Started` plus the token;
   no match raises `No sell matches the filtering conditions` — which is also the
   de-facto replay protection.
4. The payment result is requested. A commit failure inside the adapter is
   converted to a non-zero response code; a transport failure raises
   `Webpay failed to confirm the transaction`, mapped locally to a 500-class
   response.
5. Response code zero → the paid transition; anything else → the failed
   transition. A wrong-state condition is re-raised as
   `Transaction could not be confirmed`.
6. If mailing is configured, the customer notification is sent (status
   `Paid, Unconfirmed` → the payment template).
7. The response is `303` with a `Location` of the configured browser redirection
   URL plus the token.

## W-ORDER-04 Payment abortion (provider callback)

As W-ORDER-03 but `POST`, reading `TBK_TOKEN`, running the abort transition to
`Payment Cancelled`, sending no mail, and returning the same `303` redirection.

## W-ORDER-05 Operator confirmation, rejection and completion

1. Operator `POST`s to `/data/orders/confirmation`, `/rejection` or
   `/completion`; authority `orders:update` on all three.
2. The corresponding transition runs with its status guard.
3. Notifications are sent when mailing is configured:
   - confirmation → customer **and** owner;
   - rejection → customer only (despite an owner rejection subject being
     configured);
   - completion → customer only (despite an owner completion subject being
     configured).
4. Status codes differ by route: confirmation responds `204`; rejection and
   completion respond `200`.

The transition is committed before mail is attempted, so a notification failure
leaves the status changed and the mail lost (BR-ORDER-055).

## W-ORDER-06 Receipt lookup

1. Any caller issues `GET /public/receipt/{token}` — **public**.
2. A blank token raises `An incorrect receipt token was provided`.
3. The order is resolved by token alone, with **no** status restriction; no match
   raises `The transaction could not be found, no receipt can be created`.
4. The order is converted by the framework converter, which maps the identifier,
   date, transport value, tax, total items, total and token — but not the status
   or the lines.
5. The service builds each line as a trimmed product projection (name and barcode
   only) plus the unit value, then attaches the status name.

## W-ORDER-07 Order search

1. Operator issues `GET /data/orders`; authority `orders:read`.
2. A `buyOrder` parameter is special-cased into a single-item page.
3. Otherwise page index and size are resolved, the sort defaults to `buyOrder`
   descending, and the filter is built from `id`/`buyOrder`, `date`,
   `statusName` and `token`. A malformed date is logged and dropped.
4. The paged read returns orders **without** line detail; only the single-item
   read hydrates addresses and lines.

## W-ORDER-08 Order patching and the edit lock

1. Operator `PATCH`es with a non-empty selecting parameter map; authority
   `orders:update`.
2. The crud service inspects the existing order's status code: if it is 3 or
   above, or negative, and the edit toggle is false (the default), the request is
   rejected with `The requested transaction cannot be modified`.
3. Otherwise the patch service applies `date` (parsed as an instant), `status`,
   `paymentType` and `shipper` — the last three by name, unknown names silently
   ignored. A wrongly-typed value raises the truncated message `The `.
4. An unchanged result issues no write.

Editable by default: `Pending` (1) and `Payment Started` (2). Locked: everything
else. Full replacement is **not** covered by the lock (BR-ORDER-031).

## W-ORDER-09 Address resolution

1. The converter builds a candidate address from the submitted block.
2. The repository is queried on all six fields.
3. A match is reused by reference; otherwise a new row is created.
4. The address is attached to the order as its billing or shipping address, both
   mapped not-updatable.

Addresses have no API surface: they cannot be listed, searched or corrected.

## State model

```text
                    Pending (1)
                        |  start payment (+ token)
                        v
                 Payment Started (2)
                 /      |        \
       abort    /   fail|          \ pay
                v       v           v
  Payment Cancelled  Payment     Paid, Unconfirmed (3)
        (-1)         Failed (-2)      /        \
                                 confirm      reject
                                     v            v
                           Paid, Confirmed (4)  Rejected (-3)
                                     | complete
                                     v
                           Delivery Complete (6)

Unreachable: Delivery On Route (5), Delivery Failed (-5),
             Delivery Cancelled (-4), Returned (-6)

Edit lock (default): codes 1 and 2 are editable; code >= 3 or code < 0 is locked.
Patch bypass: a status patch can jump to any status, ignoring every arrow above.
```
