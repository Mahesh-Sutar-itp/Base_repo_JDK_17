# Image Registry — User Stories

Domain code: `IMAGE`

Roles:

| Role | Meaning |
|---|---|
| Catalogue administrator | An authenticated principal holding the relevant `images:*` authority. |
| Product editor | An authenticated principal creating or editing products, which writes images as a side effect. |
| Visitor | An unauthenticated caller. |

---

## US-IMAGE-001: List and page the image registry

**As a** catalogue administrator, **I want to** retrieve images one page at a time,
**so that** I can review the registry without loading every record.

**Acceptance criteria**

- GIVEN 25 images exist AND I hold `images:read`
  WHEN I request `GET /data/images?pageSize=10`
  THEN I receive 10 image objects, `pageIndex` 0, `pageSize` 10, `totalCount` 25.
- GIVEN I request `?pageIndex=99` THEN I receive an empty `items` array with 200 and
  `totalCount` 25 — **not** a 404.
- GIVEN no `pageSize` is supplied THEN the configured default applies.
- GIVEN I request a `pageSize` above the configured maximum THEN the maximum is used.
- GIVEN any response THEN **no element contains an identifier field** — only `code`,
  `filename` and `url`.

**Business rules:** BR-IMAGE-012, BR-IMAGE-023
**Data changes:** none · **Events:** none
**Errors:** 401; 403 without `images:read`; 500 on a non-numeric `pageIndex`/`pageSize`
**API:** `GET /data/images`

---

## US-IMAGE-002: Find an image by filename or code

**As a** catalogue administrator, **I want to** look an image up by its filename or code,
**so that** I can confirm whether an asset is already registered.

**Acceptance criteria**

- GIVEN an image with filename `banner.png` exists
  WHEN I request `?filename=banner.png` THEN exactly that image is returned.
- GIVEN I request `?filename=BANNER.PNG` THEN nothing is returned — exact matching is
  case-sensitive.
- GIVEN I request `?filenameLike=banner` THEN it **is** returned — substring matching is
  case-insensitive.
- GIVEN I request `?filename=banner.png&codeLike=zzz`
  THEN the image is **still returned** — the identity filter discards the code filter.
- GIVEN I request `?url=https://cdn.example.com/banner.png`
  THEN the parameter is **silently ignored** and an unfiltered page is returned — there is
  no URL filter.
- GIVEN I request `?codeLike=%` THEN **every** image is returned, because the metacharacter
  is unescaped.
- GIVEN I request `?id=abc` THEN I receive 200 with an unfiltered page, not 400.

**Business rules:** BR-IMAGE-006, BR-IMAGE-008, BR-IMAGE-011, BR-IMAGE-019, BR-IMAGE-004
**Data changes:** none
**API:** `GET /data/images`

---

## US-IMAGE-003: Sort the registry

**As a** catalogue administrator, **I want to** order results by a chosen field, **so that**
I can scan the registry predictably.

**Acceptance criteria**

- GIVEN `?sortBy=code&order=asc` THEN results are ordered by code ascending.
- GIVEN `?sortBy=url&order=desc` THEN results are ordered by URL descending — `url` **is**
  sortable even though it is not filterable.
- GIVEN no `sortBy` THEN results are unsorted and order is not guaranteed.
- GIVEN `?sortBy=code&order=sideways` THEN the ascending default applies — an unrecognised
  direction is not an error.
- GIVEN `?sortBy=id` THEN the request **fails with 500** — there is no sort key for the
  surrogate identifier and an unknown key is unhandled.
- GIVEN `?sortBy=code` with no `order` THEN the request **fails with 500**.

**Business rules:** BR-IMAGE-018, BR-IMAGE-019
**API:** `GET /data/images`

---

## US-IMAGE-004: Register a new image

**As a** catalogue administrator, **I want to** register an image's metadata, **so that**
products can reference it.

**Acceptance criteria**

- GIVEN no image carries filename `banner.png`
  WHEN I `POST` a complete image object
  THEN one row is inserted and I receive **201 with an empty body**.
- GIVEN creation succeeded
  THEN I **cannot learn the new identifier** — the response has no body and the model has
  no identifier field. I must re-query by filename to confirm.
- GIVEN any of `code`, `filename` or `url` is blank or absent
  THEN the request fails with 400 from route validation.
- GIVEN an image with that **filename** already exists
  THEN the request fails with **409** and the literal message `"The item already exists"`,
  and the existing record is unmodified.
- GIVEN an image with that **code** already exists but the filename is free
  THEN the request fails with **500** — not 409 — because the guard checks filename only.
- GIVEN an image with that **url** already exists but the filename is free
  THEN the request likewise fails with **500**.
- GIVEN the URL is `"not a url"` THEN it **is accepted** — no format check exists.
- GIVEN two concurrent creations of the same filename
  THEN exactly one succeeds, and the failure MAY surface as a raw constraint violation
  rather than a clean 409.

**Business rules:** BR-IMAGE-001, BR-IMAGE-002, BR-IMAGE-003, BR-IMAGE-005, BR-IMAGE-010,
BR-IMAGE-012
**Data changes:** one row inserted into `images` · **Events:** none
**Errors:** 400; 409 on filename; **500 on code or url**; 401/403
**API:** `POST /data/images`, authority `images:create`

---

## US-IMAGE-005: Correct an image's metadata

**As a** catalogue administrator, **I want to** change selected fields on a registered
image, **so that** a moved or renamed asset stays correctly described.

**Acceptance criteria**

- GIVEN an image exists
  WHEN I `PATCH /data/images?filename=banner.png` with `{"url": "https://cdn/x.png"}`
  THEN only the URL changes and I receive **204**.
- GIVEN I patch `{"code": ""}`
  THEN the stored code is **unchanged**, no error is raised, and I still receive 204 — no
  field can be cleared.
- GIVEN I patch `{"url": null}`
  THEN it is likewise ignored — a patch can never write a null into a `NOT NULL` column.
- GIVEN I patch with values identical to the current state
  THEN **no database write occurs** and 204 is returned.
- GIVEN I patch `{"mimeType": "image/png"}`
  THEN the unknown key is silently ignored, nothing changes, and no write occurs.
- GIVEN I patch `{"code": 42}`
  THEN the request **fails with 500**, not 400 — a non-string value is an unhandled cast
  failure.
- GIVEN I omit all query parameters
  THEN the request fails with 400 and the literal message `"Missing request params"`.
- GIVEN my selector matches nothing
  THEN the request fails with 404 and the literal message `"No element was found to
  update"`.
- GIVEN my selector matches more than one image
  THEN the request **fails with 500** and the message `"Cannot update more than one item at
  a time"`.
- GIVEN I patch `filename` to a value another image already holds
  THEN the request **fails with 500** — there is no clean 409 on the patch path for **any**
  field, including filename.

**Business rules:** BR-IMAGE-007, BR-IMAGE-009, BR-IMAGE-014, BR-IMAGE-016, BR-IMAGE-020,
BR-IMAGE-021, BR-IMAGE-022, BR-IMAGE-002
**Data changes:** at most one row updated; **zero** writes when nothing changed
**API:** `PATCH /data/images`, authority `images:update`

---

## US-IMAGE-006: Replace an image's data wholesale

**As a** catalogue administrator, **I want to** overwrite all of an image's fields at once,
**so that** I can correct several at a time.

**Acceptance criteria**

- GIVEN a selector matching exactly one image
  WHEN I `PUT` a complete image object
  THEN all three fields are overwritten and I receive **204**.
- GIVEN I omit a field from the body
  THEN route validation rejects it with 400 — which masks the underlying replace semantics,
  where an absent field would be written as null against a `NOT NULL` column.
- GIVEN I omit all query parameters THEN 400 `"Missing request params"`.
- GIVEN my selector matches nothing THEN 404 `"No element was found to update"`.
- GIVEN my selector matches more than one THEN 500 `"Cannot update more than one item at a
  time"`.
- GIVEN the new values collide with another row's unique field THEN 500.

**Business rules:** BR-IMAGE-015, BR-IMAGE-014, BR-IMAGE-021, BR-IMAGE-022, BR-IMAGE-002
**API:** `PUT /data/images`, authority `images:update` — **the same authority as patch**

---

## US-IMAGE-007: Remove an image — and the ways this goes wrong

**As a** catalogue administrator, **I want to** delete an obsolete image record, **so that**
the registry stays accurate.

**Acceptance criteria — intended use**

- GIVEN a selector matching exactly one image
  WHEN I `DELETE /data/images?filename=banner.png`
  THEN that image is deleted and I receive **204**.
- GIVEN a selector matching nothing
  THEN I receive **404** `"Requested item(s) not found"`.

**Acceptance criteria — the hazards, all current behaviour**

- GIVEN I supply **no** query parameters
  THEN **nothing is deleted** and I receive **204** — indistinguishable from a successful
  deletion.
- GIVEN I supply a **typo** such as `?fileName=banner.png` (the recognised key is
  `filename`)
  THEN the parameter map is non-empty so the guard passes, nothing is recognised so the
  predicate matches everything, and **every image in the system is deleted** — returning
  204.
- GIVEN I supply `?codeLike=%`
  THEN **every image is deleted**, because the metacharacter is unescaped.
- GIVEN I supply `?id=abc`
  THEN the unparseable value is skipped and the predicate matches everything — **every
  image is deleted**.
- GIVEN a selector matching many images
  THEN **all of them are deleted** — there is no upper bound.
- GIVEN the image is still referenced by a product
  THEN the outcome depends on the `product_images` join-table constraint — either a
  foreign-key failure or an orphaned relationship. This domain applies no protection.

**Business rules:** BR-IMAGE-013, BR-IMAGE-022, BR-IMAGE-011, BR-IMAGE-004
**Data changes:** unbounded deletion from `images`
**API:** `DELETE /data/images`, authority `images:delete`
**Rewrite note:** the highest-severity finding in the domain. An empty map is safe but a
typo is catastrophic. Require at least one **recognised** selector before deleting, and
bound the batch.

---

## US-IMAGE-008: Link images to a product

**As a** product editor, **I want to** supply image metadata as part of a product payload,
**so that** the product displays images without my registering them separately.

**Acceptance criteria**

- GIVEN a product payload containing an image whose filename **already exists**
  THEN the existing image row is linked, and the payload's `code` and `url` are
  **discarded** — no change is made to the stored image, and no error is reported.
- GIVEN a product payload containing an image whose filename **does not exist**
  THEN a new `images` row is created by the product's association cascade, **without** any
  call to this domain's create operation, **without** the duplicate guard and **without**
  route-layer validation.
- GIVEN I hold authority to create products but **not** `images:create`
  THEN I can nonetheless create image records this way.
- GIVEN a product payload containing an image with a **blank filename**
  THEN that image is **silently dropped**: the product is created successfully with fewer
  images than requested, 2xx is returned, and the only trace is a debug-level log line.
- GIVEN a product payload containing an image whose `code` or `url` collides with an
  existing row
  THEN the constraint violation surfaces as a **product-creation failure** — an image fault
  reported as a product error.

**Business rules:** BR-IMAGE-024, BR-IMAGE-002, BR-IMAGE-003, BR-IMAGE-023
**Data changes:** zero or more rows inserted into `images`, plus `product_images` rows owned
by the `product` domain
**API:** the `product` domain's write routes — **not** this domain's
**Source:** `ProductsCrudServiceImpl.makeProductImageRelationships`; see
`specs/06-integration-points.md` §2.3
**Note:** the silent-drop behaviour is the most rewrite-relevant item here. A
reimplementation that propagates the error changes product creation from success to failure.

---

## US-IMAGE-009: Be denied access without the right authority

**As a** visitor or under-privileged user, **I want to** be refused access, **so that** the
registry cannot be read or altered without authorization.

**Acceptance criteria**

- GIVEN I present no credentials THEN every one of the five routes returns **401**.
- GIVEN I am authenticated but hold no `images:*` authority THEN every route returns **403**.
- GIVEN I hold `images:read` only THEN the list succeeds and all four write routes return
  403.
- GIVEN I hold `images:update` THEN **both** `PUT` and `PATCH` succeed — they share one
  authority, with no separate replace-versus-patch privilege.
- GIVEN I hold `images:delete` THEN I can delete **without** holding `images:read`, so I can
  destroy records I cannot inspect.

**Business rules:** BR-IMAGE-023
**API:** all five routes
**Note:** this domain is fully protected, in contrast to the sibling `shipper` domain whose
list route has no authority and no class-level authentication rule.

---

## Coverage summary

| Category | Stories |
|---|---|
| Read and search | US-IMAGE-001, 002, 003 |
| Creation | US-IMAGE-004 |
| Modification | US-IMAGE-005, 006 |
| Deletion | US-IMAGE-007 |
| Cross-domain | US-IMAGE-008 |
| Access control | US-IMAGE-009 |
| **Total** | **9** |

**Categories with no stories, and why:** no state-transition stories (the entity has no
state field — `specs/00-plan.md`); no batch, scheduled or integration-triggered stories (no
background job and no external integration — `reference/background-jobs.md`,
`reference/integrations.md`); no error-recovery stories (no retry, compensation or
reconciliation logic exists).

Mode A features `F-001`…`F-008` in `detailed-domain-bre/domain-image/features.md` map to:
F-001→US-001, F-002→US-002, F-003→US-003, F-004→US-004, F-005→US-005, F-006→US-006,
F-007→US-007, F-008→US-008.
