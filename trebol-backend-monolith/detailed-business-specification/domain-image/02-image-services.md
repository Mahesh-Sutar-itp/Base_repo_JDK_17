# 02 — Image Services, Repository and Controller

Source files covered:

| File | LOC | Role |
|---|---|---|
| `src/main/java/org/trebol/jpa/repositories/ImagesRepository.java` | 33 | repository |
| `src/main/java/org/trebol/jpa/services/conversion/ImagesConverterService.java` | 29 | contract |
| `src/main/java/org/trebol/jpa/services/conversion/impl/ImagesConverterServiceImpl.java` | 56 | service |
| `src/main/java/org/trebol/jpa/services/crud/ImagesCrudService.java` | 29 | contract |
| `src/main/java/org/trebol/jpa/services/crud/impl/ImagesCrudServiceImpl.java` | 64 | service |
| `src/main/java/org/trebol/jpa/services/patch/ImagesPatchService.java` | 29 | contract |
| `src/main/java/org/trebol/jpa/services/patch/impl/ImagesPatchServiceImpl.java` | 70 | service |
| `src/main/java/org/trebol/jpa/services/predicates/ImagesPredicateService.java` | 29 | contract |
| `src/main/java/org/trebol/jpa/services/predicates/impl/ImagesPredicateServiceImpl.java` | 67 | service |
| `src/main/java/org/trebol/jpa/sortspecs/ImagesSortSpec.java` | 39 | helper |
| `src/main/java/org/trebol/api/controllers/DataImagesController.java` | 128 | controller |

---

## 0. How this domain is assembled

The standard five-part pattern: repository, converter, patch service, CRUD service,
predicate service — each a narrow interface plus one implementation, wired by injection —
plus a sort-specification allow-list and a controller.

Three of the four domain-specific contracts (`ImagesConverterService`, `ImagesCrudService`,
`ImagesPatchService`) are **empty marker interfaces**: they declare no methods and exist
only to bind the generic shared-kernel contracts to this domain's model/entity type pair so
the injection container can resolve them. A rewrite in a language without that constraint
can collapse them away. `ImagesPredicateService` is the exception — it carries the
query-root constant (§6.1).

The real behaviour splits between the shared-kernel generic bases and **three**
domain-specific overrides: `getExisting` (§3.1), the converter (§4) and the patch service
(§5).

**Unlike the sibling `person` domain, this controller extends the write-capable base**
(`DataCrudGenericController`), so all five CRUD operations are exposed over HTTP (§7.3).

Transaction scope: the CRUD service implementation and the generic base it extends are both
transactional at class level, so every public CRUD method runs in a transaction. The
converter, patch and predicate services are non-transactional in-memory translators.

---

## 1. Contracts — `ImagesConverterService`, `ImagesCrudService`, `ImagesPatchService`

Each is an empty interface extending the corresponding shared-kernel generic contract with
this domain's type pair (`ImagePojo`, `Image`). No methods, no constants, no default
implementations, no error paths. Type binding only, per §0. Their generic parents are
specified in `domain-decomposition-overview/shared-kernel.md`.

---

## 2. `ImagesRepository`

### 2.1 Overview

Extends the shared-kernel generic repository, inheriting the standard surface — find by
identifier, find all, paged find all, count, count matching a predicate, find one matching
a predicate, find all matching a predicate, exists, save, save-and-flush, delete, delete
all — and adds one domain-specific query method.

### 2.2 `findByFilename(String filename)`

**Purpose.** Resolve an image by filename. This single method is the whole basis of the
domain's duplicate detection, and the reason `filename` rather than `code` is the operative
business key.

**Parameters.** `filename` — matched **exactly and case-sensitively**; no trimming, no
normalization, no case folding. The query is derived from the method name, producing a
simple equality on `image_filename`.

**Return value.** An optional holding the match, empty if none. The column is unique at the
database level, so at most one row can match.

**Side effects.** None — read-only.

**Error paths.** A null argument queries for a null filename, matches nothing (the column
is `NOT NULL`), and returns empty rather than failing. No exception is declared. Callers
guard against blank input themselves (§3.1).

**Query characteristics.** Single-table, no joins, indexed lookup against `UK_IMG_FILENAME`.

**⚠ What it does not do.** It does not resolve by `code` or `url`, even though both are
independently unique. That gap is BR-IMAGE-002 — see §3.2.

### 2.3 Inherited surface exercised by this domain

Live: `findByFilename` (§3.1), `findOne` with a predicate, `findAll` with pagination,
`findAll` with predicate and pagination, `count`, `count` with a predicate, `saveAndFlush`,
`deleteAll`. Unlike the sibling `person` domain, **all of these are reachable over HTTP**
because the controller exposes the full CRUD surface (§7.3).

---

## 3. `ImagesCrudServiceImpl`

### 3.1 `getExisting(ImagePojo input)` — the only CRUD override

**Purpose.** Resolve an inbound transport model to the existing image it refers to. This is
the domain's identity resolution and the hook the generic base calls to detect duplicates
before creating.

**Parameters.** `input` — a transport model. The method reads **only** its `filename`;
`code` and `url` are ignored entirely.

**Behaviour, branch by branch.**

1. Read `filename` from the input into a local variable.
2. **If the value is blank** — null, empty, or whitespace-only — throw a bad-input exception
   with the exact message `"Invalid filename"`. Blankness, not nullness, is the test: `"  "`
   is rejected.
3. **Otherwise** delegate to `findByFilename` and return its result directly.

**Return value.** An optional holding the matched entity, or empty when no image carries
that filename.

**Side effects.** None beyond the read.

**Error paths.**

| Condition | Outcome |
|---|---|
| `filename` is null, empty, or whitespace-only | bad-input exception, `"Invalid filename"` → **HTTP 400** |
| `filename` present but matches no row | no exception; empty optional |
| `code` or `url` blank | **not checked here** — passes through, fails at the database |

**Rewrite note.** This one method defines "what identifies an image" for the entire generic
CRUD machinery. Everything else the CRUD service does is inherited behaviour parameterised
by it. The error message is correctly scoped to this domain, unlike the sibling `shipper`
domain's copy-paste defect.

### 3.2 `create(ImagePojo input)` — inherited; the three-way uniqueness defect

**Behaviour, in order.**

1. **Duplicate guard.** The generic pre-creation validation calls `getExisting(input)`
   (§3.1). If it returns a present optional, throw an entity-already-exists exception with
   the shared literal message `"The item already exists"`. A bad-input exception from
   `getExisting` (blank filename) propagates unchanged.
2. **Convert.** `convertToNewEntity` (§4.2) builds an unsaved entity.
3. **Persist.** Save and flush. The database assigns the identifier. The immediate flush
   means any constraint violation surfaces here rather than at commit.
4. **Convert back.** `convertToPojo` (§4.3) on the saved entity; return the result.

**⚠ The central defect of this domain.** The guard in step 1 checks **filename only**, but
step 3 writes against **three** independent unique constraints (`UK_IMG_CODE`,
`UK_IMG_FILENAME`, `UK_IMG_URL`). So:

| Inbound image | Guard outcome | Database outcome | Client sees |
|---|---|---|---|
| filename collides | caught in step 1 | not attempted | **409** `"The item already exists"` |
| `code` collides, filename free | **passes** | `UK_IMG_CODE` violation | **500** raw constraint violation |
| `url` collides, filename free | **passes** | `UK_IMG_URL` violation | **500** raw constraint violation |

Two of the three duplicate cases are reported as server errors. BR-IMAGE-002. A rewrite
should either widen the check to all three fields — converting those 500s into 409s — or
reduce the constraint set. The present combination is incoherent.

**Return value.** A transport model reflecting persisted state, **without the new
identifier** (the model has no such field, and the converter does not copy one). A caller
that creates an image cannot learn its key and must re-query by filename.

**Side effects.** One row inserted into `images`. No events, no cache invalidation, no
notification, no audit record. Nothing is uploaded or fetched.

**Concurrency.** The duplicate check and the insert are separate statements in one
transaction with no lock between them. Two concurrent creations of the same filename can
both pass the check; the second insert then fails on `UK_IMG_FILENAME`. The surfaced error
differs by race outcome — a clean 409 if serialized, a raw violation if interleaved. The
unique constraints are what actually guarantee uniqueness. BR-IMAGE-010.

**Reachability.** `POST /data/images`, authority `images:create`. Also reached internally
from the `product` domain — but **only `getExisting`**, not `create`; the product domain
builds and persists `Image` instances itself (`specs/06-integration-points.md` §2).

### 3.3 `readMany(pageIndex, pageSize, order, filters)` — inherited

**Behaviour.** Build a pagination request (including the sort only if non-null). **Count**
— all rows if no filter, matching rows otherwise; a separate round trip. **Fetch** the page,
filtered or not. **Convert** each entity in order via §4.3. Return a page container with
the list, page index, total count and page size.

**Return value.** A page container; the total is the count of *matching* rows, not of
returned rows.

**Side effects.** None. Two queries per call.

**Error paths.** No domain exception. A page index beyond the end yields an empty list with
a non-zero total. Negative index or non-positive size is rejected by the pagination
framework before this method.

### 3.4 `update(ImagePojo input, Predicate filters)` — inherited, reachable via `PUT`

**Behaviour.** Count matching rows. **If more than one, throw a plain runtime exception**
with the message `"Cannot update more than one item at a time"` — unchecked and unmapped,
so **HTTP 500** (BR-IMAGE-014). If none, return empty, which the controller converts to a
404 (§7.3). Otherwise take the matched row's identifier and delegate to the by-identifier
variant: convert the input as a **brand-new entity**, force the identifier onto it, save
and flush, convert back.

**⚠ Destructive replace semantics.** Because the input is converted as a new entity, every
field absent from the input becomes null — a true replace, not a merge. All three fields
are `NOT NULL`, so a `PUT` omitting any of them fails at the database. In practice the
transport validation on `PUT` requires all three to be non-blank, which masks this; but a
rewrite must not treat `PUT` as a partial update. BR-IMAGE-015.

A match count of **zero** is not an error here — only two or more.

### 3.5 `partialUpdate(changes, Predicate filters)` and `flushPartialChanges` — inherited, reachable via `PATCH`

**Behaviour.**

1. Count matching rows; **if more than one**, throw the same plain runtime exception as
   §3.4 → 500.
2. Find the one matching row. If absent, return empty → the controller raises 404.
3. Call the patch service (§5.1), which returns a **clone** carrying the changes.
4. **Compare** original to clone using full-field equality.
5. **If equal, return the original with no database write.** Otherwise save and flush the
   clone.

Step 5 implements BR-IMAGE-009 and depends entirely on the entity's all-fields equality —
see document `01` §1.5.

**Error paths.** A bad-input exception from the patch service is caught and **rethrown
wrapped in an unchecked runtime exception**, so patch validation failures surface as 500
rather than 400. For this domain the patch service never throws bad-input (§5.1), so the
only reachable case is a non-string value producing a class-cast failure — also a 500.
BR-IMAGE-016.

### 3.6 `readOne(Predicate filters)` — inherited

**Behaviour.** Find one matching row. **If absent, throw the framework's generic
entity-not-found exception** with the shared literal `"Requested item(s) not found"` → 404.
Otherwise convert and return.

**Note.** No HTTP route calls this — the controller exposes no single-fetch operation
(§7.3). If more than one row matches, the underlying find-one call fails rather than
returning the first match; reachable in principle via the substring filters.

### 3.7 `delete(Predicate filters)` — inherited, reachable via `DELETE`

**Behaviour.** Count matching rows. **If zero, throw entity-not-found** with
`"Requested item(s) not found"` → 404. Otherwise fetch all matching rows and delete them as
a batch.

**⚠ Mass-deletion hazard.** The guard checks only that the match is non-empty — there is no
upper bound. A filter matching many images deletes all of them in one call, and a
sufficiently permissive filter empties the registry. Worse, the controller's delete
behaviour makes an **empty** parameter map a silent no-op rather than an error (§7.3), so
the dangerous and the harmless cases are adjacent. BR-IMAGE-013.

**Referential consequence.** Images referenced by products are not protected here; the
outcome depends on the `product_images` join-table constraint. See
`specs/01-data-model.md` §3.1.

### 3.8 `update(ImagePojo input)` — inherited, unsupported

The single-argument update **throws an unsupported-operation exception with the message
`"This method signature has been deprecated"`** unconditionally. Retained for interface
compatibility; no caller. Omit from a rewrite. BR-IMAGE-017.

### 3.9 Constructor

Takes the repository, converter and patch service; passes all three to the generic base and
additionally retains the repository in its own field — the base keeps it privately, so the
override in §3.1 needs its own reference to reach `findByFilename`. No other side effects.

---

## 4. `ImagesConverterServiceImpl`

### 4.1 Overview

Bidirectional entity ⇄ model translation. Stateless, non-transactional, no injected
dependencies, no logging. Three methods, one of which always fails.

**Simpler than its siblings:** every field is mandatory and non-nullable, so unlike the
`person` domain's converter there are **no null-guards and no defaulting** in either
direction. Both conversions are straight four-field copies minus the identifier.

### 4.2 `convertToNewEntity(ImagePojo source)`

**Purpose.** Build a new unsaved entity from a transport model, for the creation path.

**Behaviour.** Build an entity via its builder setting `code`, `filename` and `url` from the
source. **The identifier is not set** — it stays null so the database assigns it. No
branches, no conditionals, no defaults.

**Return value.** An unsaved entity with a null identifier.

**Side effects.** None; does not persist.

**Error paths.** The contract declares a bad-input exception, but **this implementation
never throws it.** A null source fails with a null dereference. Null or blank fields pass
through silently and fail later at the database. There is no validation whatsoever in this
method.

### 4.3 `convertToPojo(Image source)`

**Purpose.** Produce the external representation. Used by every read path and by the
`product` domain (`specs/06-integration-points.md` §2).

**Behaviour.** Build a transport model via its builder from `code`, `filename` and `url`.
**The identifier is deliberately not copied** — the model has no such field. No branches.

**Return value.** A transport model; never null for a non-null input.

**Error paths.** None. A null source fails with a null dereference; the contract's "returns
null if not persistent" note is **not** implemented — there is no null check.

**Information-hiding consequence.** Since the identifier is never emitted, no response
anywhere reveals an image's surrogate key. BR-IMAGE-012.

### 4.4 `applyChangesToExistingEntity(ImagePojo source, Image target)` — unsupported

**Throws an unsupported-operation exception with the exact message `"This method is
deprecated"`** unconditionally. No parameter is examined. The patch service (§5) superseded
it; every converter in the codebase stubs it out identically.

**Rewrite guidance.** Omit it. Unlike the sibling `person` domain — where the structurally
identical patch-service stub has a live caller — **this stub has no caller**, so deleting it
is safe. BR-IMAGE-017.

---

## 5. `ImagesPatchServiceImpl`

### 5.1 `patchExistingEntity(Map<String,Object> changes, Image existing)`

**Purpose.** Produce a modified **clone** from a sparse change map. Cloning rather than
mutating is what lets the caller detect "nothing changed" and skip the write (§3.5).

**Parameters.** `changes` — field name to new value, keyed by **transport-model field
names**. `existing` — the loaded entity; never mutated.

**Behaviour.** Clone `existing` via the copy constructor (document `01` §1.4) — identifier
included. Then, for each of the three fields in this fixed order:

| # | Key | Behaviour when key present |
|---|---|---|
| 1 | `code` | Cast the value to a string. **If not blank, set it. If blank, leave the clone's value untouched.** |
| 2 | `filename` | Same blank-guarded behaviour. |
| 3 | `url` | Same blank-guarded behaviour. |

Keys absent from the map are untouched. Unrecognised keys are **silently ignored** — no
error, no warning, no log entry. Return the clone.

**Return value.** A clone carrying the changes, with the same identifier. Never null.

**Side effects.** None. Does not persist; does not modify `existing`.

**Error paths.**

| Condition | Outcome |
|---|---|
| a value is not a string | **unchecked class-cast failure** — not the declared bad-input exception. Surfaces as 500, additionally wrapped by `partialUpdate` (§3.5). |
| a key is unrecognised | silently ignored |
| a value is blank | silently ignored; previous value retained |
| `existing` is null | null dereference in the copy constructor |

The declared bad-input exception is **never thrown** by this implementation.

**⚠ Uniform blank-guarding — and why that matters.** All three fields are blank-guarded,
with **no asymmetry**. This differs from the sibling `person` domain, where two of six
fields deliberately accept blanks to allow clearing. Here, **no field can ever be cleared
by a patch**, which is correct given all three columns are `NOT NULL`. A client sending
`{"code": ""}` receives a success response while nothing changes — and by BR-IMAGE-009 no
write occurs either. Silent, but safe. BR-IMAGE-007.

**Null handling.** A null value for any field is caught by the blank test and ignored, so
`{"url": null}` is a safe no-op. There is no path by which a patch can write a null into a
`NOT NULL` column — again unlike the `person` domain.

**⚠ Patching into a uniqueness collision.** A patch *can* change `code`, `filename` or
`url` to a value already held by another row. The patch service does not check, and
`flushPartialChanges` does not either — the write proceeds and fails at the database with a
raw constraint violation → **500**. This is BR-IMAGE-002 reappearing on the patch path:
there is no clean 409 on `PATCH` for any of the three fields, not even `filename`.

### 5.2 `patchExistingEntity(ImagePojo changes, Image target)` — unsupported

**Throws an unsupported-operation exception with the exact message `"This method signature
has been deprecated"`** unconditionally. The map-based overload replaced it.

**No live caller** — verified by call-site census (`specs/06-integration-points.md` §2).
Safe to omit from a rewrite. Note the sibling `person` domain's identical stub *does* have a
caller and is a live defect there; this domain does not share that problem.

---

## 6. `ImagesPredicateService` and `ImagesPredicateServiceImpl`

### 6.1 Contract

Unlike the other three contracts, this one is not empty: it declares the query base path
for the image entity, which the implementation builds conditions against. In a rewrite this
is simply "the table/alias conditions are built against" and needs no separate declaration.

### 6.2 `parseMap(Map<String,String> queryParamsMap)`

**Purpose.** Translate raw query parameters into a filter condition. This is the domain's
entire search capability **and** the addressing mechanism for `PUT`, `PATCH` and `DELETE`
(§7.3) — so it governs writes, not just reads. That dual role makes its quirks far more
consequential here than in a read-only domain.

**Parameters.** `queryParamsMap` — raw parameters, including ones this method does not
recognise (pagination and sort keys among them).

**Behaviour.** Create an empty condition accumulator and iterate the entries. **Iteration
order is the map's and is not guaranteed.** For each entry, match the parameter name:

| Parameter | Condition | Combination |
|---|---|---|
| `id` | `image_id` equals the value parsed as a 64-bit integer | **returns immediately** |
| `code` | `image_code` equals the value exactly, case-sensitively | **returns immediately** |
| `filename` | `image_filename` equals the value exactly, case-sensitively | **returns immediately** |
| `codeLike` | `image_code` contains the value, case-insensitively | AND-ed into the accumulator |
| `filenameLike` | `image_filename` contains the value, case-insensitively | AND-ed |
| anything else | no condition | silently ignored |

"Contains, case-insensitively" is a case-insensitive pattern match with the value wrapped
in leading and trailing wildcards. The three exact matches are **case-sensitive**; the two
substring matches are not. A rewrite must preserve this per-parameter distinction — it is
not uniform. BR-IMAGE-006.

After the loop, return the accumulator. An accumulator with nothing added matches
**everything**.

**⚠ Three identity parameters short-circuit.** `id`, `code` and `filename` each **return
immediately from inside the loop**, discarding accumulated conditions and every unvisited
parameter. So `?filename=a.png&codeLike=zzz` filters by filename alone. With two or three
identity parameters supplied, which one wins depends on map iteration order and is
genuinely **non-deterministic**. BR-IMAGE-008.

**⚠ There is no filter on `url`.** Neither exact nor substring. The third unique field is
unaddressable and unsearchable: an image cannot be looked up, updated or deleted by URL. A
rewrite adding a `url` filter changes the addressable surface, including for writes.

**⚠ Why the empty-accumulator case is dangerous here.** In a read-only domain, "matches
everything" means an unfiltered first page — harmless. In this domain the same predicate
also reaches `DELETE` and `PUT`. A `DELETE` with only unrecognised parameters — a typo such
as `?fileName=x` — produces a match-everything predicate and **deletes the entire
registry**. The controller's only protection is a non-empty-map check, which a typo
satisfies (§7.3). BR-IMAGE-013.

**⚠ Wildcard injection in pattern matches.** User values are interpolated into the pattern
without escaping, so a metacharacter acts as a wildcard rather than matching literally —
`?codeLike=%` matches every image. Values are still bound as query parameters, so this is
not SQL injection, but it lets a caller broaden a match beyond what the parameter implies.
On `DELETE` that is a destructive broadening. BR-IMAGE-011.

**Error paths.**

| Condition | Outcome |
|---|---|
| `id` is not parseable as a number | The number-format failure is **caught, logged at info level** with parameter name and value, and the loop **continues**. The request is not rejected. |
| any other parameter unrecognised | silently ignored |
| a value is empty | a legitimate value — an empty-string equality match, or a pattern of two adjacent wildcards matching everything |

So `?id=abc` on a `GET` returns an unfiltered page with 200. On a `DELETE`, `?id=abc`
produces a match-everything predicate — the swallow-and-continue behaviour is benign on
reads and destructive on writes. BR-IMAGE-004.

**Note on catch placement.** The handler wraps the whole switch inside the loop, so a parse
failure aborts only that parameter's handling; earlier accumulated conditions survive.

**Side effects.** None beyond the info-level log line on a parse failure. The method builds
a condition; it does not execute a query.

---

## 7. `DataImagesController` and `ImagesSortSpec`

### 7.1 `ImagesSortSpec` — sortable-field allow-list

A non-instantiable holder (private constructor) exposing one constant: a map from a public
sort key to an ordering over an image column.

| Sort key | Orders by | Direction as declared |
|---|---|---|
| `code` | `image_code` | ascending |
| `filename` | `image_filename` | ascending |
| `url` | `image_url` | ascending |

Three keys, three distinct orderings, no aliases. No `id` key — consistent with the
identifier never being exposed. The declared direction is a default the sort parser may
invert (§7.2). The map is an immutable allow-list: a key outside it cannot reach the
database.

Note `url` **is** sortable even though it is not filterable (§6.2).

### 7.2 Sort and pagination resolution (shared-kernel behaviour)

**Page index.** Absent `pageIndex` → `0`. Present → parsed as an integer; a non-numeric
value raises a number-format failure that is **not** caught (unlike the `id` filter) and
surfaces as an error response.

**Page size.** Absent `pageSize` → the configured default items-per-page. Present → parsed
and **capped**: honoured if below the configured maximum, otherwise the maximum. A caller
cannot force an unbounded page.

**Sort.** Absent `sortBy` → unsorted. Present → look the value up in the allow-list (§7.1),
then read `order`: `asc` → ascending, `desc` → descending, anything else → the declared
default direction.

**⚠ Known defect.** If `sortBy` names a key **not** in the allow-list, the lookup yields
nothing and the code builds a sort from it without a presence check → null dereference →
**HTTP 500**. Separately, if `sortBy` is supplied but `order` is not, switching on the
absent direction also dereferences null → 500. So `?sortBy=unknown` and `?sortBy=code`
(without `order`) both fail with 500 rather than being ignored or defaulted.

This is shared-kernel behaviour affecting every list endpoint in the system; recorded in
`cross-cutting-documentation/error-handling-patterns.md` and, for this domain, as
BR-IMAGE-018.

### 7.3 `DataImagesController`

**Class-level configuration.**

| Aspect | Value |
|---|---|
| Base path | `/data/images` |
| Documentation tag | `Images management` |
| Class-level access rule | **authentication required** |
| Extends | the shared-kernel **write-capable** generic CRUD controller, bound to this domain's types |

**Constructor.** Takes the pagination service, sort parser, this domain's CRUD service and
predicate service; passes all four to the generic base. No other side effects.

**The five operations.**

| Method | Route | Authority | Success status | Body | Delegates to |
|---|---|---|---|---|---|
| `readMany` | `GET /data/images` | `images:read` | 200 | — | §3.3 via the read pipeline |
| `create` | `POST /data/images` | `images:create` | **201 Created** | validated `ImagePojo` | §3.2 directly on the CRUD service |
| `update` | `PUT /data/images` | `images:update` | **204 No Content** | validated `ImagePojo` | §3.4 |
| `partialUpdate` | `PATCH /data/images` | `images:update` | **204 No Content** | free-form key/value map | §3.5 |
| `delete` | `DELETE /data/images` | `images:delete` | **204 No Content** | — | §3.7 |

Note `PUT` and `PATCH` share the single authority `images:update`; there is no separate
replace-versus-patch privilege.

**`readMany(allRequestParams)`** — forwards the complete raw query string to the generic
read pipeline: resolve page index, resolve and cap page size, parse sort (§7.1–7.2) and
filters (§6.2) if any parameters were supplied, then call §3.3. Returns a page of transport
models. Errors: 401, 403, and 500 from the §7.2 sort defects. No 404 — an out-of-range page
is an empty list.

**`create(input)`** — the body is **validated**, so the three non-blank rules apply
(document `01` §2.4). Calls the CRUD service's `create` directly rather than through a base
method. Returns **nothing**: a 201 with an empty body. The created resource is not echoed
and carries no identifier, so a client cannot learn what was created beyond what it sent.
Errors: 400 blank field or blank filename; 409 filename duplicate; **500 on a `code` or
`url` duplicate** (BR-IMAGE-002).

**`update(input, requestParams)`** — **addressed by query parameters, not by a path
identifier.** Behaviour in the base: **if the parameter map is empty, throw a bad-input
exception with the literal message `"Missing request params"`** → 400. Otherwise parse the
parameters into a predicate (§6.2) and call §3.4; **if that returns empty, throw
entity-not-found with the literal message `"No element was found to update"`** → 404. Body
validated. Semantics are a destructive replace (BR-IMAGE-015).

**`partialUpdate(input, requestParams)`** — same addressing and the same two guards with
the same two literal messages (`"Missing request params"` → 400,
`"No element was found to update"` → 404). The body is a **free-form map and is not
validated**; per-field blank guarding happens in the patch service (§5.1). Errors: 500 on a
non-string value; 500 on a uniqueness collision; 500 if more than one row matches.

**`delete(requestParams)`** — **⚠ the guard here is inverted relative to the other two.**
The base checks `if (!requestParams.isEmpty())` and only then parses a predicate and
deletes. So:

| Request | Outcome |
|---|---|
| `DELETE /data/images` with **no** parameters | **Silent no-op.** Nothing is deleted, no error is raised, and **204 No Content** is returned — indistinguishable from a successful deletion. |
| `DELETE` with parameters matching nothing | 404 `"Requested item(s) not found"` from §3.7. |
| `DELETE` with parameters matching many | **All of them are deleted.** No upper bound. |
| `DELETE` with only *unrecognised* parameters (e.g. a typo) | The map is non-empty, so the guard passes; the predicate matches **everything**; **the entire registry is deleted** and 204 returned. |

The last row is the dangerous one: an empty map is safe, but a *typo* is catastrophic. This
is the interaction of §6.2's match-everything default with the base's non-empty check.
BR-IMAGE-013. A rewrite must require at least one *recognised* selector before deleting.

**`getOrderSpecMap()`** — protected override returning the §7.1 allow-list. Called by the
base during sort resolution. No side effects, no error paths.

### 7.4 Comparison with the read-only sibling pattern

Worth stating explicitly, because the two patterns coexist in this codebase and are easy to
conflate. The `person` domain's controller extends the **read-only** base and therefore
exposes one route; this controller extends the **write-capable** base and exposes five. The
underlying CRUD service is the same generic class in both cases — the difference is purely
which controller base is chosen. A rewrite inferring capability from the service layer will
get it wrong in both directions.
