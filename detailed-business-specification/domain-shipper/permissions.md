# Shipper — Permissions

Authorisation is **entirely method-level**, driven by `@PreAuthorize`. `SecurityConfig`
declares **no request-matcher rules at all** — the Gate D finding recorded in `ADR-002` —
so any endpoint without an annotation is publicly reachable, and there is no second layer to
fall back on.

---

## 1. The four shipper authorities, and the one that does nothing

All four resolve through `AuthorityCodes` in the **shared-contracts module**, never as string
literals. Gate B requires zero `hasAuthority('...')` literals anywhere; exit criterion **X6**
pins it.

| Constant | Value (verbatim) | Guards | Enforced? |
|---|---|---|---|
| `SHIPPERS_CREATE` | `shippers:create` | `POST /data/shippers` | **yes** |
| `SHIPPERS_UPDATE` | `shippers:update` | `PUT` **and** `PATCH /data/shippers` | **yes** |
| `SHIPPERS_DELETE` | `shippers:delete` | `DELETE /data/shippers` | **yes** |
| `SHIPPERS_READ` | `shippers:read` | — | **NO — never referenced by any annotation** |

**`shippers:read` is seeded and dead.** It exists as a row in `app_permissions` (ids 46–49
cover the four shipper codes), it is granted to roles, it is present in the generated
catalogue of 54 codes — and **no `@PreAuthorize` anywhere in the codebase mentions it**.

That is **BR-SHIPPER-009**, and it is a concrete instance of the broader **BR-USER-037**
("several seeded read authorities are never enforced"). The authority is not a mistake to be
corrected during the port: granting it changes nothing today, and *enforcing* it would make
the carrier list private, which is a client-visible change (§2).

**`shippers:update` guards two verbs.** `PUT` and `PATCH` share one authority, so a principal
who may replace a carrier may also patch one. There is no finer distinction, and introducing
one would be new behaviour.

---

## 2. The read is deliberately anonymous

```java
@RestController
@RequestMapping("/data/shippers")
@Tag(name = "Shippers management")
// NO class-level @PreAuthorize
public class DataShippersController extends DataCrudGenericController<ShipperPojo, Shipper> {

    @GetMapping
    // NO method-level @PreAuthorize either
    public DataPagePojo<ShipperPojo> readMany(@RequestParam Map<String,String> allRequestParams) { ... }
```

**`GET /data/shippers` is reachable by anyone, unauthenticated.** That is **BR-SHIPPER-008**,
and the storefront depends on it: a customer must be able to see the available carriers while
choosing a delivery option, **before** authenticating — guest checkout exists precisely so an
unauthenticated customer can complete a purchase.

The absence of a class-level guard is therefore load-bearing. Adding one would not merely
change a status code; it would break anonymous checkout, and it would break it silently from
the server's point of view.

**`OrdersRouteParityTest` must assert that this route demands no authority**, exactly as
`CatalogueRouteParityTest` asserts for the five anonymous storefront reads and
`IdentityRouteParityTest` asserts for `/public/register`. An accidental addition must fail the
build.

---

## 3. The enforcement model, route by route

| Route | Verb | Authority | Anonymous? |
|---|---|---|---|
| `/data/shippers` | GET | **none** | **yes, by design** |
| `/data/shippers` | POST | `shippers:create` | no |
| `/data/shippers` | PUT | `shippers:update` | no |
| `/data/shippers` | PATCH | `shippers:update` | no |
| `/data/shippers` | DELETE | `shippers:delete` | no |

**BR-SHIPPER-010** — write operations require explicit authorities. There is no implicit
grant and no role shortcut; nothing checks a role *name* anywhere in this system.

Note the shape: **one anonymous read and four guarded writes, with no class-level
`isAuthenticated()`**. Contrast the identity domain's data controllers, which carry *both*
layers — a class-level `isAuthenticated()` **and** per-method authorities. This domain has
only the per-method layer, so there is no safety net: a future method added here **without**
an annotation would be public.

That is worth stating plainly because it is the Gate D consequence in miniature:

> On this controller, an un-annotated write method would be reachable by anyone. Nothing
> else would stop it.

---

## 4. How a principal acquires these authorities

Authorities are **not** roles. The chain, assembled at login by the Identity service:

```
app_users → app_user_roles → app_user_role_permissions → app_permissions
```

A user has exactly one role; the role is granted a set of permissions; the permission
**codes** become granted authorities, and those are what `hasAuthority` matches.

Two consequences specific to this domain:

- **The grant matrix lives in the Identity service's `data.sql`**, not here. `shippers` as a
  table is not seeded at all; the four `shippers:*` permission rows are. After Phase 2 those
  rows belong to Identity, and this domain merely consumes the codes through the published
  `AuthorityCodes` artefact.
- **Authorities travel inside the JWT and are never re-checked against the database**
  (BR-USER-022). The Orders service verifies tokens locally against the shared signing key
  per `ADR-002`; it never calls Identity to authorise a carrier write. So a revoked
  `shippers:delete` grant stays effective until the token expires.

---

## 5. Porting checklist

| Item | Requirement |
|---|---|
| Authority source | `AuthorityCodes` consumed from `trebol-shared-contracts`; **zero** `hasAuthority('...')` literals in orders-service source (Gate B, X6) |
| Annotations | the three enforced authorities ported verbatim on their exact verbs — including `shippers:update` guarding **both** `PUT` and `PATCH` |
| The dead authority | **`shippers:read` stays unenforced** (BR-SHIPPER-009). Do not "complete" the set by adding a check — that would make the carrier list private and break anonymous checkout |
| The anonymous read | `GET /data/shippers` keeps **no** `@PreAuthorize`, and the controller keeps **no** class-level guard (BR-SHIPPER-008) |
| Pinning test | `OrdersRouteParityTest` asserts the route prefix, the exact authority **value** per write verb, and that `GET` demands none |
| Literals | `shippers:create`, `shippers:update`, `shippers:delete`, `shippers:read` — all four unchanged, including the unenforced one, because the catalogue is generated from the seed data and `CATALOGUE_SIZE 54` is asserted (G2, X6) |
