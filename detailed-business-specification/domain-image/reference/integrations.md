# Image Registry — Integrations

**No external integrations applicable to this domain.**

The domain's only I/O is the relational database holding its one owned table, `images`.
There is no outbound HTTP or RPC client, no message broker, no object store or CDN client,
no file-system access, no upload handler, no image-processing library, no cache, no search
index and no no-op or mock client standing in for an absent provider.

Verified against the domain's complete 13-file source inventory and the system-wide
`reference-documentation/external-integrations-map.md`.

## The defining absence: the URL is never dereferenced

This is the single most important fact about the domain's integration posture. Every image
record carries an absolute URL of up to 500 characters, and **nothing ever fetches it.**
There is no HTTP client anywhere in the domain.

| Absent capability | Consequence |
|---|---|
| No existence check | A record can point at a 404 indefinitely; nothing detects or reports it. |
| No content-type check | A record can point at an HTML page, a document or an executable. |
| No upload path | Binary content must already exist at the URL, placed there by a process entirely outside this system. The domain stores **metadata only**. |
| No metadata capture | No checksum, byte size, MIME type, width, height or upload timestamp. |
| No scheme or host allow-listing | Any string is accepted — including a `javascript:` or `file:` URL, or a host outside the organisation's control. |

The last row warrants emphasis. These URLs are served to clients and presumably rendered as
image sources. The domain applies **no** allow-listing, so the integrity of rendered content
depends entirely on the trustworthiness of whoever holds `images:create` or `images:update`
— and, because of the cross-domain bypass below, on whoever can create products. See
`permissions.md` §2.1 and §3.

A rewrite should treat a service boundary as the natural enforcement point for scheme and
host restrictions. Adding them changes what the system accepts, so it is a deliberate
behavioural change rather than a cleanup.

## Cross-domain integration

The domain has one consumer and a notably **clean** service-level boundary — no consumer
reaches its repository, unlike the sibling `person` domain where six of seventeen call
sites do.

| Consumer | Calls | Level |
|---|---|---|
| `product` | `ImagesCrudService.getExisting`, `ImagesConverterService.convertToPojo` | service only |

But the write behaviour is unusual: when `getExisting` finds no match, the `product` domain
**constructs an `Image` itself and lets its own association cascade persist it**, bypassing
this domain's create operation, duplicate guard and route-layer validation
(BR-IMAGE-024). A blank filename is caught, logged at debug level and the image **silently
dropped** — the product still succeeds with fewer images than requested.

Full specification: `specs/06-integration-points.md` §2.3.

**Extraction assessment.** One consumer, no repository reach, and a small service surface
make `image` a comparatively good candidate for early extraction — **provided** two defects
are fixed first: the three-way uniqueness incoherence (BR-IMAGE-002) and unbounded deletion
(BR-IMAGE-013). The shared transaction that currently makes "create a product and its
images" atomic disappears at a service boundary, and compensation is unsafe until deletion
is bounded. See `specs/06-integration-points.md` §4.
