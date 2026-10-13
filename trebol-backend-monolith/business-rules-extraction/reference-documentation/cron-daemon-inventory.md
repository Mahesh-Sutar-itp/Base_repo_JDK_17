# Cron / Daemon Inventory

## Finding: there is no scheduled, background or asynchronous work

A repository-wide search over `src/main/java` and `src/test/java` returned
**zero matches** for every scheduling and asynchrony mechanism available to this
stack:

| Searched for | Matches |
|---|---|
| scheduling enablement annotation | 0 |
| scheduled-method annotations (fixed rate, fixed delay, cron) | 0 |
| async enablement annotation | 0 |
| async-method annotations | 0 |
| task executor / task scheduler beans | 0 |
| thread pools created by application code | 0 |
| application-runner or command-line-runner beans | 0 |
| external cron definitions, timer units or job descriptors in the repository | 0 |
| quartz or other job-scheduler dependencies in the build manifest | 0 |

Every unit of work in this application is performed **synchronously, inside an
inbound HTTP request**. There is no daemon, no worker, no queue consumer and no
timer. This is an **absence recorded as a finding**, not an omission in the
analysis.

## Jobs the system would need (and does not have)

Each entry below is justified by a specific behaviour or defect found in the
code, so this is derived from evidence rather than speculation. None exists
today.

| Candidate job | Why the code implies it | Evidence |
|---|---|---|
| expire abandoned checkouts | an order that reaches `Payment Started` and is never confirmed stays there **forever**, and because status code 2 is below the edit-lock threshold it also stays patchable | `BR-ORDER-029`, `../detailed-domain-bre/domain-order/workflows.md` |
| reconcile with the payment provider | a transport failure during confirmation leaves the order in `Payment Started` with no resolution path, and a commit failure is silently recorded as a decline | `BR-ORDER-036`, `BR-ORDER-037` |
| retry failed notifications | mail is sent inline **after** the status transition is committed, with no retry, so a failure loses the notification permanently | `BR-ORDER-055` |
| low-stock alerting | every product carries a `product_stock_critical` threshold that **no code ever compares to anything** — the clearest evidence of an intended but unbuilt scheduled feature | `BR-PRODUCT-021` |
| advance delivery states | four order statuses (`Delivery On Route`, `Delivery Failed`, `Delivery Cancelled`, `Returned`) are seeded and set by no code path | `BR-ORDER-025` |
| reclaim orphaned person rows | no code path deletes a person, and deleting an account, customer or salesperson leaves the person row behind | `BR-USER-048`, `BR-CUSTOMER-014`, `BR-SALESPERSON-018` |
| prune orphaned image links | deleting a product is blocked by its own image-link rows, which this system never cleans up | `BR-PRODUCT-028` |
| detect category cycles | a cycle is creatable through replacement and is never detected; the recursion depth limit masks it | `BR-PRODCAT-009` |
| detect duplicate salespeople | the person reference has no unique constraint, so duplicates are possible | `BR-SALESPERSON-002` |

## What performs "background-like" work today

| Mechanism | What it does | Why it is not a job |
|---|---|---|
| `ON DELETE CASCADE` on product-list membership | removes a deleted product from every list | a database rule evaluated inside the deleting transaction |
| `ON DELETE SET NULL` on order lines | nulls the product reference on historical lines | same |
| the identity-generator restart statements at the end of `data.sql` | advance sequences past seeded rows | one-off bootstrap work, not scheduled |
| lazy pattern compilation in the regular-expression adapter | compiles two patterns on first use | per-instance memoisation, not a job |

## Startup work

| Component | What happens at startup |
|---|---|
| `BackendApp` | application bootstrap |
| `JpaConfig` | entity and repository scanning only — declares no data source or transaction manager |
| `JwtKeyConfig` | derives the HMAC signing key from the configured secret string |
| configuration holders | validated at startup, so a blank signing key or a BCrypt strength below 6 prevents the application from starting |
| `src/main/resources/schema-mariadb.sql` and `data.sql` | schema creation and seeding, driven by the framework's deferred data-source initialisation |

Note that a configuration with all three token-lifetime components set to zero
passes startup validation and issues already-expired tokens (`BR-USER-017`).

## Implications for a rewrite

1. **No scheduling infrastructure exists to extend.** Adding any of the jobs
   above is new work, including the choice of scheduler, leader election for
   multiple instances, and failure handling.
2. **Three of the candidates are mandatory for a split**, not optional: the two
   database rules must become application-driven cleanup, and the orphan
   reclamation becomes visible once `person` is a separate service.
3. **The abandoned-checkout job is the highest-value addition** for the existing
   monolith, because the current behaviour leaves payment-started orders both
   permanently open and editable.

## Related documentation

- why there is no event mechanism to drive jobs from: `../cross-cutting-documentation/event-publishing-patterns.md`
- the inert stock fields: `../detailed-domain-bre/domain-product/business-rules.md`
- the unreachable order statuses: `../detailed-domain-bre/domain-order/workflows.md`
- the uncalled cleanup operations: `dead-code-decomposition-notes.md`
