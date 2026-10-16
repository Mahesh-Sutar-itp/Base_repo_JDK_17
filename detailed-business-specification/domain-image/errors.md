# Image Registry — Error Codes

Application-wide error shapes, the exception-to-status mapping, and errors shared across
domains are in `cross-cutting-documentation/error-handling-patterns.md` and
`cross-cutting-documentation/common-error-codes.md`. This document covers only what is
specific to this domain.

**This domain declares no exception type of its own** — unlike the sibling `person` domain.
Every error it raises is the application's shared bad-input type, a framework exception, or
an unhandled runtime failure.

---

## 1. Errors this domain produces

Message strings are literals and part of the observable contract.

| # | Message (literal) | Exception type | Status | Trigger | Related rule |
|---|---|---|---|---|---|
| E1 | `Invalid filename` | bad-input | **400** | The submitted filename is null, empty or whitespace-only. Raised by `ImagesCrudServiceImpl.getExisting`; reached from `create` and from the `product` domain. | BR-IMAGE-003 |
| E2 | `The item already exists` | entity-already-exists | **409** | A creation carries a **filename** that already exists. Shared literal, not image-specific. | BR-IMAGE-001 |
| E3 | `Missing request params` | bad-input | **400** | `PUT` or `PATCH` called with an **empty** query-parameter map. Raised by the shared-kernel write controller. **Not raised by `DELETE`** — see §2. | BR-IMAGE-022 |
| E4 | `No element was found to update` | framework entity-not-found | **404** | A `PUT` or `PATCH` selector matched nothing. Raised by the shared-kernel write controller. | BR-IMAGE-021 |
| E5 | `Requested item(s) not found` | framework entity-not-found | **404** | A `DELETE` selector matched nothing, or `readOne` found nothing. Shared literal. | BR-IMAGE-013 |
| E6 | `Cannot update more than one item at a time` | plain runtime | **500** | A `PUT` or `PATCH` selector matched more than one image. Unchecked, so unmapped. | BR-IMAGE-014 |
| E7 | `This method signature has been deprecated` | unsupported-operation | **500** | The single-argument full update, or the model-based patch overload, is invoked. Always. **No caller.** | BR-IMAGE-017 |
| E8 | `This method is deprecated` | unsupported-operation | **500** | The converter's apply-changes-to-existing method is invoked. Always. **No caller.** | BR-IMAGE-017 |
| E9 | *(no message — persistence layer)* | constraint violation | **500** | **A `code` or `url` duplicate on create**; any unique-field duplicate on patch or replace; a concurrent create losing the filename race. | BR-IMAGE-002, BR-IMAGE-010 |
| E10 | *(no message — class-cast)* | class-cast failure, wrapped in a runtime exception | **500** | A patch change map contains a non-string value. | BR-IMAGE-016 |
| E11 | *(no message — null dereference)* | unchecked null failure | **500** | `sortBy` names a key outside the allow-list, or `sortBy` is supplied without `order`. Shared-kernel defect. | BR-IMAGE-018 |

### 1.1 The dominant quirk — duplicates are mostly 500s

E9 is the most consequential row. Because `getExisting` resolves by **filename only** while
the database enforces **three** unique keys:

| Duplicate on | Reported as |
|---|---|
| `filename`, on create | **409** (E2) — the intended behaviour |
| `code`, on create | **500** (E9) |
| `url`, on create | **500** (E9) |
| **any field**, on patch or replace | **500** (E9) — including filename |

So a client cannot reliably distinguish "you sent a duplicate" from "the server broke". On
the patch path there is no 409 at all. BR-IMAGE-002 — resolve this before extracting the
domain.

### 1.2 Server errors describing client faults

E6, E11 and most of E9 describe client mistakes but report 500 because the underlying
exceptions are unchecked and unmapped. E7 and E8 describe invoking a method that always
fails. A rewrite should map E6 to 400 or 409, E9 to 409, E11 to 400, and omit the E7/E8
methods entirely.

---

## 2. The delete asymmetry

`DELETE` does **not** raise E3 on an empty parameter map. Its guard is inverted relative to
`PUT` and `PATCH`:

| Request | Outcome |
|---|---|
| `PUT` / `PATCH` with no parameters | **400** E3 `"Missing request params"` |
| `DELETE` with no parameters | **204, silent no-op** — no error at all |
| `DELETE` with only *unrecognised* parameters | **204 — the entire registry is deleted** |

The no-op is the safe outcome; the unrecognised-parameter case is the dangerous one, because
the predicate defaults to matching everything. BR-IMAGE-013, BR-IMAGE-022.

---

## 3. Conditions that are deliberately *not* errors

A rewrite that turns any of these into an error changes observable behaviour.

| Condition | Current outcome | Rule |
|---|---|---|
| A non-numeric `id` selector | Logged at info level, parameter skipped. On a `GET`, **200** with an unfiltered page; on a `DELETE`, **the whole registry is deleted**. | BR-IMAGE-004 |
| An unrecognised query parameter | Silently ignored. Dangerous on `DELETE`. | BR-IMAGE-013 |
| A `url` selector | Silently ignored — no URL filter exists. | BR-IMAGE-019 |
| An unrecognised patch key | Silently ignored; by BR-IMAGE-009 also means no write. | BR-IMAGE-020 |
| A blank or null patch value for any field | Silently ignored; stored value retained; **204 success reported**. | BR-IMAGE-007 |
| A patch that changes nothing | 204, with no database write. | BR-IMAGE-009 |
| A URL that is not a valid URL, or unreachable | Accepted and stored. Never fetched. | BR-IMAGE-005 |
| `DELETE` with no parameters | 204, nothing deleted. | BR-IMAGE-022 |
| A page index past the end | Empty `items` with a non-zero `totalCount`, 200. | — |
| **A blank filename inside a product payload** | The image is **silently dropped**; the product succeeds with fewer images; only a debug log line records it. | BR-IMAGE-024 |

The last row is raised by this domain (E1) but **swallowed by the `product` domain**. From a
client's perspective it is not an error at all — see §4.

---

## 4. Errors raised by this domain but suppressed by its consumer

| Flow | This domain raises | The `product` domain does | Client sees |
|---|---|---|---|
| Product payload contains an image with a blank filename | E1 `"Invalid filename"` | catches it, logs at debug level, skips that image | **2xx** — product created with fewer images, no indication |
| Product payload contains an image whose `code`/`url` collides | nothing — `create` is bypassed | lets the cascade insert, which violates a unique key | **500 on product creation** — an image fault reported as a product error |

Source: `ProductsCrudServiceImpl.makeProductImageRelationships`; see
`specs/06-integration-points.md` §2.3. A service extraction must give both rows a defined
contract — today the first is decided by a swallowed exception.

---

## 5. Exception hierarchy

```
RuntimeException (platform)
 ├── framework EntityNotFoundException ............ → 404  (E4, E5)
 ├── framework EntityExistsException ............... → 409  (E2)
 ├── BadInputException (application base) .......... → 400  (E1, E3)
 ├── UnsupportedOperationException ................. → 500  (E7, E8)
 ├── ClassCastException ............................ → 500  (E10)
 ├── persistence ConstraintViolationException ...... → 500  (E9)
 └── plain RuntimeException ........................ → 500  (E6, and the wrapper around E10)
```

`BadInputException` is the application's own base type for client-input faults and the only
4xx type this domain raises itself. The other 4xx responses come from framework exceptions
raised by the shared-kernel bases.

**No domain-specific exception type exists**, and no numeric or symbolic error-code
vocabulary is defined. Errors are identified solely by exception type and literal message —
there is no machine-readable discriminator a client could branch on.
