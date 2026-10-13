# Sequence diagrams — Order, Checkout & Fulfilment (`order`)

## Checkout: submit a cart and start a payment (`POST /public/checkout`)

```mermaid
sequenceDiagram
    actor Buyer
    participant PublicCheckoutController
    participant OrdersCrudServiceImpl
    participant OrdersConverterServiceImpl
    participant CustomersCrudServiceImpl
    participant ProductsRepository
    participant BillingTypesRepository
    participant ShippersRepository
    participant AddressesRepository
    participant CheckoutServiceImpl
    participant WebpayplusPaymentServiceImpl
    participant OrdersProcessServiceImpl
    participant OrdersRepository

    Buyer->>PublicCheckoutController: POST /public/checkout (OrderPojo)
    Note over PublicCheckoutController: authority 'checkout'.<br/>details @Valid @NotEmpty,<br/>paymentType @NotBlank,<br/>customer @Valid.<br/>Address and billing blocks are NOT<br/>cascade-validated.
    PublicCheckoutController->>OrdersCrudServiceImpl: create(orderPojo)
    OrdersCrudServiceImpl->>OrdersConverterServiceImpl: convertToNewEntity(orderPojo)
    OrdersConverterServiceImpl->>OrdersRepository: resolve status by name "Pending"
    OrdersConverterServiceImpl->>OrdersConverterServiceImpl: convertPaymentTypeInformationForEntity
    alt payment type blank
        OrdersConverterServiceImpl-->>Buyer: 400 REJECTED_01 "A payment type has to be included"
    else payment type unknown
        OrdersConverterServiceImpl-->>Buyer: 500 "The payment type does not exist" (unchecked)
    end
    OrdersConverterServiceImpl->>CustomersCrudServiceImpl: getExisting(customer) - reuse or create
    OrdersConverterServiceImpl->>BillingTypesRepository: findByName(billingType)
    alt billing type unknown
        OrdersConverterServiceImpl-->>Buyer: 400 REJECTED_01 "Specified billing type does not exist"
    else "Enterprise Invoice"
        OrdersConverterServiceImpl->>AddressesRepository: resolve-or-create the billing address
    end
    opt shipper supplied
        OrdersConverterServiceImpl->>ShippersRepository: findByName(shipper)
        alt unknown
            OrdersConverterServiceImpl-->>Buyer: 400 REJECTED_01 "Specified shipper does not exist"
        end
        OrdersConverterServiceImpl->>AddressesRepository: resolve-or-create the shipping address
    end
    loop each order line
        OrdersConverterServiceImpl->>ProductsRepository: findByBarcode(barcode)
        alt blank barcode
            OrdersConverterServiceImpl-->>Buyer: 400 "Product barcode must be valid"
        else unknown barcode
            OrdersConverterServiceImpl-->>Buyer: 400 "Unexisting product in sell details"
        else resolved
            Note over OrdersConverterServiceImpl: unitValue := product price (client value IGNORED)<br/>description := units + "x " + product name
            rect rgb(255,240,240)
            Note over OrdersConverterServiceImpl,ProductsRepository: NO stock check. NO decrement.
            end
        end
    end
    OrdersConverterServiceImpl->>OrdersConverterServiceImpl: updateTotals (19% extracted per line,<br/>integer accumulation, transport EXCLUDED)
    OrdersCrudServiceImpl->>OrdersRepository: saveAndFlush(order, status Pending)

    PublicCheckoutController->>CheckoutServiceImpl: requestTransactionStart(orderPojo)
    CheckoutServiceImpl->>WebpayplusPaymentServiceImpl: requestNewPaymentPageDetails(order)
    Note over WebpayplusPaymentServiceImpl: buyOrder = order id,<br/>sessionId = order model HASH CODE,<br/>amount = order total,<br/>returnUrl = configured callback.<br/>Sandbox credentials unless production=true.
    alt provider creation fails
        WebpayplusPaymentServiceImpl-->>CheckoutServiceImpl: PaymentServiceException<br/>"Webpay could not create a new transaction"
        CheckoutServiceImpl-->>Buyer: 500 (raw message body)
    end
    WebpayplusPaymentServiceImpl-->>CheckoutServiceImpl: {url, token}
    CheckoutServiceImpl->>OrdersProcessServiceImpl: markAsStarted(order with token)
    OrdersProcessServiceImpl->>OrdersRepository: setStatus(id, "Payment Started")
    OrdersProcessServiceImpl->>OrdersRepository: setTransactionToken(id, token)
    alt order not found
        OrdersProcessServiceImpl-->>CheckoutServiceImpl: EntityNotFound
        CheckoutServiceImpl-->>Buyer: 500 "The server had a problem requesting the transaction"
    end
    PublicCheckoutController-->>Buyer: 200 {url, token}
```

## Payment confirmation callback (`GET /public/checkout/validate`)

```mermaid
sequenceDiagram
    participant Provider as Transbank (buyer's browser)
    participant PublicCheckoutController
    participant CheckoutServiceImpl
    participant OrdersPredicateServiceImpl
    participant OrdersCrudServiceImpl
    participant WebpayplusPaymentServiceImpl
    participant OrdersProcessServiceImpl
    participant MailgunMailingServiceImpl

    Provider->>PublicCheckoutController: GET /public/checkout/validate?token_ws=...
    Note over PublicCheckoutController: PUBLIC: no authority, no signature check,<br/>no provider-origin check, no idempotency key
    alt token_ws missing
        PublicCheckoutController-->>Provider: 400 REJECTED_01 "No transaction token was provided"
    end
    PublicCheckoutController->>CheckoutServiceImpl: confirmTransaction(token, wasAborted=false)
    CheckoutServiceImpl->>OrdersPredicateServiceImpl: parseMap{statusCode: "Payment Started", token: token}
    CheckoutServiceImpl->>OrdersCrudServiceImpl: readOne(predicate)
    alt no match (already confirmed, or unknown token)
        OrdersCrudServiceImpl-->>Provider: 404 NOTFOUND_01 "No sell matches the filtering conditions"
        Note over OrdersCrudServiceImpl: this status condition is the ONLY<br/>replay protection in the flow
    end
    CheckoutServiceImpl->>WebpayplusPaymentServiceImpl: requestPaymentResult(token)
    alt commit fails inside the adapter
        Note over WebpayplusPaymentServiceImpl: failure SWALLOWED - returns a non-zero code,<br/>so infrastructure trouble looks like a decline
    else transport failure
        WebpayplusPaymentServiceImpl-->>PublicCheckoutController: PaymentServiceException<br/>"Webpay failed to confirm the transaction"
        PublicCheckoutController-->>Provider: 500 (raw message); order STAYS Payment Started
    end
    WebpayplusPaymentServiceImpl-->>CheckoutServiceImpl: responseCode
    alt responseCode == 0
        CheckoutServiceImpl->>OrdersProcessServiceImpl: markAsPaid -> "Paid, Unconfirmed"<br/>(+ re-reads and attaches order lines)
    else responseCode != 0
        CheckoutServiceImpl->>OrdersProcessServiceImpl: markAsFailed -> "Payment Failed"
    end
    alt wrong current status
        OrdersProcessServiceImpl-->>CheckoutServiceImpl: BadInput "The transaction is not in<br/>a valid state for this api"
        CheckoutServiceImpl-->>Provider: 500 "Transaction could not be confirmed"<br/>(real state only logged)
    end
    opt mailing bean present
        PublicCheckoutController->>MailgunMailingServiceImpl: notifyOrderStatusToClient(order)
        Note over MailgunMailingServiceImpl: "Paid, Unconfirmed" -> customer payment template.<br/>Sent AFTER the transition is committed:<br/>a failure loses the mail, cannot roll back.
    end
    PublicCheckoutController-->>Provider: 303 See Other<br/>Location: browserRedirectionUrl + "?token=" + token
```

## Operator confirmation (`POST /data/orders/confirmation`)

```mermaid
sequenceDiagram
    actor Operator
    participant DataOrdersController
    participant OrdersProcessServiceImpl
    participant OrdersCrudServiceImpl
    participant OrderStatusesRepository
    participant OrdersRepository
    participant OrderDetailsRepository
    participant MailgunMailingServiceImpl

    Operator->>DataOrdersController: POST /data/orders/confirmation (OrderPojo)
    Note over DataOrdersController: isAuthenticated() + orders:update
    DataOrdersController->>OrdersProcessServiceImpl: markAsConfirmed(order)
    OrdersProcessServiceImpl->>OrdersCrudServiceImpl: getExisting(order) by buyOrder id
    alt no match (or buyOrder null)
        OrdersProcessServiceImpl-->>Operator: 404 NOTFOUND_01 "No transaction matches given input"
    end
    OrdersProcessServiceImpl->>OrdersProcessServiceImpl: current status must be "Paid, Unconfirmed"
    alt wrong state
        OrdersProcessServiceImpl-->>Operator: 400 REJECTED_01 "The transaction is not in<br/>a valid state for this api"
    end
    OrdersProcessServiceImpl->>OrderStatusesRepository: findByName("Paid, Confirmed")
    alt status row missing
        OrdersProcessServiceImpl-->>Operator: 500 "No status matches the 'Paid, Confirmed' name -<br/>Is the database empty or corrupt?"
    end
    OrdersProcessServiceImpl->>OrdersRepository: setStatus(id, status)
    Note over OrdersRepository: modifying update that CLEARS the<br/>persistence context - the status<br/>association is mapped updatable=false,<br/>so it cannot simply be assigned
    OrdersProcessServiceImpl->>OrdersRepository: getById(id) + convert
    alt conversion yields null
        OrdersProcessServiceImpl-->>Operator: 500 "Converter could not turn Sell<br/>into its Pojo equivalent"
    end
    OrdersProcessServiceImpl->>OrderDetailsRepository: findBySellId(id)
    Note over OrdersProcessServiceImpl: lines re-attached so notification<br/>templates have full detail
    opt mailing bean present
        DataOrdersController->>MailgunMailingServiceImpl: notifyOrderStatusToClient(order)
        DataOrdersController->>MailgunMailingServiceImpl: notifyOrderStatusToOwners(order)
        Note over MailgunMailingServiceImpl: confirmation is the ONLY route that<br/>notifies the owner; rejection and<br/>completion notify the customer only
    end
    DataOrdersController-->>Operator: 204 No Content
```

## The patch bypass of the status machine (`PATCH /data/orders`)

```mermaid
sequenceDiagram
    actor Operator
    participant DataOrdersController
    participant OrdersCrudServiceImpl
    participant OrdersPatchServiceImpl
    participant OrderStatusesRepository
    participant PaymentTypesRepository
    participant ShippersRepository
    participant OrdersRepository

    Operator->>DataOrdersController: PATCH /data/orders?buyOrder=42<br/>{"status": "Delivery Complete"}
    Note over DataOrdersController: isAuthenticated() + orders:update
    alt request params empty
        DataOrdersController-->>Operator: 400 REJECTED_01 "Missing request params"
    end
    DataOrdersController->>OrdersCrudServiceImpl: partialUpdate(changes, filters)
    OrdersCrudServiceImpl->>OrdersCrudServiceImpl: flushPartialChanges - read existing status code
    alt code >= 3 or code < 0, and able-to-edit-orders-after-being-processed = false
        OrdersCrudServiceImpl-->>Operator: 400 REJECTED_01 "The requested transaction cannot be modified"
        Note over OrdersCrudServiceImpl: EDIT LOCK. Codes 1 (Pending) and<br/>2 (Payment Started) remain editable.<br/>NOTE: the lock does NOT cover PUT.
    end
    OrdersCrudServiceImpl->>OrdersPatchServiceImpl: patchExistingEntity(changes, order)
    opt key "status"
        OrdersPatchServiceImpl->>OrderStatusesRepository: findByName(value)
        alt resolved
            Note over OrdersPatchServiceImpl: STATUS APPLIED DIRECTLY.<br/>No transition guard runs. An order can<br/>jump from Pending to Delivery Complete.
        else unknown name
            Note over OrdersPatchServiceImpl: SILENTLY IGNORED - request succeeds,<br/>nothing changes
        end
    end
    opt key "paymentType"
        OrdersPatchServiceImpl->>PaymentTypesRepository: findByName(value) - ifPresent
    end
    opt key "shipper"
        OrdersPatchServiceImpl->>ShippersRepository: findByName(value) - ifPresent
    end
    opt key "date"
        Note over OrdersPatchServiceImpl: parsed as an Instant
    end
    alt wrongly-typed value
        OrdersPatchServiceImpl-->>Operator: 400 REJECTED_01 message is literally "The "<br/>(truncated string - DEFECT)
    end
    OrdersCrudServiceImpl->>OrdersRepository: saveAndFlush (skipped if unchanged)
    DataOrdersController-->>Operator: 204 No Content
```

## Public receipt lookup (`GET /public/receipt/{token}`)

```mermaid
sequenceDiagram
    actor AnyCaller
    participant PublicReceiptController
    participant ReceiptServiceImpl
    participant OrdersRepository
    participant OrderEntity2ReceiptPojo

    AnyCaller->>PublicReceiptController: GET /public/receipt/{token}
    Note over PublicReceiptController: PUBLIC: no authority
    alt token blank
        PublicReceiptController-->>AnyCaller: 400 REJECTED_01 "An incorrect receipt token was provided"
    end
    PublicReceiptController->>ReceiptServiceImpl: fetchReceiptByTransactionToken(token)
    ReceiptServiceImpl->>OrdersRepository: findByTransactionToken(token)
    Note over OrdersRepository: NO status restriction - unlike the<br/>checkout validation lookup. A receipt is<br/>readable while still Payment Started.
    alt not found
        ReceiptServiceImpl-->>AnyCaller: 404 NOTFOUND_01 "The transaction could not be found,<br/>no receipt can be created"
    end
    ReceiptServiceImpl->>OrderEntity2ReceiptPojo: convert(order)
    Note over OrderEntity2ReceiptPojo: maps buyOrder, date, transportValue,<br/>taxValue, totalItems, totalValue, token.<br/>Does NOT map status or details.
    loop each OrderDetail
        ReceiptServiceImpl->>ReceiptServiceImpl: build a line with a TRIMMED product<br/>(name + barcode only) and the unit value
    end
    ReceiptServiceImpl->>ReceiptServiceImpl: attach status name separately
    ReceiptServiceImpl-->>PublicReceiptController: ReceiptPojo (may be null if conversion failed)
    PublicReceiptController-->>AnyCaller: 200 receipt
```
