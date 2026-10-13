# Features — Identity & Access (`user`)

## F-001 Password login with token issuance

`POST /public/login`, handled by a filter rather than a controller. The request
body is deserialised into a login model (`name`, `password`), delegated to the
authentication manager, and on success a signed token is written **both** to the
`Authorization` response header (prefixed `Bearer `) and to the response body.
A non-`POST` request to the URL yields no authentication attempt. A body that
cannot be deserialised produces a bad-credentials failure with the message
`Request body is not a login request`.

## F-002 Token contents and lifetime

The issued token carries four claims: subject (the account name), `authorities`,
issued-at and expiration. Lifetime is the sum of the configured days, hours and
minutes (default 30 minutes). No issuer, audience or token identifier claim is
present. Signed with an HMAC-SHA key derived directly from the configured secret
string.

## F-003 Guest session creation

`POST /public/guest`, handled by a filter. The request body is a person profile;
the filter persists it as a customer, then authenticates using the configured
guest name as **both** username and password. Enabled only when
`trebol.security.guest-user-enabled` is true. An already-registered guest
identification number is logged and tolerated rather than rejected.

## F-004 Token verification on every request

A verification filter reads the `Authorization` header. A blank header lets the
request continue anonymously. Otherwise the token is parsed and signature-verified,
the expiration is compared to the current instant, and only a still-valid token
populates the security context with the account name and its authorities.

## F-005 Authority resolution at login

For a regular account the authority set is resolved by loading the account's role
and then every authority granted to that role, de-duplicated in memory. Each
authority code becomes a granted authority string. All four account-status flags
(expired, locked, credentials-expired, enabled) are hard-coded to the permissive
value.

## F-006 Guest authority resolution

For the guest account the authority set is loaded from the grants of the
configured guest role identifier (default 4, the `Customer` role) rather than
from any account row.

## F-007 Self-registration

`POST /public/register`, public. Validates account-name uniqueness, converts the
submitted profile into a person, validates identification-number uniqueness,
persists the person, creates the account with a hashed password and the hardcoded
`Customer` role, then creates a customer row for that person.

## F-008 Read own profile

`GET /account/profile`, authenticated. Resolves the account from the
authenticated principal's name and returns its person. Distinct failures exist
for "no such account" and "account has no profile".

## F-009 Replace own profile

`PUT /account/profile`, authenticated. Three behaviours depending on state: patch
the existing person; attach an existing unowned person found by identification
number; or create and attach a new person. Attaching a person already owned by
another account is rejected.

## F-010 Discover accessible route prefixes

`GET /access`, authenticated. Parses the caller's own token, loads the account
details and returns the distinct resource prefixes of its authorities — the
`resource` part of every `resource:action` code. Used by clients to decide which
navigation to render.

## F-011 Discover accessible actions for one route

`GET /access/{apiRoute}`, authenticated. Returns the action suffixes of the
caller's authorities whose resource part contains the supplied route. Matching is
a substring test, not an equality test.

## F-012 Account administration

`/data/users` — read, create, replace and delete behind `users:read`,
`users:create`, `users:update` and `users:delete`. Account name is the business
key. There is **no `PATCH` route**: `UsersPatchServiceImpl` is implemented and
wired (supporting `name` and `role` by role name, never the password) but
unreachable, so replacement is the only way to modify an account.

## F-013 Role administration

`/data/user_roles` — read, create, replace and delete behind
`user_roles:read`, `user_roles:create`, `user_roles:update` and
`user_roles:delete`. Role name is the business key and the only field. As with
accounts there is **no `PATCH` route**, though `UserRolesPatchServiceImpl` is
implemented and wired.

## F-014 Protected-account guard

When account protection is enabled, deleting the account whose identifier equals
the configured protected identifier raises a dedicated protection failure. Only
the single matched account is checked.

## F-015 Account filtering and sorting

Accounts filter on `id`, `name` (both short-circuit), `email` (through the linked
person), `nameLike` and `emailLike`; sortable by `name` and `role`. Roles filter
on `id`, `name` (both short-circuit) and `nameLike`; sortable by `name` only.

## Features deliberately absent

| Expected capability | Status |
|---|---|
| password change / reset | **absent** — password is set only at registration or account creation |
| logout / token revocation | **absent** — tokens are stateless and valid until expiry |
| token refresh | **absent** |
| account lockout or failed-attempt tracking | **absent** — status flags are hardcoded permissive |
| multi-factor authentication | **absent** |
| authority catalogue management | **absent** — no route exposes permissions or grants |
| audit log of access decisions | **absent** |
