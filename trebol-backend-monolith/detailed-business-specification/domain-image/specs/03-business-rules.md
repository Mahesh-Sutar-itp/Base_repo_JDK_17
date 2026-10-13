# Image Registry — Business Rules

Domain code: `IMAGE`

Rules `BR-IMAGE-001` … `BR-IMAGE-015` are carried forward from
`business-rules-extraction/detailed-domain-bre/domain-image/business-rules.md` where they
correspond; `BR-IMAGE-016` and above are new findings from method-level and call-site
analysis. Each rule is a testable assertion.

---

## Identity and uniqueness

### BR-IMAGE-001 — A filename identifies at most one image, and is the only resolution key

The system MUST NOT contain two images with the same filename. Filename MUST be the **only**
field by which the service layer resolves an existing image.

- **Enforced by:** `UK_IMG_FILENAME` on `images.image_filename` (database);
  `ImagesCrudServiceImpl.getExisting` → `ImagesRepository.findByFilename` (application).
- **Test:** create an image with filename `a.png`; create a second with the same filename
  and a different code and url; the second MUST fail with 409.
- **Note:** matching is exact and case-sensitive — `A.PNG` and `a.png` are distinct.

### BR-IMAGE-002 — Code and URL are unique in the database but unchecked in the service

`image_code` and `image_url` MUST each be unique. The service layer MUST NOT check either,
so a collision on them MUST surface as an unhandled constraint violation (HTTP 500) rather
than a conflict (409).

- **Source:** three independent unique keys (`UK_IMG_CODE`, `UK_IMG_FILENAME`,
  `UK_IMG_URL`) versus a single `findByFilename` check in `getExisting`.
- **Test:** create image `{code: "C1", filename: "a.png", url: "u1"}`. Then create
  `{code: "C1", filename: "b.png", url: "u2"}` — filename is free, so the guard passes and
  the request MUST fail with **500**, not 409. Repeat with a colliding `url` for the same
  result.
- **Also applies on `PATCH`:** patching any of the three fields into an existing value
  produces the same 500 — there is no clean 409 on the patch path for *any* field,
  including filename.
- **Rewrite note:** the defining incoherence of this domain. Either widen the existence
  check to all three fields (converting these 500s into 409s) or reduce the constraint set.
  Do not preserve the present combination.

### BR-IMAGE-003 — An image submitted without a filename is rejected; a blank code or URL is not

A write whose filename is null, empty or whitespace-only MUST be rejected with a bad-input
error carrying the literal message `"Invalid filename"` → HTTP 400.

A blank `code` or `url` MUST NOT be rejected by the service layer.

- **Source:** the blank test in `getExisting` covers `filename` only.
- **Test:** via the internal path from the `product` domain, submit an image with a valid
  filename and a blank code — it MUST reach the database and fail there, **not** with a
  validation error. On `POST`/`PUT` the route-layer non-blank rules catch it first with a
  400.
- **Note:** the three transport non-blank rules apply only to `POST` and `PUT`. `PATCH` and
  the internal path escape them entirely.

### BR-IMAGE-010 — The duplicate check is not race-safe

Two concurrent creations of the same filename MAY both pass the application check; the
database unique key MUST reject the second.

- **Source:** in `create`, the `getExisting` check and the insert are separate statements in
  one transaction with no lock between them.
- **Test:** issue two concurrent creations with the same filename; exactly one MUST succeed,
  and the failure MAY surface as a raw constraint violation rather than the clean 409 of
  BR-IMAGE-001.
- **Rewrite note:** keep all three unique constraints. They — not the application check —
  are what enforce uniqueness.

### BR-IMAGE-012 — An image's surrogate identifier is never exposed

No response MUST include the image's surrogate key, and no request body MUST be able to
supply one.

- **Source:** `ImagePojo` declares no identifier field;
  `ImagesConverterServiceImpl.convertToPojo` does not copy one.
- **Test:** inspect any `GET /data/images` response element — it MUST contain exactly
  `code`, `filename` and `url`.
- **Note:** the `id` *filter* parameter does accept an identifier, so the key is usable as
  input but never returned. A caller that does not already know a key cannot discover one.
  A `POST` returns 201 with an **empty body**, so a creator learns nothing about what it
  created.

---

## Field content and validation

### BR-IMAGE-005 — The URL is length-constrained but never validated or dereferenced

The system MUST accept any non-blank URL value of 1 to 500 characters. It MUST NOT check
syntax, scheme, host or reachability, and MUST NOT fetch the resource.

- **Source:** the ORM length rule on `Image.url`; the non-blank rule on `ImagePojo.url`. No
  format rule or HTTP client exists anywhere in the domain.
- **Test:** store `"not a url"` as a URL — it MUST be accepted.
- **Scope:** the domain stores **metadata only**. No binary content is held, uploaded,
  resized or served, and no field records checksum, size, MIME type, dimensions or
  existence. An image record is an unverified claim about where an asset lives.

### BR-IMAGE-006 — Exact filters are case-sensitive; substring filters are not

The filters `code` and `filename` MUST match exactly and case-sensitively. The filters
`codeLike` and `filenameLike` MUST match as case-insensitive substring searches.

- **Source:** equality comparisons versus case-insensitive wildcard patterns in
  `ImagesPredicateServiceImpl.parseMap`.
- **Test:** for an image with code `"Banner"`, `?code=banner` MUST return nothing while
  `?codeLike=banner` MUST return it.

### BR-IMAGE-019 — There is no filter on URL

The system MUST provide no filter parameter — exact or substring — for `image_url`.

- **Source:** `parseMap` recognises `id`, `code`, `filename`, `codeLike` and `filenameLike`
  only.
- **Test:** `?url=u1` MUST be silently ignored, returning an unfiltered page.
- **Consequence:** because the predicate also addresses `PUT`, `PATCH` and `DELETE`, an
  image can be neither found, updated nor deleted by URL — despite `url` being unique and
  sortable. A rewrite adding a `url` filter widens the addressable surface **for writes as
  well as reads**.

---

## Patch semantics

### BR-IMAGE-007 — No field can be cleared by a patch

A patch supplying a blank value for `code`, `filename` or `url` MUST leave the stored value
unchanged, MUST NOT raise an error, and MUST report success.

- **Source:** `ImagesPatchServiceImpl.patchExistingEntity` — all three fields are
  blank-guarded, with no exceptions.
- **Test:** patch `{"code": ""}`; the response MUST be 204 and the stored code unchanged.
- **Note:** uniform, unlike the sibling `person` domain where two of six fields accept
  blanks deliberately. Correct here, since all three columns are `NOT NULL`. A null value is
  likewise caught by the blank test, so there is **no** path by which a patch writes a null
  into a `NOT NULL` column.

### BR-IMAGE-009 — A patch that changes nothing performs no write

When the patched clone is field-for-field equal to the stored entity, the system MUST
return the stored entity without issuing a database write.

- **Source:** `flushPartialChanges` in the shared-kernel CRUD base — full-field equality
  before save; depends on `Image` equality covering all four fields.
- **Test:** patch an image with its current values; no UPDATE statement MUST be issued.
- **Rewrite note:** redefining entity equality by identifier alone **silently disables all
  patching**. See document `01` §1.5.

### BR-IMAGE-020 — Unrecognised patch keys are silently ignored

A patch map containing keys outside the three known field names MUST be accepted, with the
unknown keys having no effect and producing no error or warning.

- **Source:** `patchExistingEntity` tests only for `code`, `filename` and `url` and never
  inspects the remainder of the map.
- **Test:** patch `{"mimeType": "image/png"}`; the call MUST succeed, change nothing, and
  (by BR-IMAGE-009) perform no write.

### BR-IMAGE-016 — A non-string patch value fails as an unhandled error

A patch value that is not a string MUST fail, and MUST NOT produce a clean validation
error.

- **Source:** unchecked string casts in `patchExistingEntity`; the resulting cast failure is
  further wrapped by `partialUpdate` in the CRUD base.
- **Test:** patch `{"code": 42}`; expect HTTP 500, not 400.
- **Rewrite note:** worth fixing, but fixing it changes an observable status code.

---

## Write addressing and destructive operations

### BR-IMAGE-021 — Writes are addressed by query parameters, not by a path identifier

`PUT`, `PATCH` and `DELETE` MUST select their target through the same query-parameter
predicate used for reads. There MUST be no path-identifier route form.

- **Source:** `DataImagesController` maps all three verbs to the collection path
  `/data/images`; `DataCrudGenericController` parses `requestParams` into a predicate.
- **Test:** `PATCH /data/images?filename=a.png` with body `{"code":"C2"}` MUST update that
  one image. There MUST be no `/data/images/{id}` route.
- **Consequence:** the predicate quirks of BR-IMAGE-008, BR-IMAGE-011 and BR-IMAGE-019 apply
  to **writes**, not only searches. This is the single most important structural fact about
  the domain's API.

### BR-IMAGE-022 — An empty parameter map is rejected on update but silently accepted on delete

`PUT` and `PATCH` with an empty parameter map MUST fail with a bad-input error carrying the
literal message `"Missing request params"` → 400.

`DELETE` with an empty parameter map MUST be a **silent no-op** returning 204.

- **Source:** `DataCrudGenericController` — `update` and `partialUpdate` guard with
  `if (requestParams.isEmpty()) throw`; `delete` guards with
  `if (!requestParams.isEmpty())` and otherwise does nothing.
- **Test:** `PUT /data/images` with no parameters → 400. `DELETE /data/images` with no
  parameters → **204 with nothing deleted**, indistinguishable from a successful deletion.
- **Rewrite note:** the inverted guard is a defect in reporting, not in safety — the no-op
  is the safe outcome. The unsafe case is BR-IMAGE-013.

### BR-IMAGE-013 — Deletion is unbounded, and a typo deletes the whole registry

`DELETE` MUST reject an empty match with a not-found error but MUST NOT limit how many
images a non-empty match removes.

- **Source:** `delete(filters)` in the CRUD base guards only against a zero count, then
  deletes all matches as a batch; `parseMap` returns a match-everything predicate when no
  parameter is recognised.
- **Test — the critical case:** `DELETE /data/images?fileName=a.png` (note the typo — the
  recognised key is `filename`). The map is non-empty so the controller guard passes; no
  parameter is recognised so the predicate matches everything; **every image in the system
  is deleted** and 204 is returned.
- **Also:** `DELETE ?codeLike=%` deletes everything, because the metacharacter is unescaped
  (BR-IMAGE-011).
- **Rewrite note:** require at least one **recognised** selector before deleting, and bound
  the batch. This is the highest-severity finding in the domain.
- **Referential consequence:** images referenced by products are not protected here; the
  outcome depends on the `product_images` join-table constraint, owned by the `product`
  domain.

### BR-IMAGE-008 — Three identity filters short-circuit, discarding all other filters

When the parameters include `id`, `code` or `filename`, the system MUST filter on that
parameter alone and MUST ignore every other filter parameter.

- **Source:** those three cases return immediately from inside the iteration in `parseMap`,
  discarding accumulated and unvisited conditions.
- **Test:** `?filename=a.png&codeLike=zzz` MUST return the image with that filename despite
  the non-matching code filter.
- **Hazard:** with two or three identity parameters supplied, precedence depends on map
  iteration order and is **not deterministic**. A rewrite MUST define precedence.
- **Severity:** elevated relative to a read-only domain, because this predicate also
  addresses `DELETE`.

### BR-IMAGE-011 — Pattern metacharacters in substring filters are not escaped

A user-supplied value for `codeLike` or `filenameLike` MUST be interpolated into the match
pattern without escaping, so metacharacters in the value act as wildcards.

- **Source:** the value is wrapped in leading and trailing wildcards with no escaping.
- **Test:** `?codeLike=%` MUST return every image. `DELETE ?codeLike=%` MUST delete every
  image.
- **Rewrite note:** values are bound as query parameters, so this is not SQL injection; it
  is a match-broadening flaw that becomes destructive on `DELETE`. Escape metacharacters.

### BR-IMAGE-004 — A non-numeric value for a numeric filter is ignored, not rejected

A non-numeric `id` parameter MUST NOT fail the request. The parse failure MUST be logged at
info level and the parameter skipped, with remaining parameters still applied.

- **Source:** the number-format failure is caught inside the loop in `parseMap`, logged
  with parameter name and value; iteration continues.
- **Test:** `GET ?id=abc` → HTTP 200 with an unfiltered first page, not 400.
- **Hazard:** `DELETE ?id=abc` produces a match-everything predicate. Benign on reads,
  destructive on writes.

### BR-IMAGE-015 — Full replacement is destructive, not a merge

`PUT` MUST convert the input as a brand-new entity before forcing the matched identifier
onto it, so any field absent from the input is overwritten with null.

- **Source:** `update(input, id)` in the CRUD base converts via `convertToNewEntity`, then
  sets the identifier.
- **Test:** all three fields are `NOT NULL`, so a `PUT` omitting one fails at the database.
  In practice the route-layer non-blank rules reject it with 400 first, masking the
  underlying behaviour.
- **Rewrite note:** do not treat `PUT` as a partial update. Prefer the patch semantics of
  BR-IMAGE-007/009 for partial edits.

### BR-IMAGE-014 — A multi-match write is a server error

When a `PUT` or `PATCH` predicate matches more than one image, the system MUST fail with an
unchecked error carrying the literal message `"Cannot update more than one item at a
time"` → HTTP 500.

- **Source:** `update(input, filters)` and `partialUpdate(changes, filters)` in the CRUD
  base — a plain runtime exception, not a typed bad-input exception.
- **Test:** `PATCH /data/images?codeLike=a` where two images match → 500.
- **Note:** a match count of **zero** is not this error — it returns empty, which the
  controller converts to 404 `"No element was found to update"`.
- **Rewrite note:** this describes a client error but reports a server error; map it to 400
  or 409.

### BR-IMAGE-018 — Sorting is restricted to an allow-list, and an unknown key is a server error

Sorting MUST be permitted only on `code`, `filename` and `url`. There MUST be no sort key
for the surrogate identifier.

An unknown `sortBy` value, or a `sortBy` supplied without an `order` value, currently
produces an unhandled server error rather than being ignored or defaulted.

- **Source:** the immutable allow-list in `ImagesSortSpec`; the unchecked lookup result and
  unchecked direction switch in the shared-kernel sort parser.
- **Test:** `?sortBy=id` → 500; `?sortBy=code` with no `order` → 500;
  `?sortBy=code&order=asc` → 200.
- **Scope:** shared-kernel behaviour affecting every list endpoint, not only this domain.
- **Note:** `url` is sortable but not filterable (BR-IMAGE-019) — the two allow-lists differ.

---

## Access control and lifecycle

### BR-IMAGE-023 — Every operation requires authentication and an operation-specific authority

All five routes MUST require authentication. Each MUST additionally require its own
authority literal, except that replace and patch share one.

| Operation | Authority literal |
|---|---|
| list | `images:read` |
| create | `images:create` |
| replace | `images:update` |
| patch | `images:update` |
| delete | `images:delete` |

- **Source:** the class-level authentication rule and the method-level authority rules on
  `DataImagesController`.
- **Test:** call each route anonymously (401), authenticated without the authority (403),
  and with it (success).
- **Note:** this domain is fully protected — contrast the sibling `shipper` domain, whose
  list route has **no** authority and no class-level authentication rule at all.

### BR-IMAGE-017 — Two legacy operations always fail, and neither has a caller

The single-argument full update and the model-based patch overload MUST throw an
unsupported-operation error unconditionally with the literal message `"This method
signature has been deprecated"`; the converter's apply-changes-to-existing method MUST do
the same with the literal `"This method is deprecated"`.

- **Source:** `CrudGenericService.update(M)`,
  `ImagesPatchServiceImpl.patchExistingEntity(ImagePojo, Image)`,
  `ImagesConverterServiceImpl.applyChangesToExistingEntity`.
- **Test:** invoke each; all MUST fail. Then grep for callers — there MUST be none.
- **Rewrite note:** safe to omit all three. Note the sibling `person` domain's identical
  patch stub **does** have a live caller and is a live defect there; this domain does not
  share that problem.

### BR-IMAGE-024 — The product domain creates images without this domain's knowledge

When the `product` domain links images to a product, it MUST call this domain's
`getExisting` to resolve each one, and — when no match is found — MUST construct and persist
an `Image` itself rather than calling this domain's `create`.

- **Source:** `ProductsCrudServiceImpl.makeProductImageRelationships` — calls
  `imagesCrudService.getExisting(img)`, then `match.orElseGet(() -> Image.builder()…)`,
  with persistence following from the product's association cascade.
- **Test:** create a product referencing an image filename that does not exist; a row MUST
  appear in `images` without any call to this domain's `create`, and therefore **without
  the duplicate guard, without the 409 path, and without route-layer validation**.
- **Consequence:** `create` is bypassed on the only internal write path. The `code` and
  `url` uniqueness collisions of BR-IMAGE-002 are reachable from product creation and
  surface there as product-creation failures.
- **Additional behaviour:** a bad-input exception from `getExisting` (blank filename) is
  **caught and logged at debug level** by the product domain, and that image is **silently
  dropped** from the product — the product creation still succeeds, with fewer images than
  requested. A client receives no indication.
- **Rewrite note:** this is the hard edge of extracting the domain. See
  `specs/06-integration-points.md` §2.3.

---

## Rule coverage summary

| Category | Rules |
|---|---|
| Identity and uniqueness | BR-IMAGE-001, 002, 003, 010, 012 |
| Field content and validation | BR-IMAGE-005, 006, 019 |
| Patch semantics | BR-IMAGE-007, 009, 016, 020 |
| Write addressing and destructive operations | BR-IMAGE-004, 008, 011, 013, 014, 015, 021, 022, 018 |
| Access control and lifecycle | BR-IMAGE-017, 023, 024 |
| **Total** | **24** |

**Highest-severity findings:** BR-IMAGE-013 (a typo in a `DELETE` parameter deletes the
entire registry), BR-IMAGE-002 (two of three duplicate cases report 500 instead of 409),
and BR-IMAGE-024 (the `product` domain writes images bypassing every guard).
