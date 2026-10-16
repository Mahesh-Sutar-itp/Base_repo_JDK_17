# Permissions — Product Catalogue

Four authority codes exist for `product`. **Three are enforced; one is not.** The
`company-profile` appendix has **no authority at all**.

Since Phase 0 (Gate B) every check references a constant on
`org.trebol.security.authorities.AuthorityCodes` — the generated, versioned catalogue —
instead of a duplicated string literal. The **string values** remain contract and are
unchanged (G2); only the reference form changed.

---

## 1. The four product authorities

| Constant | String value | Enforced | Where |
|---|---|---|---|
| `PRODUCTS_CREATE` | `products:create` | ✅ yes | `DataProductsController.create` |
| `PRODUCTS_UPDATE` | `products:update` | ✅ yes | `DataProductsController.update` **and** `partialUpdate` |
| `PRODUCTS_DELETE` | `products:delete` | ✅ yes | `DataProductsController.delete` |
| `PRODUCTS_READ` | `products:read` | ❌ **no** | **referenced by nothing** |

All three enforced checks use the method-level form
`@PreAuthorize("hasAuthority('" + CONSTANT + "')")`.

---

## 2. What each grants

| Authority | Grants |
|---|---|
| `products:create` | `POST /data/products` — create a product, including with a negative price or negative stock, including silently uncategorised when the named category does not exist, and **including creating `images` rows through the bypassing write path** (§3). |
| `products:update` | `PUT` (full replacement — wipes every omitted field **and deletes every image link**) and `PATCH` (the six scalar fields; category and images are untouchable). One authority covers both, so there is **no way to grant patching without granting the destructive replacement**. |
| `products:delete` | `DELETE /data/products` with query-parameter addressing. A product with image links cannot actually be deleted — the FK blocks it (E16) — so in practice this authority only deletes image-less products. |
| `products:read` | **Nothing.** Seeded in the database and present in the catalogue, but no code path consults it. |

---

## 3. The authorization hole in the image write path

`makeProductImageRelationships` creates `images` rows as a side effect of a product
write, by building a transient `Image` inline instead of calling
`ImagesCrudService.create`.

**Consequence: `images:create` is never checked on this path.** A principal holding only
`products:create` or `products:update` can insert arbitrary rows into the `images`
table — including arbitrary `url` values, which are never validated or allow-listed and
are served to clients and rendered.

This is the authorization half of BR-PRODUCT-014 and of the `image` domain's own W3
finding. It is **preserved behaviour** for Phase 1: fixing it would change the observable
outcome of a product write and requires a register ruling, which it does not have. It is
recorded here so the gap is explicit in the extracted service rather than inherited
silently.

The Phase-0 **D7** fix narrowed it slightly — a payload reusing an existing `code` or
`url` now resolves to the existing image instead of inserting a colliding row — but the
create-by-side-effect path itself is untouched.

---

## 4. The unenforced read authority — BR-PRODUCT-026

`GET /data/products` carries **no `@PreAuthorize` annotation**, so the entire catalogue is
**world-readable** (BR-PRODUCT-025), including by unauthenticated callers, and including
`description`, `price`, `currentStock` and `criticalStock` — i.e. **stock levels are
public**.

This is not an annotation oversight alone. Per ADR-002 (Gate D), `SecurityConfig`
declares **no request-matcher rules**: enforcement is entirely method-level, so **any
endpoint without an authority annotation is publicly reachable**. Granting
`products:read` to a role changes nothing, and revoking it from every role also changes
nothing.

**Porting ruling:** preserve exactly. The storefront depends on anonymous product reads,
so adding the missing check would break clients. The catalogue service keeps method-level
enforcement and keeps this endpoint unannotated. The authority code stays in the
catalogue — removing it would be a catalogue change, and the integrity test fails the
build on drift.

---

## 5. `company-profile` appendix — no authority exists

`GET /public/about` has **no `@PreAuthorize`, no parameters and no declared exceptions**:
fully anonymous (BR-COMPANY-007). There is **no authority code for the company profile at
all** — not enforced, not seeded, not defined — because there is **no write path** to
protect (BR-COMPANY-008).

The `app_params` table is read through exactly one query, filtered to the `company`
category (BR-COMPANY-002), and is **not an extension point for other domains**
(BR-COMPANY-013). A rewrite that generalises it into a settings API would need an
authority model that does not currently exist.

---

## 6. Granularity gaps

| Gap | Consequence |
|---|---|
| `PATCH` and `PUT` share one authority | A role that may correct a typo in a product name may also replace every field and **delete every image link** (BR-PRODUCT-011). Very different blast radii, indistinguishable at the authorization layer. |
| Product writes imply image writes | `images:create` is bypassed entirely (§3). |
| No authority distinguishes price changes from descriptive edits | Repricing is the commercially sensitive operation and is indistinguishable from editing a description. |
| No authority covers stock | Stock is writable through `products:update` and is never acted upon by any process anyway (BR-PRODUCT-021). |
| Reads are unauthenticated | Prices, descriptions and **stock levels** are public. There is no draft/published flag to hide anything behind. |
| No per-category or per-product scoping | Authorities are global. There is no "may edit the Electronics catalogue only". |

---

## 7. Enforcement mechanics that port with the code

| Mechanic | Detail |
|---|---|
| Model | Method-level `@PreAuthorize` only. No URL-pattern rules anywhere (ADR-002). |
| Token verification | The catalogue service verifies the shared-signing-key JWT **locally**; it issues no tokens and owns no user tables. Login and guest filters are **not** ported into it. |
| Authority source | Authorities come from the verified token's claims; the catalogue service performs **no database lookup** of roles or permissions — those tables belong to Identity. |
| Catalogue artefact | `AuthorityCodes` moves to the shared-contracts module and is consumed by both the monolith and the catalogue service, which is what makes exit criterion X6 genuinely true rather than nominally true. |
| Drift protection | `AuthorityCatalogueIntegrityTest` regenerates the catalogue from `00-seed-data.json` and fails the build on drift or an unknown code. `NoAuthorityLiteralsArchitectureTest` prevents raw literals reappearing, and is applied to the catalogue-service sources as well. |

---

## 8. Seeded assignment

`products:create`, `products:update`, `products:delete` and `products:read` are seeded in
`business-rules-extraction/database-documentation/00-seed-data.json` among the 54
authority codes, and are assigned to roles through `app_user_role_permissions` — a table
owned by **Identity**, not by this domain. The catalogue service therefore has **no write
path and no read path** for authority assignment; it only ever evaluates the claims
presented in a verified token.
