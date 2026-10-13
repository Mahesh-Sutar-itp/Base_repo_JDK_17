# Image Registry — Integration Points

## 1. External systems

**None.** The domain integrates with no external system:

- no outbound HTTP, gRPC or SOAP client;
- no message broker producer or consumer, no topic, no queue;
- no object store, CDN client, file system access or upload handler;
- no image processing — nothing resizes, converts, compresses, thumbnails or inspects an
  image;
- no cache, search index or secondary datastore;
- no scheduled or asynchronous job;
- no no-op or mock client standing in for an absent provider.

The domain's only I/O is the relational database holding the `images` table.

**The defining absence: the URL is never dereferenced.** This domain stores an absolute URL
for every image and **never fetches it**. There is no HTTP client anywhere in the domain, so
nothing verifies that the asset exists, is reachable, is actually an image, or matches the
declared filename. An image record is an **unverified claim** about where an asset lives.

Consequences a rewrite must accept or deliberately change:

| Absent capability | Consequence |
|---|---|
| No existence check | A record can point at a 404 indefinitely. Nothing detects or reports it. |
| No content-type check | A record can point at an HTML page or an executable. |
| No upload path | Binary content must already exist at the URL, placed there by some process entirely outside this system. |
| No metadata capture | No checksum, byte size, MIME type, width, height or upload timestamp. |
| No scheme or host allow-listing | Any string up to 500 characters is accepted, including a `javascript:` or `file:` URL, or a host outside the organisation's control. |

The last row deserves emphasis: these URLs are served to clients and presumably rendered.
The domain applies no allow-listing, so the integrity of rendered image sources depends
entirely on the trustworthiness of whoever holds `images:create` or `images:update`.

---

## 2. Cross-domain integration points

These are **in-process** today. They are documented here because they are the boundaries a
service extraction must convert into real integrations — and because the coupling runs
deeper than the service interface suggests.

### 2.1 Direction of dependency

This domain `depends_on_domains` **nothing**. It is a pure leaf: it calls no other domain,
holds no foreign key and declares no association. All coupling is **inbound**, from a
single consumer.

| Consumer domain | Depends on this domain for |
|---|---|
| `product` | resolving and creating the images linked to a product |

The link itself lives in `product_images`, a join table owned by the `product` domain. This
domain has no knowledge of it.

### 2.2 Inbound call boundaries — the complete census

| # | Caller (file:line) | Target | Level | What is passed | What is returned | Side effects |
|---|---|---|---|---|---|---|
| I1 | `ProductsCrudServiceImpl:149` | `ImagesCrudService.getExisting` | service | an `ImagePojo` (only `filename` is read) | optional `Image` | none; **throws bad-input on a blank filename — and the caller swallows it** (§2.3) |
| I2 | `ProductsCrudServiceImpl:150-154` | **none — the entity builder directly** | **entity construction** | `code`, `filename`, `url` from the `ImagePojo` | a transient `Image` | **a row is inserted into `images` by the product's association cascade** |
| I3 | the `product` domain's own converter | `ImagesConverterService.convertToPojo` | service | an `Image` held via `product_images` | an `ImagePojo` nested in the product model | none |

That is the entire inbound surface — **three call sites, one consumer**. Notably absent:

- **No consumer calls `create`.** The domain's own creation operation is reached only from
  `POST /data/images`.
- **No consumer calls the repository.** Unlike the sibling `person` domain, where six of
  seventeen call sites reach the repository directly, here every cross-domain call goes
  through a service interface. The boundary is cleaner.
- **No consumer calls `delete`, `update`, `partialUpdate` or `readOne`.**

### 2.3 The one behaviour that matters — `makeProductImageRelationships`

This is the whole cross-domain story, and it is unusual enough to specify step by step.
Source: `ProductsCrudServiceImpl.makeProductImageRelationships`, invoked when a product is
created or updated with a collection of images.

For each inbound `ImagePojo` in the product's payload:

1. Call `ImagesCrudService.getExisting(img)` — resolves by filename only (I1).
2. **If a match is found**, use the existing `Image` entity.
3. **If no match is found**, construct a **new transient `Image`** via the entity builder
   from the payload's `code`, `filename` and `url` (I2). **`create` is not called.**
4. Build a `ProductImage` join entity pairing the product with that `Image`.
5. Accumulate it into the returned list. The product domain persists the list, and the
   association cascade inserts any transient `Image` rows.
6. **If `getExisting` threw bad-input** (blank filename), **catch it, log at debug level
   with the product's barcode, and skip that image entirely.**

**Four consequences.**

**(a) The internal write path bypasses every guard.** Step 3 reaches the database without
the duplicate check, without the 409 path, and without route-layer validation. So a product
payload carrying an image whose `code` or `url` collides with an existing row produces a
raw constraint violation surfacing as a **product-creation failure** — the operator sees a
product error caused by image data (BR-IMAGE-002, BR-IMAGE-024).

**(b) Images are created silently, as a side effect of product writes.** Anyone with
authority to create a product can create image records, without holding `images:create`.
The authority model of this domain (BR-IMAGE-023) does not govern its only internal write
path.

**(c) A blank filename silently drops an image.** Step 6 swallows the exception. The product
is created **with fewer images than requested**, 2xx is returned, and the client is given no
indication. The only trace is a debug-level log line. This is the single most
rewrite-relevant behaviour here: a naive reimplementation that propagates the error changes
product-creation outcomes from success to failure.

**(d) Resolution is by filename, so `code` and `url` from the payload are ignored on a
match.** If an image with that filename exists, step 2 uses the stored row and **discards**
the payload's `code` and `url`. A caller attempting to update an image's URL by resubmitting
it inside a product payload will see no change and no error.

### 2.4 Outbound call boundaries

**None.** No code in this domain calls into any other domain. It reads nothing it does not
own.

---

## 3. Payload shapes at the boundary

With no external integrations, the only boundary payload is the transport model
(`specs/01-data-model.md` §2):

| Field | Type | Required | Note |
|---|---|---|---|
| `code` | string, 1–50 | yes at the route layer | unique |
| `filename` | string, 1–100 | yes at the route layer | unique; **the resolution key** |
| `url` | string, 1–500 | yes at the route layer | unique; never dereferenced |

No identifier field. JSON over HTTP on the five routes. No versioning, no content
negotiation, no schema registry.

---

## 4. Retry, ordering and delivery semantics

**Not applicable.** With no broker, no external client and no asynchronous processing, there
is no retry policy, backoff, circuit breaker, timeout, idempotency key, ordering guarantee
or delivery guarantee to document.

The only reliability property is the database transaction: every CRUD-service method runs in
one, and the product domain's create/update runs in the consumer's transaction.

**Extraction consequence.** That shared transaction is what currently makes "create a
product and its images" atomic. Split `image` into a service and the atomicity is gone:

- Image creation becomes an explicit remote call per image, outside the product's
  transaction.
- A partial failure leaves images created without the product that justified them — and
  this domain's `delete` is unbounded and unsafe (BR-IMAGE-013), so compensation is risky
  until that is fixed.
- The silent-drop behaviour of §2.3(c) needs a defined contract: fail the product, or report
  a partial success. Today the choice is made by a swallowed exception.

This is less severe than the equivalent problem in the `person` domain — one consumer rather
than three, and no direct repository reach — so `image` is a comparatively good candidate
for early extraction, **provided** BR-IMAGE-002 and BR-IMAGE-013 are fixed first.
