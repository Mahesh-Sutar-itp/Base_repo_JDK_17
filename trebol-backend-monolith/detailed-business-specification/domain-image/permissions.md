# Image Registry — Permissions

The authentication mechanism, how authorities are granted to roles, and the system-wide
authority catalog are in `cross-cutting-documentation/authentication-authorization.md` and
`cross-cutting-documentation/common-permissions.md`. This document covers only the
authorities governing this domain's resources.

---

## 1. Domain-owned authorities

| Authority (literal string) | Resource | Operation | Enforcement point |
|---|---|---|---|
| `images:read` | `images` | list / search | method-level rule on `DataImagesController.readMany` |
| `images:create` | `images` | create | method-level rule on `DataImagesController.create` |
| `images:update` | `images` | **replace and patch** | method-level rules on `DataImagesController.update` **and** `partialUpdate` |
| `images:delete` | `images` | delete | method-level rule on `DataImagesController.delete` |

Literal values are taken from the authority declarations on the handlers, not from
descriptions or documentation annotations — these are the exact strings checked at runtime.

### 1.1 Class-level requirement

`DataImagesController` carries a class-level rule requiring the caller to be
**authenticated**, applying to all five handlers. Every route is therefore protected by
both authentication and a specific authority.

This domain is **fully protected**. Worth contrasting explicitly with its batch siblings:

| Domain | List route protection |
|---|---|
| `image` | authenticated **and** `images:read` |
| `shipper` | **neither** — no class-level authentication rule, no authority on the list route |
| `company-profile` | **neither** — a public route by design |

A rewrite unifying the authority model must not assume the `image` pattern holds elsewhere.

### 1.2 Replace and patch share one authority

There is no separate replace-versus-patch privilege. A principal holding `images:update`
can perform both the destructive full replacement (BR-IMAGE-015) and the guarded partial
patch. If a rewrite wants to restrict the destructive form, the authority must be split —
it is not split today.

---

## 2. What each authority actually grants

| Authority | Scope granted |
|---|---|
| `images:read` | Page through the **entire** registry. No row scoping, no field redaction. Full case-insensitive substring search over `code` and `filename`. |
| `images:create` | Insert arbitrary image metadata, including any URL string up to 500 characters with no scheme or host allow-listing (BR-IMAGE-005). |
| `images:update` | Change any field on any addressable image, by replacement or patch. |
| `images:delete` | **Delete without bound** (BR-IMAGE-013) — including, via a mistyped selector, the entire registry. |

### 2.1 Two security observations

**(a) `images:create` and `images:update` permit arbitrary URL content.** Nothing validates
the scheme, host or syntax of a stored URL, and these URLs are served to clients and
presumably rendered. The integrity of rendered image sources rests entirely on the
trustworthiness of authority holders. A rewrite should consider host allow-listing; see
`reference/integrations.md`.

**(b) `images:delete` does not require `images:read`.** The authorities are independent, so
a principal can destroy records it cannot inspect — and because deletion is unbounded and a
typo matches everything, it can do so at registry scale without ever having listed the
contents. Granting `images:delete` without `images:read` is not a meaningful restriction
today.

---

## 3. The authority model does not cover the domain's internal write path

**This is the most important finding in this document.**

Image records are created by the `product` domain as a side effect of product writes
(BR-IMAGE-024). That path calls this domain's `getExisting` and then **constructs and
cascade-persists `Image` rows itself**, bypassing `create` entirely.

| Write path | Authority actually required |
|---|---|
| `POST /data/images` | `images:create` |
| Product create/update carrying new images | **the `product` domain's authorities only** — `images:create` is *not* checked |

So a principal authorised to manage products can create image records without holding any
`images:*` authority. The domain's authority model governs its HTTP surface but not its only
internal write path.

A rewrite must decide deliberately: either the extracted image service requires an
authority for all writes — in which case the product flow needs a propagated identity or a
service credential — or product-driven image creation remains implicitly authorised by
product permissions. Today the choice is made by accident of in-process access.

---

## 4. Domain-scoped roles

**None.** This domain defines no role of its own. The four authorities are granted to roles
defined in the system-wide catalog; see
`cross-cutting-documentation/common-permissions.md` for which roles hold them.

---

## 5. Extraction considerations

| # | Consideration |
|---|---|
| 1 | **Make `DELETE` safe before exposing it** (BR-IMAGE-013). Requiring at least one *recognised* selector is a one-line change with very high value. Across a service boundary the blast radius is identical but the caller is further away and harder to audit. |
| 2 | **Decide who may create images.** The product bypass (§3) must become an explicit API call. Either propagate the end-user identity so `images:create` can be enforced, or grant the product service a credential and accept that product permissions imply image creation. |
| 3 | **Consider splitting `images:update`.** Replacement is destructive and patch is not, yet they share one authority. |
| 4 | **Consider URL allow-listing at the write boundary.** A service API is the natural enforcement point for scheme and host restrictions that do not exist today. |
| 5 | **Pair `images:delete` with `images:read`,** or scope deletion, so that destructive capability implies at least the ability to inspect what is being destroyed. |
