# Permissions — Product Category Tree

Four authority codes exist for this domain. **Three are enforced; one is not.**

Since Phase 0 (Gate B) every check references a constant on
`org.trebol.security.authorities.AuthorityCodes` — the generated, versioned catalogue —
instead of a duplicated string literal. The **string values** remain contract and are
unchanged (G2); only the reference form changed.

---

## 1. The four authorities

| Constant | String value | Enforced | Where |
|---|---|---|---|
| `PRODUCT_CATEGORIES_CREATE` | `product_categories:create` | ✅ yes | `DataProductCategoriesController.create` |
| `PRODUCT_CATEGORIES_UPDATE` | `product_categories:update` | ✅ yes | `DataProductCategoriesController.update` **and** `partialUpdate` |
| `PRODUCT_CATEGORIES_DELETE` | `product_categories:delete` | ✅ yes | `DataProductCategoriesController.delete` |
| `PRODUCT_CATEGORIES_READ` | `product_categories:read` | ❌ **no** | **referenced by nothing** |

All three enforced checks use the method-level form
`@PreAuthorize("hasAuthority('" + CONSTANT + "')")`.

---

## 2. What each grants

| Authority | Grants |
|---|---|
| `product_categories:create` | `POST /data/product_categories` — create a category, including silently creating it as a root when the named parent does not exist, and including creating a self-parent cycle (BR-PRODCAT-009). |
| `product_categories:update` | `PUT` (full replacement — **the only re-parenting path**, and the only path that can write a cycle) and `PATCH` (code and name only; parent changes are impossible). One authority covers both, so there is **no way to grant patching without granting re-parenting**. |
| `product_categories:delete` | `DELETE /data/product_categories` with query-parameter addressing. Deletion of a category with children or products is blocked by the database FK (E13), so this authority cannot orphan the tree. |
| `product_categories:read` | **Nothing.** It is seeded in the database and present in the catalogue, but no code path consults it. |

---

## 3. The unenforced read authority — BR-PRODCAT-015

`GET /data/product_categories` carries **no `@PreAuthorize` annotation at all**, so the
whole category tree is **world-readable** (BR-PRODCAT-014), including by unauthenticated
callers.

This is not an oversight in the annotation alone. Per ADR-002 (Gate D), `SecurityConfig`
declares **no request-matcher rules**: enforcement is entirely method-level, so **any
endpoint without an authority annotation is publicly reachable**. Granting
`product_categories:read` to a role therefore changes nothing, and revoking it from
every role also changes nothing.

**Porting ruling:** preserve exactly. The storefront depends on anonymous category
reads, so adding the missing check would break clients. The catalogue service keeps
method-level enforcement and keeps this endpoint unannotated. The authority code stays
in the catalogue — removing it would be a catalogue change, and the integrity test fails
the build on drift.

---

## 4. Granularity gaps

| Gap | Consequence |
|---|---|
| `PATCH` and `PUT` share one authority | A role that may rename a category may also re-parent it, replace every field, and create a cycle. The two operations have very different blast radii. |
| No authority distinguishes tree-structure changes from attribute changes | Re-parenting is the structurally dangerous operation and is indistinguishable from a rename at the authorization layer. |
| No per-branch or per-category scoping | Authorities are global. There is no notion of "may edit the Electronics branch only". |
| Reads are unauthenticated | The full tree, including internal or unpublished branches, is exposed to anyone. There is no draft/published flag to hide anything behind. |

---

## 5. Enforcement mechanics that port with the code

| Mechanic | Detail |
|---|---|
| Model | Method-level `@PreAuthorize` only. No URL-pattern rules anywhere (ADR-002). |
| Token verification | The catalogue service verifies the shared-signing-key JWT **locally**; it issues no tokens and owns no user tables. Login and guest filters are **not** ported into it. |
| Authority source | Authorities are read from the token's claims; the catalogue service performs **no database lookup** of roles or permissions — those tables belong to Identity. |
| Catalogue artefact | `AuthorityCodes` moves to the shared-contracts module and is consumed by both the monolith and the catalogue service, which is what makes exit criterion X6 genuinely true rather than nominally true. |
| Drift protection | `AuthorityCatalogueIntegrityTest` regenerates the catalogue from `00-seed-data.json` and fails the build on drift or an unknown code. `NoAuthorityLiteralsArchitectureTest` prevents raw literals reappearing, and is applied to the catalogue-service sources as well. |

---

## 6. Seeded assignment

The four codes are seeded in
`business-rules-extraction/database-documentation/00-seed-data.json` among the 54
authority codes, and are assigned to roles through `app_user_role_permissions` —
a table owned by **Identity**, not by this domain. The catalogue service therefore has
**no write path and no read path** for authority assignment; it only ever evaluates the
claims presented in a verified token.
