# Permissions — Product Lists

Five authority codes exist for this domain. **Four are enforced; one is not.** One of the
four covers **three different write operations** on the contents surface.

Since Phase 0 (Gate B) every check references a constant on
`org.trebol.security.authorities.AuthorityCodes` — the generated, versioned catalogue —
instead of a duplicated string literal. The **string values** remain contract and are
unchanged (G2); only the reference form changed.

---

## 1. The five authorities

| Constant | String value | Enforced | Where |
|---|---|---|---|
| `PRODUCT_LISTS_CREATE` | `product_lists:create` | ✅ yes | `DataProductListsController.create` |
| `PRODUCT_LISTS_UPDATE` | `product_lists:update` | ✅ yes | `DataProductListsController.update` **and** `partialUpdate` |
| `PRODUCT_LISTS_DELETE` | `product_lists:delete` | ✅ yes | `DataProductListsController.delete` |
| `PRODUCT_LISTS_CONTENTS` | `product_lists:contents` | ✅ yes | `DataProductListContentsController.addToContents`, `updateContents` **and** `deleteFromContents` |
| `PRODUCT_LISTS_READ` | `product_lists:read` | ❌ **no** | **referenced by nothing** |

All checks use the method-level form
`@PreAuthorize("hasAuthority('" + CONSTANT + "')")`.

**`product_lists:contents` is the only authority in the entire catalogue that is not one
of the conventional create/read/update/delete four** (BR-PRODLIST-028 covers the
conventional three; BR-PRODLIST-027 covers this one).

---

## 2. What each grants

| Authority | Grants |
|---|---|
| `product_lists:create` | `POST /data/product_lists` — create a list. A duplicate **name** is a clean 409; a duplicate **code** is a 500. |
| `product_lists:update` | `PUT` (full replacement — wipes an omitted `name` and **silently resets `disabled` to `false`**) and `PATCH` (`name` and `code` only). One authority for both, so there is **no way to grant renaming without granting the destructive replacement**. |
| `product_lists:delete` | `DELETE /data/product_lists` with query-parameter addressing. Deletes the list **and all its membership rows** (the CRUD override deletes children first). |
| `product_lists:contents` | **All three contents writes**: add one product, replace the entire contents, and delete contents. See §3. |
| `product_lists:read` | **Nothing.** Seeded in the database and present in the catalogue, but no code path consults it. |

---

## 3. One authority, three very different blast radii — BR-PRODLIST-027

`product_lists:contents` is checked identically on all three write endpoints, but they
are not comparable operations:

| Endpoint | Effect |
|---|---|
| `POST /data/product_list_contents` | Adds **one** product to a list. Idempotent in practice, reversible. |
| `PUT /data/product_list_contents` | **Deletes the entire contents and commits that delete**, then re-inserts from the body. An empty body, or a body whose every product is unresolvable, **empties the list and returns 204**. A failure mid-loop leaves partial state with no rollback. |
| `DELETE /data/product_list_contents` | With only `listCode`, **clears the whole list** — and because this controller extends nothing, the Phase-0 D1 guard does not apply, so a **mistyped** product filter also clears the whole list. |

**Consequence: there is no way to grant "may add a product to a featured list" without
also granting "may empty any list".** A principal with this one authority can wipe every
curated collection in the storefront, and two of the three paths do so while returning a
success status.

This is **preserved behaviour** for Phase 1. Splitting the authority would be a
catalogue change and a behavioural change, and the authority-catalogue integrity test
fails the build on drift. It is recorded here so the gap is explicit in the extracted
service rather than inherited silently.

---

## 4. The unenforced read authority — BR-PRODLIST-026

**Both read routes carry no `@PreAuthorize` annotation** (BR-PRODLIST-025):

- `GET /data/product_lists` — every list, including disabled ones, with item counts.
- `GET /data/product_list_contents` — the full contents of any list, addressed by
  `listCode`, **including the contents of a disabled list** (the flag is never
  consulted).

So the curation structure of the storefront is **world-readable** by unauthenticated
callers, and the `disabled` flag provides **no concealment whatsoever** — it is not a
visibility control, it is an inert column (BR-PRODLIST-009).

This is not an annotation oversight alone. Per ADR-002 (Gate D), `SecurityConfig`
declares **no request-matcher rules**: enforcement is entirely method-level, so **any
endpoint without an authority annotation is publicly reachable**. Granting
`product_lists:read` to a role changes nothing, and revoking it from every role also
changes nothing.

**Porting ruling:** preserve exactly. The storefront depends on anonymous list and
contents reads, so adding the missing checks would break clients. The catalogue service
keeps method-level enforcement and keeps both endpoints unannotated. The authority code
stays in the catalogue — removing it would be a catalogue change, and the integrity test
fails the build on drift.

---

## 5. Granularity gaps

| Gap | Consequence |
|---|---|
| One authority for all three contents writes | §3 — the central gap. Add, replace and clear are indistinguishable at the authorization layer. |
| `PATCH` and `PUT` share one list authority | A role that may rename a list may also replace it and reset its `disabled` flag. |
| `disabled` is unsettable through the API but clearable by any `PUT` | There is no authority governing list visibility, because there is no working visibility mechanism to govern. |
| No per-list scoping | Authorities are global. There is no "may curate the Featured list only" — the single most plausible real requirement for this domain. |
| Reads are unauthenticated | Curation structure, item counts and the contents of disabled lists are all public. |
| Contents writes do not require any product authority | Membership references products but needs no `products:read`; it only needs `product_lists:contents`. |
| The contents controller enforces nothing at class level | There is no `@PreAuthorize` on the class, only on three of the four methods — the read method is deliberately open, but the pattern means a newly added method would default to **public** (ADR-002). |

---

## 6. Enforcement mechanics that port with the code

| Mechanic | Detail |
|---|---|
| Model | Method-level `@PreAuthorize` only. No URL-pattern rules anywhere (ADR-002). |
| Token verification | The catalogue service verifies the shared-signing-key JWT **locally**; it issues no tokens and owns no user tables. Login and guest filters are **not** ported into it. |
| Authority source | Authorities come from the verified token's claims; the catalogue service performs **no database lookup** of roles or permissions — those tables belong to Identity. |
| Catalogue artefact | `AuthorityCodes` moves to the shared-contracts module and is consumed by both the monolith and the catalogue service, which is what makes exit criterion X6 genuinely true rather than nominally true. |
| Drift protection | `AuthorityCatalogueIntegrityTest` regenerates the catalogue from `00-seed-data.json` and fails the build on drift or an unknown code. `NoAuthorityLiteralsArchitectureTest` prevents raw literals reappearing, and is applied to the catalogue-service sources as well. |

---

## 7. Seeded assignment

All five codes are seeded in
`business-rules-extraction/database-documentation/00-seed-data.json` among the 54
authority codes, and are assigned to roles through `app_user_role_permissions` — a table
owned by **Identity**, not by this domain. The catalogue service therefore has **no write
path and no read path** for authority assignment; it only ever evaluates the claims
presented in a verified token.
