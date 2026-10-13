# Person Registry — Background Jobs

**No background jobs applicable to this domain.**

There is no scheduled task, cron entry, daemon thread, timer, queue worker, batch process
or asynchronous handler that touches the `people` table or any component of this domain.
Every code path in the domain runs synchronously, inside a request thread or inside a
consumer domain's service call.

Verified against the domain's complete source inventory (14 files, all specified in
documents `01` and `02`) and against the system-wide
`reference-documentation/cron-daemon-inventory.md`.

## Why this matters for a rewrite

Two consequences specific to this domain:

1. **Orphan person rows are never reclaimed.** Nothing deletes a person
   (BR-PERSON-013), and no consumer cascades deletion, so orphans accumulate permanently.
   A cleanup job is the obvious remedy and does **not** exist — a rewrite introducing one
   would be adding new behaviour, not preserving existing behaviour, and would need to
   define what "orphan" means (no account, no customer, no salesperson referencing the
   row). See US-PERSON-009.
2. **No asynchronous validation or enrichment.** The identification number and e-mail are
   never checked against anything, synchronously or otherwise
   (`specs/06-integration-points.md` §1). There is no deferred verification step to
   preserve.
