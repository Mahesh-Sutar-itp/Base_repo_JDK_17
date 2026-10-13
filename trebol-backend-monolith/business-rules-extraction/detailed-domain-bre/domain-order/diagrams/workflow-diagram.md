# Workflow diagram — Order, Checkout & Fulfilment (`order`)

## The order status machine

```mermaid
stateDiagram-v2
    [*] --> Pending: POST /public/checkout<br/>(or POST /data/orders)<br/>code 1

    Pending --> PaymentStarted: markAsStarted<br/>+ stores the payment token<br/>code 2
    PaymentStarted --> PaymentCancelled: markAsAborted<br/>POST /public/checkout/validate<br/>code -1
    PaymentStarted --> PaymentFailed: markAsFailed<br/>response code != 0<br/>code -2
    PaymentStarted --> PaidUnconfirmed: markAsPaid<br/>response code == 0<br/>code 3
    PaidUnconfirmed --> PaidConfirmed: markAsConfirmed<br/>POST /data/orders/confirmation<br/>code 4
    PaidUnconfirmed --> Rejected: markAsRejected<br/>POST /data/orders/rejection<br/>code -3
    PaidConfirmed --> DeliveryComplete: markAsCompleted<br/>POST /data/orders/completion<br/>code 6

    PaymentCancelled --> [*]
    PaymentFailed --> [*]
    Rejected --> [*]
    DeliveryComplete --> [*]

    state "UNREACHABLE (seeded, never set)" as Unreachable {
        state "Delivery On Route (5)" as OnRoute
        state "Delivery Failed (-5)" as DelFailed
        state "Delivery Cancelled (-4)" as DelCancelled
        state "Returned (-6)" as Returned
    }

    note right of PaymentStarted
        Abandoned checkouts stay here forever:
        no expiry job exists. Code 2 is still
        below the edit lock threshold, so such
        orders remain patchable.
    end note
    note right of PaidUnconfirmed
        Code 3 crosses the edit lock threshold:
        from here on, PATCH is refused by default.
    end note
    note right of Unreachable
        Delivery tracking, delivery failure and
        returns are modelled in the seed data and
        absent from the behaviour. No transition
        sets any of these four statuses.
    end note
```

## Guards, the edit lock and the two bypasses

```mermaid
flowchart TD
    Change([Something wants to change an order]) --> How{through which path?}

    How -- "the 7 transition operations" --> T1[Fetch by buyOrder id]
    T1 --> T2{found?}
    T2 -- no --> TE1[/404 "No transaction matches given input"/]
    T2 -- yes --> T3{current status NAME matches<br/>the one this transition requires?}
    T3 -- no --> TE2[/400 "The transaction is not in a<br/>valid state for this api"/]
    T3 -- yes --> T4{target status row exists?}
    T4 -- no --> TE3[/500 "No status matches the '<STATUS>' name -<br/>Is the database empty or corrupt?"/]
    T4 -- yes --> T5[setStatus via a modifying update<br/>that clears the persistence context]
    T5 --> T6[Re-read + convert; 4 of 7 transitions<br/>also re-attach the order lines]
    T6 --> TOK([status advanced LEGALLY])

    How -- "PATCH /data/orders" --> P1{status code >= 3 or < 0?}
    P1 -- no --> P2[Codes 1 and 2 only:<br/>patch allowed]
    P1 -- yes --> P3{able-to-edit-orders-<br/>after-being-processed?}
    P3 -- "false (default)" --> PE1[/400 "The requested transaction<br/>cannot be modified" - EDIT LOCK/]
    P3 -- true --> P2
    P2 --> P4{change map contains 'status'?}
    P4 -- yes --> P5[/BYPASS 1: status resolved by name and<br/>APPLIED DIRECTLY. No transition guard.<br/>An order can jump Pending -> Delivery Complete./]
    P4 -- no --> P6[date / paymentType / shipper applied;<br/>unknown names SILENTLY IGNORED]
    P5 --> POK([order mutated])
    P6 --> POK

    How -- "PUT /data/orders" --> U1[/BYPASS 2: full replacement is NOT<br/>covered by the edit lock at all.<br/>A processed order can be overwritten<br/>regardless of the toggle./]
    U1 --> UOK([order overwritten])
```

## Monetary calculation

```mermaid
flowchart TD
    Lines([Submitted order lines]) --> Per{for each line}
    Per --> Price[unitValue := product.price<br/>CLIENT VALUE DISCARDED]
    Price --> Desc[description := units + 'x ' + product.name]
    Desc --> Tax["unitTax = unitValue * 0.19<br/>(TAX IS EXTRACTED from a<br/>tax-INCLUSIVE unit value)"]
    Tax --> Net[unitNet = unitValue - unitTax]
    Net --> Acc["accumulate as int:<br/>taxesValue += unitTax * units<br/>netValue += unitNet * units<br/>totalItems += units"]
    Acc --> More{more lines?}
    More -- yes --> Per
    More -- no --> Total[totalValue = taxesValue + netValue]
    Total --> Transport[/transportValue is NOT added.<br/>It is NOT NULL, sortable, and set<br/>by NO code: always zero./]
    Transport --> Done([order totals stored as int(11):<br/>no decimal places anywhere])

    Done --> Warn[/Truncation order is LOAD-BEARING:<br/>a rewrite that accumulates in decimal<br/>and rounds at the end will produce<br/>DIFFERENT totals./]
```

## Checkout end to end, including the failure branches

```mermaid
flowchart TD
    A([Buyer: POST /public/checkout<br/>authority 'checkout']) --> B{details non-empty,<br/>paymentType non-blank?}
    B -- no --> BE[/400 REJECTED_02 validation envelope/]
    B -- yes --> C[Convert: status Pending, payment type,<br/>customer reuse-or-create, billing branch,<br/>shipper + addresses, lines, totals]
    C --> CE{any resolution failure?}
    CE -- "payment type blank" --> E1[/400 "A payment type has to be included"/]
    CE -- "payment type unknown" --> E2[/500 "The payment type does not exist"/]
    CE -- "billing type unknown" --> E3[/400 "Specified billing type does not exist"/]
    CE -- "shipper unknown" --> E4[/400 "Specified shipper does not exist"/]
    CE -- "line barcode blank" --> E5[/400 "Product barcode must be valid"/]
    CE -- "line product unknown" --> E6[/400 "Unexisting product in sell details"/]
    CE -- none --> D[Order saved as Pending]
    D --> NoStock[/NO stock check ran.<br/>NO stock was decremented./]
    NoStock --> F[Request a payment page from the provider]
    F --> FE{provider creation fails?}
    FE -- yes --> E7[/500 "Webpay could not create a new transaction"<br/>order LEFT as Pending/]
    FE -- no --> G[Copy the token onto the order;<br/>markAsStarted -> Payment Started]
    G --> GE{order not found during the transition?}
    GE -- yes --> E8[/500 "The server had a problem<br/>requesting the transaction"/]
    GE -- no --> H([200 - redirection URL + token<br/>returned to the buyer])

    H --> I{provider callback}
    I -- "GET validate?token_ws" --> J[Resolve by status Payment Started + token]
    I -- "POST validate?TBK_TOKEN" --> K[Resolve the same way]
    J --> JE{no match?}
    K --> JE
    JE -- yes --> E9[/404 "No sell matches the filtering conditions"<br/>THE ONLY replay protection/]
    JE -- no --> L{which callback?}
    L -- abort --> M[markAsAborted -> Payment Cancelled]
    L -- success --> N[requestPaymentResult]
    N --> NE{transport failure?}
    NE -- yes --> E10[/500 "Webpay failed to confirm the transaction"<br/>order STUCK in Payment Started/]
    NE -- no --> O{response code}
    O -- "0" --> P[markAsPaid -> Paid, Unconfirmed]
    O -- "non-zero (incl. a SWALLOWED commit failure)" --> Q[markAsFailed -> Payment Failed]
    P --> R{mailing bean present?}
    R -- yes --> S[Customer payment mail -<br/>sent AFTER the commit, no retry,<br/>failure loses the mail]
    R -- no --> T
    S --> T[303 See Other -> browserRedirectionUrl?token=...]
    Q --> T
    M --> T
```

## Notification matrix

```mermaid
flowchart LR
    subgraph Statuses
        S1[Pending]
        S2[Payment Started]
        S3[Payment Cancelled]
        S4[Payment Failed]
        S5[Paid, Unconfirmed]
        S6[Paid, Confirmed]
        S7[Rejected]
        S8[Delivery Complete]
    end
    subgraph Customer["Customer mail"]
        C5[payment template]
        C6[confirmation template]
        C7[rejection template]
        C8[completion template]
    end
    subgraph Owner["Owner mail"]
        O6[confirmation template]
        O7[rejection template - configured]
        O8[completion template - configured]
    end
    S5 --> C5
    S6 --> C6
    S6 --> O6
    S7 --> C7
    S7 -. "subject configured but the<br/>rejection route notifies the<br/>CUSTOMER ONLY" .-> O7
    S8 --> C8
    S8 -. "subject configured but the<br/>completion route notifies the<br/>CUSTOMER ONLY" .-> O8
    S1 -.-> N1[/no mail/]
    S2 -.-> N1
    S3 -.-> N1
    S4 -.-> N1
```
