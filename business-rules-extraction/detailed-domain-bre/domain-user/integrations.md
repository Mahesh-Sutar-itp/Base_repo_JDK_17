# Integrations — Identity & Access (`user`)

## External systems

**None.** There is no identity provider, no directory service, no single-sign-on,
no OAuth or OpenID relying-party configuration, no e-mail delivery for account
verification or password reset, and no external secret store. Every credential,
role and authority lives in this application's own database, and the signing key
comes from a local configuration property.

The only third-party code in the security path is the token library used for
signing and parsing; it is a local dependency, not a network integration.

## Inbound integrations

| Caller | Entry point | Purpose |
|---|---|---|
| every domain's controllers | authority strings resolved from this domain's catalogue | method-level access checks |
| the framework filter chain | the login, guest and verification filters | authentication for all routes |
| storefront clients | `/access` and `/access/{apiRoute}` | discover what the current principal may do |

No other domain holds a compile-time dependency on this domain's services. The
coupling is through **authority string literals** scattered across every
controller, which is a design-level dependency that no compiler enforces: a
renamed authority code silently breaks access control instead of failing a build.
Recorded in `reference-documentation/coupling-hotspots.md`.

## Outbound integrations

| Target domain | Entry point | Purpose |
|---|---|---|
| `person` | person converter, person repository, person patch service | registration creates a person; profile editing attaches or patches one |
| `customer` | customer crud service | registration creates a customer row; the guest filter creates one before authenticating |

| Shared-kernel target | Nature |
|---|---|
| generic CRUD base | account and role create/read/update/patch/delete |
| generic controller bases | the two data controllers |
| pagination service, sort-spec parser | account and role listing |
| password encoder, authentication manager, filter chain | authentication infrastructure |
| configuration holders | all ten security properties |
| regular-expression adapter service | exposes an authorization-header validator that the verification filter does **not** use |

Note the outbound dependency direction: the security kernel depends on two
business domains (`person`, `customer`), not the other way round. This is the
main structural obstacle to extracting `user` as a standalone identity service —
registration and guest access would need to call the person and customer services
over a boundary, or those writes would need to move out of the registration flow.

## Events

**None.** No event is published on login, logout (which does not exist),
registration, role change, grant change or account deletion. A rewrite that wants
to react to identity changes — invalidate caches, notify auditing, provision
downstream records — has no existing pattern to extend. See
`cross-cutting-documentation/event-publishing-patterns.md`.

## Scheduled work

**None.** No token cleanup (tokens are stateless), no stale-account reaping, no
credential-expiry sweep, no grant reconciliation.

## Caching

**None.** Authorities are resolved from the database on each authentication, then
carried inside the token for its lifetime — which is the system's only
access-control caching mechanism, and an implicit one. Its practical consequence
is BR-USER-022: a revoked grant stays effective until the holder's token expires.

## Logging and observability

Ad-hoc log statements only:

| Event | Level | Notes |
|---|---|---|
| account created at registration | info | includes account name and identification number |
| repeat guest | info/debug | includes identification number |
| invalid token | warn/error | **includes the full token value** — credential leak |

There is no structured audit record of authentication attempts, authorization
denials or privilege changes.

## Anti-corruption surface

Accounts and roles have converters and patch services isolating transport from
persistence, and the account-details loader is a clean adapter between this
domain's entities and the framework's security contract. Two leaks:

1. Authority strings are duplicated as literals in every consuming controller
   rather than being referenced from a shared catalogue — only the standalone
   checkout authority is declared as a constant.
2. The registration flow reaches directly into the `person` and `customer`
   domains' persistence rather than negotiating through a contract.

See `domain-decomposition-overview/anti-corruption-layers.md`.
