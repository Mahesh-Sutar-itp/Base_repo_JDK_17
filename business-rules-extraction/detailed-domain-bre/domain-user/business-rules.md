# Business Rules — Identity & Access (`user`)

## Account identity and structure

### BR-USER-001 Account names are unique
`app_users.user_name` carries UNIQUE KEY `UK_USER_NAME`. Asserting: two accounts
cannot share a name; the name is the login identifier and the token subject.
Source: `jpa/entities/User.java`, `database-documentation/00-global-schema.sql`.

### BR-USER-002 Every account must have exactly one role
`app_users.user_role_id` is not null and `User.userRole` is `optional=false`.
Asserting: an account without a role cannot exist, and multi-role accounts are
impossible — the model is single-role, not role-set.
Source: `jpa/entities/User.java`.

### BR-USER-003 An account may exist without a person profile
`app_users.person_id` is nullable and `User.person` is optional. Asserting: the
four seeded bootstrap accounts have no profile at all, and `GET /account/profile`
on such an account fails with a dedicated "no profile" error rather than a
generic one.
Source: `jpa/entities/User.java`, `database-documentation/00-seed-data.json`,
`api/services/impl/ProfileServiceImpl.java`.

### BR-USER-004 A person may be claimed by at most one account
`ProfileServiceImpl` rejects attaching a person already referenced by another
account with the bad-input message
`Person profile is associated to another account. Cannot use it.` Asserting: this
is enforced in application code only — `app_users.person_id` has **no** unique
constraint, so concurrent requests can bypass it.
Source: `api/services/impl/ProfileServiceImpl.java`,
`database-documentation/00-global-schema.sql`.

### BR-USER-005 Role names are unique
`app_user_roles.user_role_name` carries UNIQUE KEY `UK_UROLE_NAME`.
Source: `jpa/entities/UserRole.java`.

### BR-USER-006 Authority codes are unique
`app_permissions.permission_code` carries UNIQUE KEY `UK_PERM_CODE`. Asserting:
an authority string is a global identifier; the literal value is what appears in
every access check across every domain.
Source: `jpa/entities/Permission.java`.

### BR-USER-007 The same authority can be granted to a role twice
`app_user_role_permissions` has no uniqueness constraint over
`(permission_id, user_role_id)`. Asserting: duplicate grant rows are accepted by
the database; the duplication is invisible at login only because the resolution
service collects results into a set.
Source: `database-documentation/00-global-schema.sql`,
`security/services/impl/UserPermissionsServiceImpl.java`.

### BR-USER-008 Grants are immutable once written
Both join columns are mapped `updatable=false`. Asserting: a grant can be created
or deleted but never re-pointed.
Source: `jpa/entities/UserRolePermission.java`.

## Registration

### BR-USER-009 A taken account name aborts registration
Checked first, before any write, raising the already-exists error
`That username is taken.`
Source: `api/services/impl/RegistrationServiceImpl.java`.

### BR-USER-010 A registered identification number aborts registration
Checked after the person is converted but before it is saved, raising
`That ID number is already registered and associated to an account.`
Source: `api/services/impl/RegistrationServiceImpl.java`.

### BR-USER-011 Self-registered accounts always receive the `Customer` role
The role is resolved by the literal name `Customer`. A missing role row raises
`No user role matches 'Customer', database might be compromised`. Asserting:
registration cannot produce a privileged account, and renaming the `Customer`
role breaks registration entirely.
Source: `api/services/impl/RegistrationServiceImpl.java`.

### BR-USER-012 Registration is not transactional
`RegistrationServiceImpl.register` is **not** annotated transactional while it
performs three sequential writes (person, account, customer). Asserting: a failure
at the customer step leaves a persisted person and account with no customer row —
a partial registration that the system has no repair path for.
Source: `api/services/impl/RegistrationServiceImpl.java`.

### BR-USER-013 Passwords are hashed with BCrypt at the configured cost
Registration and account creation encode the password with the configured
strength (default 10, minimum enforced 6). Asserting: no plaintext password is
ever stored, and the cost is tunable without code change.
Source: `api/services/impl/RegistrationServiceImpl.java`,
`config/SecurityConfig.java`, `config/SecurityProperties.java`.

## Authentication and tokens

### BR-USER-014 Login is a filter, not a controller route
`/public/login` is processed by an authentication filter. Asserting: there is no
controller method, no documented request model on the controller layer and no
validation annotation processing for the login body — the body is deserialised
directly.
Source: `config/SecurityConfig.java`,
`security/JwtLoginAuthenticationFilter.java`.

### BR-USER-015 Only `POST` attempts authentication
A non-`POST` request to the login or guest URL returns no authentication token
from the filter. Asserting: a `GET` to `/public/login` does not authenticate.
Source: `security/JwtLoginAuthenticationFilter.java`,
`security/JwtGuestAuthenticationFilter.java`.

### BR-USER-016 The token is returned in both the header and the body
The same `Bearer `-prefixed value is written to the `Authorization` response
header and to the response body. Asserting: clients may read either; a rewrite
must preserve both to stay compatible.
Source: `security/GenericJwtAuthenticationFilter.java`.

### BR-USER-017 Token lifetime is the sum of three configured components
Days plus hours plus minutes, all permitted to be zero. Asserting: setting all
three to zero issues a token that is already expired, and nothing rejects that
configuration.
Source: `security/GenericJwtAuthenticationFilter.java`,
`config/SecurityProperties.java`.

### BR-USER-018 The signing key is derived directly from the configured string
The HMAC key is built from the raw bytes of `trebol.security.jwt-secret-key`
using the platform default charset. Asserting: key strength depends entirely on
the length of that property, and the shipped default is a placeholder literal
committed to source control. The configuration file itself states it must not be
used in production.
Source: `config/JwtKeyConfig.java`,
`src/main/resources/application.properties`.

### BR-USER-019 An expired token does not produce an authentication failure
The verification filter compares expiry to now and only populates the security
context when the token is still valid; otherwise it continues the chain
**anonymously**. Asserting: a request with an expired token is treated as
unauthenticated, so a protected endpoint fails the authority check rather than
reporting an expired credential. Clients cannot distinguish "expired" from
"never sent".
Source: `security/JwtTokenVerifierFilter.java`.

### BR-USER-020 A malformed or untrusted token yields an unauthorized status with no body
Parse and signature failures are mapped to an unauthorized response. The parser
raises a failure whose message embeds the token itself
(`Token %s can't be trusted`) and the filter logs the **full token value**.
Asserting: invalid tokens are written to the application log — a credential-leak
risk that a rewrite must not reproduce.
Source: `security/services/impl/ClaimsAuthorizationHeaderParserServiceImpl.java`,
`security/JwtTokenVerifierFilter.java`.

### BR-USER-021 The bearer prefix is stripped by substring replacement
The filter removes the prefix using a replace-all over the header value rather
than a leading-prefix strip. Asserting: a token whose body happened to contain
the prefix literal would be corrupted. The shared regular-expression adapter
exposes a proper header validator (`isAValidAuthorizationHeader`) but the filter
does not call it.
Source: `security/JwtTokenVerifierFilter.java`,
`common/services/impl/RegexMatcherAdapterServiceImpl.java`.

### BR-USER-022 Authorities travel inside the token
The authority list is read back out of the `authorities` claim as a list of
objects each carrying an `authority` key. Asserting: authorities are **not**
re-resolved from the database on each request, so revoking a grant has no effect
until the holder's token expires (default 30 minutes).
Source: `security/JwtTokenVerifierFilter.java`.

### BR-USER-023 Account status flags are hardcoded permissive
All four flags are set to the permissive value when building the account details.
Asserting: there is no disabled, locked or expired account concept — removing
access requires deleting the account or changing its role.
Source: `security/UserDetailsServiceImpl.java`.

### BR-USER-024 Sessions are stateless and CSRF protection is disabled
The security configuration sets a stateless session policy and disables CSRF.
Asserting: no server-side session exists, there is no logout and no token
revocation list.
Source: `config/SecurityConfig.java`.

### BR-USER-025 There are no route-level authorization rules
The filter chain declares no request-matcher rules; all authorization is
method-level. Asserting: any endpoint whose method carries no authority
annotation is **publicly reachable**, including `/`, `/public/about`,
`/public/receipt/{token}`, `/public/register` and both checkout-validation
routes.
Source: `config/SecurityConfig.java`,
`reference-documentation/api-endpoint-catalog.md`.

## Guest access

### BR-USER-026 The guest account's password equals its name
Both the guest filter and the account-details loader use the configured guest
name as the password. Asserting: the stored guest credential must hash that same
string, and the credential is therefore public knowledge from configuration.
Source: `security/JwtGuestAuthenticationFilter.java`,
`security/UserDetailsServiceImpl.java`.

### BR-USER-027 Guest access is disabled by default
`trebol.security.guest-user-enabled` defaults to false, and the account-details
loader only recognises the guest name when it is true. Asserting: with shipped
configuration the guest route authenticates nothing.
Source: `config/SecurityProperties.java`,
`security/UserDetailsServiceImpl.java`.

### BR-USER-028 A guest session creates a real customer record
The guest filter persists the submitted profile through the customer service
before authenticating. Asserting: guest checkout is not anonymous — it produces a
permanent person and customer row.
Source: `security/JwtGuestAuthenticationFilter.java`.

### BR-USER-029 A repeat guest is tolerated, not rejected
An already-exists failure from customer creation is logged
(`Guest with idNumber={} is already registered in the database`) and the flow
continues. Asserting: the same guest can start a session repeatedly.
Source: `security/JwtGuestAuthenticationFilter.java`.

### BR-USER-030 Guest authorities come from a configured role identifier
Not from the guest account's own role. Asserting: changing
`trebol.security.guest-user-role-id` changes what guests may do without touching
any account row; pointing it at the Administrator role would grant guests
administrative authorities.
Source: `security/UserDetailsServiceImpl.java`.

## Authorization model

### BR-USER-031 Authority codes follow a `resource:action` convention
Fifty-three of the fifty-four seeded codes match it; the fifty-fourth is the
standalone `checkout`. Asserting: the route-discovery feature depends on this
convention — it splits on the colon.
Source: `database-documentation/00-seed-data.json`,
`security/services/impl/AuthorizedApiServiceImpl.java`.

### BR-USER-032 Route discovery derives prefixes by stripping the action
The resource part is obtained by removing everything from the colon onward.
Asserting: the standalone `checkout` authority has no colon, so it is reported
as the route `checkout`.
Source: `security/services/impl/AuthorizedApiServiceImpl.java`.

### BR-USER-033 Action discovery uses substring matching, not equality
Actions are returned for any authority whose resource part **contains** the
supplied route. Asserting: asking about the route `product` returns the actions
of `products:*`, `product_lists:*` and `product_categories:*` as well — a
deliberate-looking but almost certainly unintended broadening.
Source: `security/services/impl/AuthorizedApiServiceImpl.java`.

### BR-USER-034 Access discovery failures are reported as unauthorized with no explanation
The access controller maps both "no such account" and an illegal-state failure to
an unauthorized response with an empty body. Asserting: the lack of explanation is
intentional in the source.
Source: `api/controllers/AccessController.java`.

### BR-USER-035 A missing or invalid authorization header yields a null body, not an error
The route-discovery read returns a null body when the header cannot be parsed.
Source: `api/controllers/AccessController.java`.

### BR-USER-036 Four seeded authority codes are dead
`order_types:delete`, `order_types:create`, `order_types:update` and
`order_types:read` are granted (all four to Administrator, the read and others
partially to Manager and Salesperson) but referenced by no code. Asserting:
removing them changes nothing.
Source: `database-documentation/00-seed-data.json`, repo-wide absence of
`order_types`.

### BR-USER-037 Several seeded read authorities are never enforced
`shippers:read`, `product_categories:read`, `products:read` and
`product_lists:read` exist and are granted, but their read routes carry no
authority check. Asserting: granting or revoking them has no effect.
Source: `reference-documentation/api-endpoint-catalog.md`,
`database-documentation/00-seed-data.json`.

### BR-USER-038 Role grants are seed data only
No route exposes the authority catalogue or the grant table. Asserting: changing
what a role may do requires a direct database statement; there is no
administration surface and no authority code for one.
Source: absence of any permission or grant controller in `api/controllers/`.

## Account and role administration

### BR-USER-039 A blank account name is rejected
`UsersCrudServiceImpl.getExisting` raises the bad-input message
`Invalid user name`.
Source: `jpa/services/crud/impl/UsersCrudServiceImpl.java`.

### BR-USER-040 A blank role name is rejected
`UserRolesCrudServiceImpl.getExisting` raises `Invalid user role name`.
Source: `jpa/services/crud/impl/UserRolesCrudServiceImpl.java`.

### BR-USER-041 The protected account cannot be deleted
When account protection is enabled and the matched account's identifier equals
the configured protected identifier, a dedicated protection failure is raised
with the message `Protected account cannot be deleted`. Asserting: with shipped
configuration the `admin` account (identifier 1) is undeletable.
Source: `jpa/services/crud/impl/UsersCrudServiceImpl.java`,
`security/exceptions/AccountProtectionViolationException.java`.

### BR-USER-042 The protection guard only inspects a single matched account
The override resolves one account from the filter and checks that one. Asserting:
a filter matching several accounts — including the protected one — is **not**
covered by the guard. Combined with the generic delete, which deletes every
matching row, this is an exploitable gap.
Source: `jpa/services/crud/impl/UsersCrudServiceImpl.java`,
`jpa/services/crud/CrudGenericService.java`.

### BR-USER-043 The protection failure is an unmapped runtime failure
`AccountProtectionViolationException` extends the unchecked base class and is not
handled by the global error advice. Asserting: the caller receives a generic
500-class response, not a clean business error envelope.
Source: `security/exceptions/AccountProtectionViolationException.java`,
`api/ExceptionsControllerAdvice.java`.

### BR-USER-044 There is no partial-update route for accounts or roles
`DataUsersController` and `DataUserRolesController` map only read, create,
replace and delete. Asserting: `PATCH /data/users` and `PATCH /data/user_roles`
are not routed, so every account or role modification is a full replacement.
Source: `api/controllers/DataUsersController.java`,
`api/controllers/DataUserRolesController.java`.

### BR-USER-045 The account patch capability exists, excludes the password, and is unreachable
`UsersPatchServiceImpl` handles `name` (non-blank only) and `role` (resolved by
role name, applied only when the name resolves, so an unknown role name would be
silently ignored). It never touches the password. Asserting: the logic is wired
as a bean but no route invokes it; combined with BR-USER-044 this means there is
**no password-change capability anywhere in the system** — a forgotten password
requires replacing or recreating the account.
Source: `jpa/services/patch/impl/UsersPatchServiceImpl.java`,
`api/controllers/DataUsersController.java`.

### BR-USER-046 Replacing an account overwrites its password with the submitted body
The generic replace path builds a new entity from the body. Asserting: a `PUT`
that omits the password cannot satisfy the not-null column, and one that includes
it re-hashes and replaces the stored credential — replacement is the only
password-setting path after creation.
Source: `jpa/services/crud/CrudGenericService.java`,
`jpa/services/conversion/impl/UsersConverterServiceImpl.java`.

### BR-USER-047 Deleting a role referenced by an account is blocked by the database
`FK_USER_UROLE_ID` and `FK_UROLEPERMS_UROLE_ID` have no cascade and the domain
performs no pre-delete cleanup. Asserting: deleting any seeded role fails on a
constraint, because every seeded role has both accounts and grants.
Source: `database-documentation/00-global-schema.sql`.

### BR-USER-048 Deleting an account does not delete its person
`User.person` has no cascade on removal. Asserting: person rows accumulate after
account deletion, and the orphaned person remains claimable by a new account.
Source: `jpa/entities/User.java`.

### BR-USER-049 Account and role administration require explicit authorities
`users:read|create|update|delete` and `user_roles:read|create|update|delete`,
all behind class-level authentication (the update authority governs replacement
only, since no patch route exists). Seeded holders: Administrator holds all
eight; Manager holds only `user_roles:read` and `people:read`-adjacent reads (it
does **not** hold any `users:*` authority); Salesperson and Customer hold none.
Asserting: only an Administrator can manage accounts.
Source: `api/controllers/DataUsersController.java`,
`api/controllers/DataUserRolesController.java`,
`database-documentation/00-seed-data.json`.

### BR-USER-050 Profile endpoints require only authentication
`GET` and `PUT /account/profile` carry a class-level authentication requirement
and no authority requirement. Asserting: every authenticated principal, including
a guest, may read and replace its own profile.
Source: `api/controllers/AccountProfileController.java`.

### BR-USER-051 The profile replace route skips body validation
The replace method accepts the person model **without** the validation marker,
unlike every other write route in the system. Asserting: the mandatory-field
rules on names, identification number and e-mail are bypassed on this route and
only the not-null columns protect the data.
Source: `api/controllers/AccountProfileController.java`,
`api/models/PersonPojo.java`.

### BR-USER-052 Distinct failures exist for missing account and missing profile
`There is no account with the specified username` versus
`The account does not have an associated profile`, raised by two dedicated
exception types. Asserting: neither is mapped by the global error advice, so both
surface as 500-class responses despite being clearly client-side conditions.
Source: `api/services/impl/ProfileServiceImpl.java`,
`jpa/exceptions/UserNotFoundException.java`,
`jpa/exceptions/PersonNotFoundException.java`,
`api/ExceptionsControllerAdvice.java`.

### BR-USER-053 Account filtering can reach the linked person's e-mail
The account predicate service supports `email` and `emailLike` by traversing the
person association. Asserting: accounts are searchable by profile e-mail even
though e-mail is not an account attribute.
Source: `jpa/services/predicates/impl/UsersPredicateServiceImpl.java`.

### BR-USER-054 The role sort specification uses a non-conforming field name
Every sort specification holder in the codebase exposes an upper-case constant
except the role one, which uses a lower-case name. Asserting: this is a
consistency defect, not a behavioural one.
Source: `jpa/sortspecs/UserRolesSortSpec.java`.

### BR-USER-055 Bootstrap credentials are committed to source control
The four seeded accounts have passwords equal to their names, hashed at cost 10,
and the seed file documents this. Asserting: any deployment that runs the shipped
seed script has four publicly known accounts, one of them an Administrator.
Source: `src/main/resources/data.sql`,
`database-documentation/00-seed-data.json`.
