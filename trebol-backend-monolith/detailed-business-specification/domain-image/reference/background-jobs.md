# Image Registry — Background Jobs

**No background jobs applicable to this domain.**

No scheduled task, cron entry, daemon thread, timer, queue worker, batch process or
asynchronous handler touches the `images` table or any component of this domain. Every code
path runs synchronously, inside a request thread or inside the `product` domain's service
call.

Verified against the domain's complete source inventory (13 files, all specified in
documents `01` and `02`) and against the system-wide
`reference-documentation/cron-daemon-inventory.md`.

## Three consequences specific to this domain

1. **Nothing ever validates a stored URL.** Every image record is an unverified claim that
   an asset exists at a location (BR-IMAGE-005). A periodic link-checking job is the obvious
   remedy and **does not exist**. A record can point at a 404 indefinitely with nothing
   detecting or reporting it. Introducing such a job in a rewrite would be new behaviour,
   not preserved behaviour, and would need a defined outcome for a broken link — flag,
   delete, or report.

2. **Orphaned image records are never reclaimed.** Images created through the `product`
   domain's cascade (BR-IMAGE-024) survive the removal of the product that caused them, and
   no job reclaims them. "Orphan" would have to be defined as an image with no row in
   `product_images`.

3. **No asynchronous image processing.** Nothing resizes, converts, compresses, thumbnails
   or inspects image content — not synchronously and not deferred. There is no deferred
   pipeline to preserve, and no queue to drain. See `reference/integrations.md`.
