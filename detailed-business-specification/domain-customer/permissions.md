# Customer — Permissions

Authorisation is **entirely method-level**, driven by `@PreAuthorize`. `SecurityConfig`
declares **no request-matcher rules at all**, which is the finding Gate D recorded in
`ADR-002`: any endpoint without an authority annotation is publicly reachable, so a
gateway cannot derive authorization from paths.

---

## 1. The four customer authorities

All four resolve through `AuthorityCodes` in the **shared-contracts module**
(`org.trebol.security.authorities.AuthorityCodes`), never as string literals. Gate B
requires zero `hasAuthority('...')` literals anywhere, and exit criterion X6 pins it.

| Constant | Value (verbatim) | Guards | Seeded permission id |
|---|---|---|---|
| `CUSTOMERS_READ` | `customers:read` | `GET /data/customers` | — |
| `CUSTOMERS_CREATE` | `customers:create` | `POST /data/customers` | — |
| `CUSTOMERS_UPDATE` | `customers:update` | `PUT /data/customers` | — |
| `CUSTOMERS_DELETE` | `customers:delete` | `DELETE /data/customers` | — |

The four values are rows in `app_permissions`, seeded by `data.sql` and generated into
`AuthorityCodes` from `business-rules-extraction/database-documentation/00-seed-data.json`.
`AuthorityCatalogueIntegrityTest` and `AuthorityCatalogueGenerator` **stay in the
monolith** — they live with the seed JSON that is their source of truth. Identity carries
`NoAuthorityLiteralsArchitectureTest` with its own count floor instead.

**The authority strings are client-observable contract** (G2). Renaming one silently
disables an access check, which is the entire reason Gate B exists.

---

## 2. Two-layer enforcement

Every request to `/data/customers` passes **two** checks:

```java
@RequestMapping("/data/customers")
@PreAuthorize("isAuthenticated()")        // class level — layer 1
public class DataCustomersController ... {

    @GetMapping
    @PreAuthorize("hasAuthority('" + CUSTOMERS_READ + "')")   // method level — layer 2
    public DataPagePojo<PersonPojo> readMany(...) { ... }
}
```

1. **Class level — `isAuthenticated()`.** An anonymous request is rejected with **401**
   before any authority is considered.
2. **Method level — `hasAuthority(...)`.** An authenticated principal without the
   specific authority is rejected with **403**.

Both layers port verbatim. Dropping the class-level annotation would not *appear* to
change anything — the method-level checks would still fire — but it would change the
status code for anonymous requests from 401 to 403 and, more importantly, remove the
safety net that makes an un-annotated method on this class still non-public.

BR-CUSTOMER-020: customer administration requires explicit authorities; there is no
implicit grant and no role shortcut.

---

## 3. How a principal acquires these authorities

Authorities are **not** roles. The chain, assembled at login by `UserDetailsService`:

```
app_users → app_user_roles → app_user_role_permissions → app_permissions
```

A user has exactly one role; the role is granted a set of permissions; the permission
**codes** become the principal's granted authorities, and they are what `hasAuthority`
matches. Nothing checks the role name itself.

Two properties of that assembly matter here:

- **In-memory de-duplication at login.** Before the Phase 0 D17 fix,
  `app_user_role_permissions` had no unique constraint on `(user_role_id, permission_id)`,
  so the same authority could be granted twice; the duplicate was removed in memory. D17
  added `UQ_UROLEPERMS_UROLE_PERM`, and **the in-memory de-duplication was deliberately
  left in place** — removing it is an unnecessary behavioural risk. Both port.
- **No account-status flags are honoured.** Register item **D15** records that the
  `UserDetails` account-status flags are hardcoded permissive and there is no password
  change or reset path. Ruled PRESERVE, out of scope, and flagged as the first
  post-conversion Identity backlog item. It ports unchanged.

Which roles actually hold the four customer authorities is seeded data, specified in
`domain-user/permissions.md` — this file does not duplicate the grant matrix.

---

## 4. The unguarded paths into customer data

Three paths write or read `customers` **without** any `customers:*` authority. All three
are intentional and all three port:

| Path | Authorisation | Why |
|---|---|---|
| `POST /public/guest` → `GuestCustomerProvisioningService` | **none — fully anonymous** | guest checkout must work without an account (BR-CUSTOMER-017). The write runs inside an authentication filter, after authentication succeeds (D14) |
| `POST /public/register` → `RegistrationServiceImpl` | **none — fully anonymous** | self-registration creates the `people`, `app_users` **and** `customers` rows in one transaction after Phase 2 |
| checkout → `CustomerProvisioningContract.resolveOrCreateCustomer` | inherits whatever guarded the checkout call | the order domain's resolve-or-create, keyed by `idNumber` (BR-CUSTOMER-019) |

**This is the security-relevant consequence of the Gate D finding.** Two of the three
write paths into `customers` carry no authority check at all, because they are reached
through filters and public routes rather than through `DataCustomersController`. A
reviewer auditing only the `@PreAuthorize` annotations would conclude the table is fully
guarded. It is not — and that must not be "hardened" during the port, because anonymous
guest checkout and anonymous self-registration are both working, tested, client-facing
features (G1).

---

## 5. Porting checklist

| Item | Requirement |
|---|---|
| Authority source | `AuthorityCodes` consumed from `trebol-shared-contracts`; **zero** `hasAuthority('...')` literals in identity-service source (Gate B, X6) |
| Annotations | Both layers — class-level `isAuthenticated()` **and** all four method-level `hasAuthority(...)` — ported verbatim |
| Anonymous paths | `/public/guest` and `/public/register` stay unguarded; `/public/guest` stays a **filter**, as the definition requires |
| Pinning test | `IdentityRouteParityTest` asserts the exact authority **value** per method, the class-level `isAuthenticated()`, and that `/public/register` is unsecured |
| Literals | `customers:read`, `customers:create`, `customers:update`, `customers:delete` — unchanged (G2) |
