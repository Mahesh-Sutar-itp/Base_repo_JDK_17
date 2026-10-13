# Image Registry — API Contract

Domain slug: `image` · Controllers: 1 · HTTP endpoints: 5

All five endpoints live on the single collection path `/data/images`. There is **no
path-identifier route form** — writes select their target through query parameters
(BR-IMAGE-021), which makes the filter semantics in §6 part of the write contract.

All endpoints inherit the application-wide authentication flow
(`cross-cutting-documentation/authentication-authorization.md`) and error translation
(`cross-cutting-documentation/error-handling-patterns.md`).

---

## 1. Common elements

### 1.1 Access control

| Aspect | Value |
|---|---|
| Authentication | **Required for every route** (class-level rule). |
| Authorities | `images:read`, `images:create`, `images:update` (shared by replace and patch), `images:delete`. |
| Enforcement | Declarative method-level rules on `DataImagesController`. |

### 1.2 The image object

| Field | Type | Always present | Notes |
|---|---|---|---|
| `code` | string, 1–50 chars | yes | Unique. |
| `filename` | string, 1–100 chars | yes | Unique. **The write-resolution key.** |
| `url` | string, 1–500 chars | yes | Unique. Never validated or dereferenced. |

**No identifier field** (BR-IMAGE-012). Null fields are omitted from output, though in
practice all three columns are `NOT NULL` so a persisted image always serializes all three.

### 1.3 Selector parameters — used by read **and** write

| Parameter | Type | Match | Combination |
|---|---|---|---|
| `id` | integer | exact, on the surrogate key | **short-circuits** — discards all other filters |
| `code` | string | exact, case-sensitive | **short-circuits** |
| `filename` | string | exact, case-sensitive | **short-circuits** |
| `codeLike` | string | substring, case-insensitive | AND-ed |
| `filenameLike` | string | substring, case-insensitive | AND-ed |

**There is no `url` selector** (BR-IMAGE-019) — an image cannot be addressed by URL for any
operation.

Three behaviours a client must understand, all specified in document `02` §6.2:

- **Short-circuit** (BR-IMAGE-008): supplying `id`, `code` or `filename` discards every
  other selector. With two or three of them, precedence is **non-deterministic**.
- **Unescaped metacharacters** (BR-IMAGE-011): `?codeLike=%` matches everything.
- **Unrecognised parameters are silently ignored**, and a predicate with nothing recognised
  **matches everything**. On `DELETE` this is destructive (§5.3).

A non-numeric `id` is logged and skipped, not rejected (BR-IMAGE-004).

---

## 2. `GET /data/images` — list images

| Aspect | Value |
|---|---|
| Authority | `images:read` |
| Request body | none |
| Success | **200 OK** |

**Query parameters.** The selectors of §1.3, plus:

| Parameter | Default | Behaviour |
|---|---|---|
| `pageIndex` | `0` | Zero-based. A non-numeric value raises an error response (**not** swallowed, unlike `id`). An index past the end returns an empty list with a non-zero total. |
| `pageSize` | configured items-per-page | Capped at the configured maximum. |
| `sortBy` | unsorted | Allowed: `code`, `filename`, `url`. **An unknown value → 500** (BR-IMAGE-018). Note `url` is sortable though not filterable. |
| `order` | declared ascending default | `asc` / `desc`; any other value falls back to the default. **Omitting it while supplying `sortBy` → 500.** |

**Response 200.** A page container:

| Field | Type | Description |
|---|---|---|
| `items` | array of image objects | Page contents in the requested order. |
| `pageIndex` | integer | Echoed back. |
| `pageSize` | integer | Effective size after capping. |
| `totalCount` | integer | Total rows **matching the filter**, from a separate count query. |

```json
{
  "items": [
    { "code": "BANNER_HOME", "filename": "banner-home.png", "url": "https://cdn.example.com/banner-home.png" }
  ],
  "pageIndex": 0,
  "pageSize": 10,
  "totalCount": 1
}
```

**Errors.** 401 unauthenticated; 403 without `images:read`; 500 on the sort defects or a
non-numeric `pageIndex`/`pageSize`. **No 404** — an empty result is a 200 with an empty
array.

**Behavioural notes.** Two queries per request (count, then fetch), not atomic with respect
to concurrent writes. No caching. With no selector, the endpoint pages through every image
in the system.

---

## 3. `POST /data/images` — create images

| Aspect | Value |
|---|---|
| Authority | `images:create` |
| Request body | an image object, **validated** |
| Success | **201 Created, empty body** |

**Request.** All three fields are required and must be non-blank (document `01` §2.4).

```json
{ "code": "BANNER_HOME", "filename": "banner-home.png", "url": "https://cdn.example.com/banner-home.png" }
```

**Response 201.** **No body.** The created resource is not echoed, and it carries no
identifier in any case — so a client learns nothing beyond what it sent and must re-query
by filename to confirm (BR-IMAGE-012).

**Errors.**

| Status | Condition |
|---|---|
| 400 | Any of the three fields blank or absent (route validation), or a blank filename reaching `getExisting` with the literal message `"Invalid filename"`. |
| 409 | **Filename** already exists — literal message `"The item already exists"`. |
| **500** | **`code` or `url` already exists** — raw constraint violation, because the guard checks filename only (BR-IMAGE-002). |
| 500 | Concurrent creation of the same filename losing the race (BR-IMAGE-010). |
| 401 / 403 | Unauthenticated / lacking the authority. |

**The 500 rows are the contract, not an accident.** Two of the three duplicate cases are
reported as server errors. A client cannot distinguish "your code collides" from an internal
fault.

---

## 4. `PUT /data/images` — replace image data

| Aspect | Value |
|---|---|
| Authority | `images:update` |
| Selector | **query parameters, required** |
| Request body | an image object, **validated** |
| Success | **204 No Content** |

**Behaviour.** Replace, not merge (BR-IMAGE-015): the body is converted as a brand-new
entity and the matched row's identifier is forced onto it, so any absent field becomes
null. Since all three columns are `NOT NULL`, an incomplete body fails — though route
validation rejects it with 400 first.

**Errors.**

| Status | Condition | Literal message |
|---|---|---|
| 400 | **Empty parameter map** | `"Missing request params"` |
| 400 | A body field blank or absent | route validation |
| 404 | The selector matched nothing | `"No element was found to update"` |
| 500 | The selector matched **more than one** image | `"Cannot update more than one item at a time"` (BR-IMAGE-014) |
| 500 | The new `code`, `filename` or `url` collides with another row | raw constraint violation (BR-IMAGE-002) |
| 401 / 403 | Unauthenticated / lacking the authority | — |

---

## 5. `PATCH /data/images` — update parts of image data

| Aspect | Value |
|---|---|
| Authority | `images:update` — **the same as replace**; there is no separate patch privilege |
| Selector | **query parameters, required** |
| Request body | a free-form key/value map, **not validated** |
| Success | **204 No Content** |

**Request.** Keys are **transport-model field names**: `code`, `filename`, `url`. Unknown
keys are silently ignored (BR-IMAGE-020). Values must be strings.

```json
{ "url": "https://cdn.example.com/banner-home-v2.png" }
```

**Semantics** (document `02` §5.1):

- A **blank** value for any field is silently ignored and the stored value retained —
  no field can be cleared (BR-IMAGE-007). The response is still 204.
- A patch that changes nothing performs **no database write** (BR-IMAGE-009).
- A null value is likewise ignored, so a patch can never write a null into a `NOT NULL`
  column.

**Errors.**

| Status | Condition | Literal message |
|---|---|---|
| 400 | **Empty parameter map** | `"Missing request params"` |
| 404 | The selector matched nothing | `"No element was found to update"` |
| 500 | A value is not a string | class-cast failure, wrapped (BR-IMAGE-016) |
| 500 | The selector matched more than one image | `"Cannot update more than one item at a time"` |
| 500 | The new value collides with another row's unique field | raw constraint violation — **including on `filename`**, so there is no clean 409 on this path for any field (BR-IMAGE-002) |
| 401 / 403 | Unauthenticated / lacking the authority | — |

---

## 6. `DELETE /data/images` — remove images

| Aspect | Value |
|---|---|
| Authority | `images:delete` |
| Selector | query parameters |
| Request body | none |
| Success | **204 No Content** |

**⚠ This is the most dangerous endpoint in the domain.** Its guard is inverted relative to
`PUT` and `PATCH`, and it combines with the match-everything default of §1.3.

| Request | Outcome |
|---|---|
| **No parameters** | **Silent no-op.** Nothing deleted, no error, **204** — indistinguishable from a successful deletion (BR-IMAGE-022). |
| Parameters matching nothing | **404** `"Requested item(s) not found"`. |
| Parameters matching one | That image deleted, 204. |
| Parameters matching many | **All of them deleted.** No upper bound (BR-IMAGE-013). |
| **Only unrecognised parameters** — e.g. the typo `?fileName=a.png` | The map is non-empty so the guard passes; nothing is recognised so the predicate matches everything; **the entire registry is deleted** and 204 returned. |
| `?codeLike=%` | **Entire registry deleted**, because the metacharacter is unescaped (BR-IMAGE-011). |

An empty map is safe; a *typo* is catastrophic. A rewrite MUST require at least one
**recognised** selector before deleting, and SHOULD bound the batch.

**Referential note.** Images referenced by products are not protected here. The outcome
depends on the `product_images` join-table constraint, owned by the `product` domain —
either a foreign-key failure or an orphaned relationship.

---

## 7. Service API — what the `product` domain actually calls

The HTTP surface above is complete, but it is **not** how images are written internally.
The `product` domain reaches into this domain directly, and in an unexpected way.

| Member | Caller | Behaviour |
|---|---|---|
| `ImagesCrudService.getExisting(model) → optional entity` | `ProductsCrudServiceImpl:149` | Resolves by filename; throws bad-input on a blank filename. |
| `ImagesConverterService.convertToPojo(entity) → model` | this domain's read path; the `product` domain's own converter | Four-field copy minus the identifier. |

**What the `product` domain does *not* call:** `create`. When `getExisting` returns empty,
the product domain **constructs an `Image` itself** via the entity builder and lets its own
association cascade persist it (BR-IMAGE-024). So the only internal write path bypasses
this domain's duplicate guard, its 409 path and all route-layer validation.

It also **swallows** the bad-input exception: a blank filename is caught, logged at debug
level, and that image is **silently dropped** from the product. Product creation still
succeeds with fewer images than requested, and the client is not told.

Full call census: `specs/06-integration-points.md` §2.

---

## 8. What a service extraction must address

1. **The three-way uniqueness incoherence must be resolved first** (BR-IMAGE-002).
   Shipping a service whose duplicate detection covers one of three unique fields will
   produce 500s under normal use. Widen `getExisting` to all three fields, or drop two
   constraints.
2. **`DELETE` must be made safe before exposure** (BR-IMAGE-013). Requiring a recognised
   selector is a one-line change with very high value; across a service boundary the blast
   radius is the same but the caller is further away.
3. **The `product` domain's bypass must become an API call.** Today it builds and
   cascade-persists `Image` rows directly. Across a boundary there is no cascade, so this
   becomes an explicit remote create — which means the silently-dropped-image behaviour has
   to be given a defined contract (fail the product, or report a partial success).
4. **Writes need addressable identity.** Query-parameter addressing with a short-circuiting,
   non-deterministic predicate is unsuitable for a service API. Introduce
   `/images/{filename}` or return the identifier on create.
5. **Create returns nothing.** A 201 with an empty body and no identifier means callers
   cannot confirm what they created without a second request.
6. **Decide whether the domain should own binary content at all.** It currently stores
   unverified claims about URLs (BR-IMAGE-005). A service boundary is a natural point to
   either add verification or formally document the domain as metadata-only.
