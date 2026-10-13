# Service: Identity & Access (`user`)

**Proposed service name:** `identity-service`  
**Extraction position:** 7 of 12  
**Complexity:** large  
**Size:** 56 source files, 3,404 lines

## Responsibility

The security kernel as a business domain: accounts, roles, the authority catalogue, role grants, self-registration, guest sessions, token issuance and verification, authority resolution, the account-profile surface and the protected-account guard.

## Owned data

| Table | Entity |
|---|---|
| `app_users` | `User` |
| `app_user_roles` | `UserRole` |
| `app_permissions` | `Permission` |
| `app_user_role_permissions` | `UserRolePermission` |

No other domain writes these tables. See `data-ownership.md` for the ownership basis.

## Component breakdown

| Role | Count |
|---|---|
| entity | 4 |
| controller | 4 |
| service | 27 |
| repository | 4 |
| handler | 4 |
| helper | 2 |
| other | 11 |

## API surface

- POST /public/login — **filter, not a controller**
- POST /public/guest — **filter**, enabled by configuration
- POST /public/register — **public**
- GET /account/profile — authenticated
- PUT /account/profile — authenticated, **body not validated**
- GET /access, GET /access/{apiRoute} — authenticated
- GET POST PUT DELETE /data/users — `users:*` (**no PATCH route**)
- GET POST PUT DELETE /data/user_roles — `user_roles:*` (**no PATCH route**)

## Inbound contracts (who would call this service)

| Caller | Purpose | Current mechanism |
|---|---|---|
| `every domain` | authority enforcement through **string literals** in annotations | convention — **shared vocabulary needed** |

### Contract this service must expose

```text
verifyToken(bearer)          -> { subject, authorities } or anonymous
resolveAuthorities(accountId) -> set of authority codes
authorityCatalogue()          -> versioned list of codes (generated constants)
```

## Outbound contracts (what this service would call)

| Target | Purpose | Current mechanism |
|---|---|---|
| `person` | create, attach and patch person rows during registration and profile editing | repository + internals — **contract needed** |
| `customer` | create a customer row during registration and guest authentication | **service** — survives a split unchanged |

## Risks and decisions required

1. **The security kernel depends outward on two business domains.** Registration and guest authentication write `person` and `customer` rows, which is the reverse of the usual direction and the main obstacle to extracting identity.
2. Registration is **not transactional** across three writes (`BR-USER-012`), so partial registration is already possible today and becomes the normal failure mode after a split.
3. Authorities are frozen inside the issued token, so a revoked grant stays effective for up to the configured lifetime (`BR-USER-022`). A split must choose between trusting token claims and re-resolving grants per request.
4. Authority codes are duplicated as literals across all eleven other domains with no compile-time check (`BR-USER-031`); a rename silently disables an access check.
5. Security defects to decide on explicitly: full token values are logged on parse failure (`BR-USER-020`), an expired token proceeds anonymously (`BR-USER-019`), the protected-account guard misses multi-row deletes (`BR-USER-042`), the signing key is a committed placeholder (`BR-USER-018`), and bootstrap credentials are in source control (`BR-USER-055`).
6. There is no password change, no logout, no revocation and no account status (`BR-USER-023`, `BR-USER-024`, `BR-USER-045`).

## Extraction readiness

**Low — extract second to last.** Internally coherent but outbound-coupled to `person` and `customer`, and consumed implicitly by every other domain.

## Cross-references

- behavioural detail: `../detailed-domain-bre/domain-user/` (55 rules, 15 features)
- boundary justification: `bounded-contexts.md`
- table ownership: `data-ownership.md`
- translation contracts: `anti-corruption-layers.md`
- extraction sequencing: `execution-order.md`
