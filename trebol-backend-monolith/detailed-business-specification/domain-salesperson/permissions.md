# Salesperson — Permissions

Authorisation is **entirely method-level**, driven by `@PreAuthorize`. `SecurityConfig`
declares **no request-matcher rules at all** — the Gate D finding recorded in `ADR-002`:
any endpoint without an authority annotation is publicly reachable, so a gateway cannot
derive authorization from paths.

---

## 1. The four salesperson authorities

All four resolve through `AuthorityCodes` in the **shared-contracts module**
(`org.trebol.security.authorities.AuthorityCodes`), never as string literals. Gate B
requires zero `hasAuthority('...')` literals anywhere; exit criterion X6 pins it.

| Constant | Value (verbatim) | Guards |
|---|---|---|
| `SALESPEOPLE_READ` | `salespeople:read` | `GET /data/salespeople` |
| `SALESPEOPLE_CREATE` | `salespeople:create` | `POST /data/salespeople` |
| `SALESPEOPLE_UPDATE` | `salespeople:update` | `PUT /data/salespeople` |
| `SALESPEOPLE_DELETE` | `salespeople:delete` | `DELETE /data/salespeople` |

The four values are rows in `app_permissions`, seeded by `data.sql` and generated into
`AuthorityCodes` from `business-rules-extraction/database-documentation/00-seed-data.json`.
`AuthorityCatalogueIntegrityTest` and `AuthorityCatalogueGenerator` **stay in the
monolith** — they live with the seed JSON that is their source of truth. Identity carries
`NoAuthorityLiteralsArchitectureTest` with its own count floor instead.

BR-SALESPERSON-021: salesperson administration requires explicit authorities; there is no
implicit grant and no role shortcut.

---

## 2. Two-layer enforcement

```java
@RequestMapping("/data/salespeople")
@PreAuthorize("isAuthenticated()")        // class level — layer 1
public class DataSalespeopleController ... {

    @GetMapping
    @PreAuthorize("hasAuthority('" + SALESPEOPLE_READ + "')")   // method level — layer 2
    public DataPagePojo<PersonPojo> readMany(...) { ... }
}
```

1. **Class level — `isAuthenticated()`.** An anonymous request is rejected with **401**
   before any authority is considered.
2. **Method level — `hasAuthority(...)`.** An authenticated principal lacking the specific
   authority is rejected with **403**.

Both layers port verbatim. Dropping the class-level annotation would not appear to change
anything — the method checks would still fire — but it would turn anonymous requests from
401 into 403 and remove the safety net that keeps an un-annotated method on this class
non-public.

---

## 3. How a principal acquires these authorities

Authorities are **not** roles. The chain, assembled at login by `UserDetailsService`:

```
app_users → app_user_roles → app_user_role_permissions → app_permissions
```

A user has exactly one role; the role is granted a set of permissions; the permission
**codes** become the principal's granted authorities, and they are what `hasAuthority`
matches. Nothing checks the role name itself.

Two properties matter here:

- **In-memory de-duplication at login.** Before the Phase 0 **D17** fix,
  `app_user_role_permissions` had no unique constraint on `(user_role_id, permission_id)`,
  so the same authority could be granted twice; the duplicate was removed in memory. D17
  added `UQ_UROLEPERMS_UROLE_PERM`, and **the in-memory de-duplication was deliberately
  left in place**. Both port.
- **No account-status flags are honoured.** Register item **D15** — the `UserDetails`
  account-status flags are hardcoded permissive and there is no password change or reset
  path. Ruled PRESERVE, out of scope, flagged as the first post-conversion Identity
  backlog item.

Which roles hold the four salesperson authorities is seeded data, specified in
`domain-user/permissions.md`; this file does not duplicate the grant matrix.

---

## 4. A salesperson is not an account — and the authorities do not say otherwise

**BR-SALESPERSON-020 is the rule most easily broken by a well-meaning refactor.**

| Expectation | Reality |
|---|---|
| "A salesperson logs in to record their sales" | No. There is **no relationship between `salespeople` and `app_users`** — no FK, no join, no shared column other than both pointing at `people` |
| "`salespeople:*` authorities are what a salesperson holds" | No. They are what an **administrator** holds in order to *manage* salesperson records. Nothing grants a salesperson any authority, because a salesperson has no principal |
| "Creating a salesperson creates a login" | No. `POST /data/salespeople` writes `people` and `salespeople` only. Account creation is `POST /public/register` or `POST /data/users`, both in the user domain |
| "An order records which user sold it" | No. `Order.salesperson` points at `salespeople`, and **no route ever sets it** (BR-SALESPERSON-015), so it is always null in practice |

Both tables live in the Identity service, so nothing prevents someone from "joining them
up" during the port. That would be new product behaviour, not conversion work, and it is
explicitly out of scope.

---

## 5. The unguarded path into salesperson data

Unlike `customers`, which has three write paths (two of them anonymous), `salespeople` has
exactly **two** writers, and only one is a route:

| Path | Authorisation | Note |
|---|---|---|
| `POST`/`PUT`/`DELETE /data/salespeople` | the four authorities above, plus `isAuthenticated()` | the only route that writes this table |
| checkout → `Order.salesperson` **cascade** | inherits whatever guarded the checkout call | **BR-SALESPERSON-016** — an order carrying salesperson data can create or modify the row through the cascade, with **no `salespeople:create` or `salespeople:update` check whatsoever** |

**The second row is the security-relevant finding.** It is a write path into an
administratively-guarded table that bypasses every one of that table's authorities, and it
is reachable from a **public checkout**. It is harmless today only because
BR-SALESPERSON-015 holds — no route populates an order's salesperson, so the cascade is
never exercised.

**It must not be "hardened" during Phase 2** (G1), and it must not be forgotten in
Phase 3: cutting `Order.salesperson` is what actually closes it. Recorded in `00` §3.3 as
the one genuine distributed-write risk this domain hands forward.

There is **no anonymous read** of salesperson data. `GET /data/salespeople` requires both
layers, and nothing in `/public/*` exposes a salesperson — which is a meaningful contrast
with `customer`, reachable anonymously through both guest checkout and self-registration.

---

## 6. Porting checklist

| Item | Requirement |
|---|---|
| Authority source | `AuthorityCodes` consumed from `trebol-shared-contracts`; **zero** `hasAuthority('...')` literals in identity-service source (Gate B, X6) |
| Annotations | Both layers — class-level `isAuthenticated()` **and** all four method-level `hasAuthority(...)` — ported verbatim |
| Unguarded cascade | the `Order.salesperson` write path stays unguarded in Phase 2 and is closed by the Phase 3 FK cut, not by adding a check now |
| Account separation | no relationship between `salespeople` and `app_users` is introduced (BR-SALESPERSON-020) |
| Pinning test | `IdentityRouteParityTest` asserts the exact authority **value** per method and the class-level `isAuthenticated()` |
| Literals | `salespeople:read`, `salespeople:create`, `salespeople:update`, `salespeople:delete` — unchanged (G2) |
