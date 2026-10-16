# Authentication & Authorization

Owned by the `user` domain; consumed by every other domain. This file is the
cross-cutting summary — the full rule set is in
`../detailed-domain-bre/domain-user/business-rules.md`.

## Authentication

### Mechanism

Stateless bearer tokens, signed with HMAC-SHA. There is no server-side session,
no logout, no revocation list and no refresh.

| Aspect | Detail |
|---|---|
| issuance | `security/GenericJwtAuthenticationFilter.java` (abstract base) |
| password login | `security/JwtLoginAuthenticationFilter.java`, processing URL `/public/login` |
| guest login | `security/JwtGuestAuthenticationFilter.java`, processing URL `/public/guest` |
| verification | `security/JwtTokenVerifierFilter.java` |
| signing key | `config/JwtKeyConfig.java` — derived from the raw bytes of `trebol.security.jwt-secret-key` |
| claims | subject, `authorities`, issued-at, expiration. **No** issuer, audience or token identifier |
| lifetime | the sum of `jwt-expiration-after-days` + `-hours` + `-minutes` (default 30 minutes); all three may be zero |
| token delivery | written **both** to the `Authorization` response header and to the response body (`BR-USER-016`) |
| session policy | stateless; **CSRF protection is disabled** (`BR-USER-024`) |

Both login routes are implemented by **filters, not controllers**, so they do not
appear in any controller file and are invisible to a controller-derived API
inventory. Neither applies bean validation to its request body: each uses a
directly constructed object mapper, so the `LoginPojo` non-blank constraints never
run.

### Filter chain order

```
1. JwtLoginAuthenticationFilter   (/public/login)
2. JwtGuestAuthenticationFilter   (/public/guest, after the login filter)
3. JwtTokenVerifierFilter         (after the guest filter)
```

Declared in `config/SecurityConfig.java`. The configuration also sets
same-origin frame options and the stateless session policy.

### Verification outcomes

| Condition | Outcome |
|---|---|
| no header, or a blank header | the chain continues **anonymously** |
| malformed or untrusted token | unauthorized status, **empty body**, and the **full token value is written to the log** (`BR-USER-020`) |
| expired token | **no error** — the chain continues anonymously, so the caller cannot distinguish "expired" from "never sent" (`BR-USER-019`) |
| valid token | the security context is populated from the subject and the `authorities` claim |

The bearer prefix is removed with a replace-all over the header value rather than
a leading-prefix strip (`BR-USER-021`). A proper header validator exists in the
shared regular-expression adapter and is **not** called.

### Password handling

BCrypt at the configured strength (default 10, minimum enforced 6). Passwords are
set only at account creation, at self-registration, or by full account
replacement — **there is no password-change or reset capability anywhere**
(`BR-USER-045`).

### Account status

All four account-status flags are hardcoded to the permissive value
(`BR-USER-023`). There is no disabled, locked, expired or credentials-expired
state, and no failed-attempt tracking or lockout. Removing access means changing
an account's role or deleting the account.

### Guest access

Disabled by default (`trebol.security.guest-user-enabled=false`). When enabled,
the configured guest name is used as **both** the username and the password
(`BR-USER-026`), and the guest's authorities come from the configured guest role
identifier (default 4, the `Customer` role) rather than from any account row
(`BR-USER-030`). A guest session persists a real person and customer row before
authenticating (`BR-CUSTOMER-017`).

## Authorization

### Model

Single-role accounts with role-granted authorities:

```
app_users (one role) -> app_user_roles -> app_user_role_permissions -> app_permissions
```

Resolved transactionally **at login**, de-duplicated into a set, and embedded in
the token. Authorities are **not** re-resolved per request, so a revoked grant
remains effective until the token expires (`BR-USER-022`).

### Enforcement

Entirely **method-level**. `config/SecurityConfig.java` declares **no**
request-matcher rules at all, which means:

> **Any endpoint whose method carries no authority annotation is publicly
> reachable.** (`BR-USER-025`)

That is not an oversight in a handful of places — it is the design. See
`common-permissions.md` for the full endpoint-to-authority matrix and
`../reference-documentation/api-endpoint-catalog.md` for the complete route list.

### Authority code convention

`resource:action` for 53 of the 54 seeded codes; the 54th is the standalone
`checkout`. The route-discovery feature depends on the convention: it derives a
route by stripping everything from the colon onward (`BR-USER-032`) and matches
actions by **substring**, not equality, so asking about `product` also returns the
actions of `product_lists:*` and `product_categories:*` (`BR-USER-033`).

### The coupling that matters

Authority codes are **string literals** duplicated across all eleven consuming
domains' controllers. Only `checkout` is declared as a constant. No compiler
checks them, so renaming a code silently disables an access check
(`BR-USER-031`). A split must publish the catalogue as a generated, versioned
artefact — see `../domain-decomposition-overview/anti-corruption-layers.md`.

### Authorities that exist but are never enforced

| Code | Status |
|---|---|
| `order_types:delete`, `order_types:create`, `order_types:update`, `order_types:read` | referenced by **no code at all** (`BR-USER-036`) |
| `products:read` | the product read route carries no authority check |
| `product_categories:read` | same |
| `product_lists:read` | same |
| `shippers:read` | same |

And the inverse gap: **no `billing_types:*`, `billing_companies:*`,
`addresses:*`, `payment_types:*` or `app_params:*` code exists**, so billing,
addresses, payment types and the company profile are outside the role model
entirely (`BR-BILLING-019`).

## Account protection

A single account may be marked undeletable through
`trebol.security.account-protection-enabled` and
`trebol.security.protected-account-id` (default: enabled, account 1 — the seeded
administrator). Enforced in `jpa/services/crud/impl/UsersCrudServiceImpl.java`.

Two defects: the guard inspects only a **single matched account**, so a filter
matching several accounts bypasses it entirely while the generic delete removes
them all (`BR-USER-042`); and the protection failure is an unmapped runtime
condition, so it surfaces as a 500-class response rather than a clean business
error (`BR-USER-043`).

## Configuration reference

| Key | Default | Effect |
|---|---|---|
| `trebol.security.jwt-secret-key` | a committed placeholder literal | signing key material; strength equals the property's length (`BR-USER-018`) |
| `trebol.security.jwt-expiration-after-minutes` | 30 | lifetime component |
| `trebol.security.jwt-expiration-after-hours` | 0 | lifetime component |
| `trebol.security.jwt-expiration-after-days` | 0 | lifetime component |
| `trebol.security.bcrypt-encoder-strength` | 10 | hashing cost, minimum 6 |
| `trebol.security.guest-user-enabled` | false | enables guest sessions |
| `trebol.security.guest-user-name` | `guest` | guest username **and** password |
| `trebol.security.guest-user-role-id` | 4 | role whose grants guests receive |
| `trebol.security.account-protection-enabled` | true | enables the undeletable-account guard |
| `trebol.security.protected-account-id` | 1 | the undeletable account |

The configuration holder is validated at startup, so a blank signing key or a
BCrypt strength below 6 prevents the application from starting. **All three
expiration components may legitimately be zero**, which issues
already-expired tokens with no warning.

## Security findings summary

| Finding | Rule |
|---|---|
| full token values are written to the application log on parse failure | `BR-USER-020` |
| an expired token proceeds anonymously instead of failing | `BR-USER-019` |
| the signing key is a committed placeholder documented as non-production | `BR-USER-018` |
| bootstrap credentials (four accounts, passwords equal to usernames) are in source control | `BR-USER-055` |
| no route-level authorization rules exist; unannotated endpoints are public | `BR-USER-025` |
| CSRF protection is disabled | `BR-USER-024` |
| the protected-account guard misses multi-row deletes | `BR-USER-042` |
| the guest password equals the guest name, which is public configuration | `BR-USER-026` |
| authority codes are unchecked string literals | `BR-USER-031` |
| revoked grants stay effective for up to one token lifetime | `BR-USER-022` |
| no password change, logout, revocation, refresh, lockout or audit trail | `BR-USER-023`, `-024`, `-045` |
| the payment validation callbacks are public with no signature or origin check | `BR-ORDER-040` |
| the public receipt lookup is token-addressed with no status restriction | `BR-ORDER-044`, `-045` |
| the profile replace route skips body validation entirely | `BR-USER-051` |

## Related documentation

- full identity rules: `../detailed-domain-bre/domain-user/`
- endpoint-to-authority matrix: `common-permissions.md`
- complete route list with access levels: `../reference-documentation/api-endpoint-catalog.md`
- the token-as-cache behaviour: `caching-patterns.md`
