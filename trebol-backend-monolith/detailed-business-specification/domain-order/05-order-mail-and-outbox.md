# Order — Mail and the Transactional Outbox

Porting-depth behavioural specification. Subsystem 5 of 7. Paths relative to
`src/main/java/org/trebol/`.

This subsystem was **rebuilt in Phase 3** by register item **D11**. The `MailingService`
contract and the Mailgun adapter are untouched; what changed is *when* and *how* a
notification is recorded and dispatched.

---

## 1. The port — `mailing/MailingService`

```java
/**
 * Point of entry for services to send mail to customers and owners alike
 */
public interface MailingService {
    void notifyOrderStatusToClient(OrderPojo sell) throws MailingServiceException;
    void notifyOrderStatusToOwners(OrderPojo sell) throws MailingServiceException;
}
```

Two methods, one checked exception. **Unchanged by D11** — which is deliberate, so Mailgun
behaviour, templates, subjects and both verbatim exception messages are unaffected.

**Mailing is optional at every call site** — **BR-ORDER-052**. The bean is injected as
`@Nullable @Autowired(required = false)`, so an application running with no mailing profile
sends no mail and raises no error. D11 preserves that exactly, and extends it: with no mailing
service active, **no outbox entries are recorded either**, because entries nothing will ever
drain would be a slow-growing leak.

### 1.1 Status coverage — four and three

| Rule | Behaviour |
|---|---|
| **BR-ORDER-049** | **customer** mail fires on exactly **four** statuses |
| **BR-ORDER-050** | **owner** mail fires on exactly **three** statuses |
| **BR-ORDER-051** | **an unmapped status produces no mail and no error** |

That rule lives in the mailing adapter, which looks up a template per status and silently
returns when there is none. **D11 does not re-implement it** — the dispatcher calls the
adapter and treats "nothing to send" as success, exactly as the synchronous call sites did.

### 1.2 Subjects and the response contract

**BR-ORDER-053** — mail subjects are suffixed with the order identifier.
**BR-ORDER-054** — a mail response without an identifier is a failure: the adapter inspects
the provider's response for a message id and raises `MailingServiceException` when absent.

Both are properties of `MailgunMailingServiceImpl` and port unchanged.

---

## 2. Register item D11 — what the defect actually was

**Ruling: FIX via a transactional outbox, with a recorded fallback if descoped. Implemented
in full in step 3.5 — the fallback was not needed.**

Mail was sent **after** the status transition had committed, synchronously, from a
**non-transactional controller method**, at four call sites:

- `PublicCheckoutController.validateSuccesfulTransaction`
- `DataOrdersController.confirmSell` / `rejectSell` / `completeSell`

A `MailingServiceException` at that point produced the worst of both outcomes:

> the caller saw an **error**, the order's status was **already changed**, and the
> notification was **lost with nothing recording that it had been owed**.

**BR-ORDER-055** recorded this as current behaviour — "notification is not transactional with
the transition". **It is now fixed, and the rule is stale** (`00` §3).

**This is the only genuinely new infrastructure in the programme.** No broker, queue, stream,
retry or scheduled work existed anywhere in the codebase; `ADR-003` accepted that an outbox
would have to be built from nothing. What was added is the minimum that fixes the defect.

| Added | Role |
|---|---|
| `jpa/entities/OrderMailOutboxEntry` + table `order_mail_outbox` | one row per owed notification |
| `jpa/repositories/OrderMailOutboxRepository` | one paged query, `findPending` |
| `mailing/outbox/MailRecipientKind` | `CUSTOMER` or `OWNERS` |
| `mailing/outbox/MailOutboxService` + `impl/MailOutboxServiceImpl` | records an entry, **inside the caller's transaction** |
| `mailing/outbox/MailOutboxDispatcher` | `@Scheduled` drain with bounded retry |
| `config/SchedulingConfig` | `@EnableScheduling` — nothing was scheduled before |

---

## 3. Where the enqueue happens, and why it is not in the controllers

**The entry is written inside `OrdersProcessServiceImpl`, which is `@Transactional`.** That is
the whole point of an outbox: the entry and the status transition **commit or roll back
together**. Either the order moved and the notification is recorded, or neither happened.

Writing it from a controller — which is *not* transactional — would have produced a
**post-commit queue with D11's original failure mode, just moved one layer out**.

`MailOutboxServiceImpl.enqueue` enforces that structurally:

```java
@Override
@Transactional(propagation = Propagation.MANDATORY)
public void enqueue(OrderPojo order, MailRecipientKind recipient) {
```

**`MANDATORY`** means a caller that invokes it outside a transaction gets
`IllegalTransactionStateException` **immediately**, rather than silently starting a second
transaction. The requirement is a guarantee, not a comment.

### 3.1 The recipient mapping — exactly what the four call sites did

| Transition | Notifications | Former call site |
|---|---|---|
| `markAsPaid` | CUSTOMER | `PublicCheckoutController` — GET `/validate` |
| `markAsFailed` | CUSTOMER | same controller, decline path |
| `markAsConfirmed` | CUSTOMER **and** OWNERS | `DataOrdersController.confirmSell` |
| `markAsRejected` | CUSTOMER | `DataOrdersController.rejectSell` |
| `markAsCompleted` | CUSTOMER | `DataOrdersController.completeSell` |
| `markAsStarted`, `markAsAborted` | **none** | neither ever mailed — **POST `/validate` sends nothing** |

**Six enqueue calls across five transitions.** `markAsConfirmed` is the only one that notifies
both parties, which is why **BR-ORDER-050**'s owner coverage is three statuses rather than
four.

**`markAsAborted` enqueues nothing**, because the abort callback never mailed. That asymmetry
is easy to "tidy up" by accident and would be a behavioural change.

### 3.2 The guard in `enqueue`

```java
if (mailingService==null) {
    return;                       // BR-ORDER-052 preserved
}
if (order==null || order.getBuyOrder()==null) {
    // Not an exception: a notification is not worth failing a committed transition for.
    logger.warn("Not recording a {} notification - the order has no identifier", recipient);
    return;
}
```

---

## 4. `jpa/entities/OrderMailOutboxEntry` — `serialVersionUID = 22L`

```java
@Entity
@Table(name = "order_mail_outbox",
    indexes = { @Index(columnList = "outbox_dispatched_at") })
```

| Column | Purpose |
|---|---|
| `outbox_id` | identity |
| `order_id` | **a plain identifier, not an association** |
| `outbox_recipient_kind` | `CUSTOMER` or `OWNERS`, stored as the enum name |
| `outbox_order_status_name` | **the status at the moment of the transition** |
| `outbox_attempts` | incremented per attempt; bounds the retry |
| `outbox_created_at` | |
| `outbox_dispatched_at` | null until sent successfully — **the only marker of completion** |
| `outbox_last_error` | the last failure, truncated to 500 chars; diagnostic only, never shown to a client |

Two decisions recorded in the entity and the DDL comment:

**`order_id` is not a foreign key.** The table is infrastructure; an FK would drag the outbox
into the order aggregate's delete semantics for no benefit, and it is one fewer key for this
extraction to reason about.

**The status name is stored, not re-read.** The mail must describe the status the order was in
*when the transition happened*; by the time the dispatcher runs the order may have moved on,
and the current status would be the wrong answer. A dedicated test pins this.

---

## 5. `mailing/outbox/MailOutboxDispatcher`

`@Component`. Dependencies: `OrderMailOutboxRepository`, `OrdersCrudService`,
`OrdersPredicateService`, `@Nullable MailingService`, plus two `@Value` bounds.

```java
@Scheduled(
    fixedDelayString  = "${trebol.mailing.outbox.fixed-delay-ms:60000}",
    initialDelayString = "${trebol.mailing.outbox.initial-delay-ms:60000}")
public void dispatchPending() {
    if (mailingService==null) {
        return;
    }
    try {
        List<OrderMailOutboxEntry> pending =
            outboxRepository.findPending(maxAttempts, PageRequest.of(0, batchSize));
        for (OrderMailOutboxEntry entry : pending) {
            this.dispatchOne(entry);
        }
    } catch (RuntimeException exc) {
        logger.error("The mail outbox dispatcher failed to drain a batch", exc);
    }
}
```

| Property | Default | Note |
|---|---|---|
| `trebol.mailing.outbox.fixed-delay-ms` | 60000 | how often to drain |
| `trebol.mailing.outbox.initial-delay-ms` | 60000 | |
| `trebol.mailing.outbox.batch-size` | 50 | bounds one run |
| `trebol.mailing.outbox.max-attempts` | 5 | retry bound |

All four have **defaults in code**, so an operator who configures nothing still gets working
behaviour.

**`dispatchPending` never throws.** A scheduled method that throws is silently disabled by
some schedulers and logs a stack trace per tick in others, and neither helps.

### 5.1 `dispatchOne`, and the annotation that is deliberately absent

```java
void dispatchOne(OrderMailOutboxEntry entry) {
    entry.setAttempts(entry.getAttempts() + 1);
    try {
        OrderPojo order = this.resolveOrder(entry);
        order.setStatus(entry.getOrderStatusName());   // as at the transition, not now
        if (MailRecipientKind.OWNERS.name().equals(entry.getRecipientKind())) {
            mailingService.notifyOrderStatusToOwners(order);
        } else {
            mailingService.notifyOrderStatusToClient(order);
        }
        entry.setDispatchedAt(Instant.now());
        entry.setLastError(null);
    } catch (Exception exc) {
        entry.setLastError(abbreviate(exc.getClass().getSimpleName() + ": " + exc.getMessage(), 500));
        logger.warn("Could not dispatch outbox entry {} for order {} (attempt {} of {}): {}", ...);
    }
    outboxRepository.save(entry);
}
```

> **It is deliberately *not* annotated `@Transactional`.** It is invoked from
> `dispatchPending` on `this`, so Spring's proxy would **never intercept it** — the annotation
> would be a guarantee that does not exist. A comment on the method says so, so it is not
> "helpfully" added later.

None is needed: `save` commits on its own, and **per-entry isolation is exactly what is
wanted**, so one unreachable recipient cannot roll back the successful sends beside it.

**The attempt counter is incremented before the send**, and the row is saved whether the send
succeeds or fails. An entry that fails `maxAttempts` times stops being selected by
`findPending` and **retains its last error** — abandoned, but not silently: the row is the
record that a notification was owed and never sent, which is exactly what the baseline had no
way of knowing.

**Every exception is treated alike** — checked `MailingServiceException`, an
`EntityNotFoundException` from a deleted order, anything the adapter throws. All are recorded
and retried within the bound.

### 5.2 `resolveOrder`

```java
Map<String, String> byId = new HashMap<>(Map.of("id", String.valueOf(entry.getOrderId())));
Predicate orderWithMatchingId = ordersPredicateService.parseMap(byId);
return ordersCrudService.readOne(orderWithMatchingId);
```

Through `OrdersCrudService` rather than a repository, mirroring how the checkout path resolves
orders — the mailing adapter needs a fully converted `OrderPojo` including details, which is
what the crud service produces (`07` §3.2).

---

## 6. `jpa/repositories/OrderMailOutboxRepository`

```java
@Query("SELECT e FROM OrderMailOutboxEntry e "
    + "WHERE e.dispatchedAt IS NULL "
    + "AND e.attempts < :maxAttempts "
    + "ORDER BY e.createdAt ASC")
List<OrderMailOutboxEntry> findPending(@Param("maxAttempts") int maxAttempts, Pageable pageable);
```

**Paged rather than unbounded**, so a long backlog cannot turn one scheduled run into an
arbitrarily long transaction. Oldest first. The retry bound is applied **by the query**, which
is why an exhausted entry is never selected again while remaining in the table.

---

## 7. What the controllers look like now

All four call sites lost their mail send and gained an identical four-line comment:

```java
processService.markAsConfirmed(sell);
// Register item D11: the mail send that used to sit here has moved into the
// transition's own transaction, as an outbox entry - see MailOutboxService.
// A mail failure can no longer fail a request whose transition already
// committed, nor be lost without trace when it does fail.
```

**The `MailingService` field remains injected** on both controllers even though neither now
uses it — and the `throws MailingServiceException` declarations remain on the three
`DataOrdersController` methods. Both are preserved: removing them would change the declared
signatures of public controller methods for no behavioural gain (G1, and the API-compatibility
guardrail).

---

## 8. Register item D28 — flagged, not fixed

**`MailgunMailingServiceImpl` builds one shared mutable `Unirest` `HttpRequestWithBody` in its
constructor and mutates it per call via `.field(...)`.**

**Ruling: PRESERVE in Phase 3, flagged.** It was a latent thread-safety hazard before and
remains one. The dispatcher does **not** make it worse today, because it drains entries
sequentially on a single scheduled thread — but it becomes materially more likely the moment
anyone raises the dispatcher's concurrency or runs more than one instance.

**Action required of the owner:** make the adapter build a fresh request per call. Rewriting a
working adapter inside an extraction phase is exactly the scope creep G1 guards against, so it
is recorded rather than done here.

---

## 9. Tests (G8) — `MailOutboxTest`, 12 tests

| Group | Proves |
|---|---|
| **`Enqueueing`** (4) | an entry is recorded **with the status as at the transition**; `OWNERS` is recorded distinctly; an order with no identifier is skipped rather than raised; **nothing is recorded when no mailing service is active** |
| **`Dispatching`** (8) | each recipient kind routes to the right method; **the stored status is sent, not the order's current one**; a mail failure **neither propagates nor loses the entry** and records `last_error`; an unresolvable order is treated the same; the retry bound is applied by the query; **one failing entry does not stop the rest of the batch**; nothing happens with no mailing service |

**One existing test was updated:** `OrdersProcessServiceImplTest` gained a
`@Mock MailOutboxService`, because the transitions now take it as a collaborator. **No
assertion was changed.**

---

## 10. Rules covered

BR-ORDER-049, 050, 051, 052, 053, 054, **055 (corrected)**.

**BR-ORDER-055 states notification is not transactional with the transition. It now is** —
see §2 and `00` §3.
