# Workflows — Identity & Access (`user`)

## W-USER-01 Password login

1. Client `POST`s `{name, password}` to `/public/login`.
2. The login filter deserialises the body. A deserialisation failure raises a
   bad-credentials condition with the message
   `Request body is not a login request`. A non-`POST` method yields no
   authentication attempt at all.
3. The authentication manager delegates to the account-details loader.
4. Guest branch: if guest access is enabled and the submitted name equals the
   configured guest name, guest details are built from the configured guest role
   identifier's grants.
5. Regular branch: the account is loaded with its role. A miss raises an
   account-not-found condition carrying the submitted name.
6. Authorities are resolved: role identifier → every grant for that role →
   each authority code, de-duplicated into a set.
7. The password is compared against the stored BCrypt hash at the configured
   cost.
8. On success a token is built with the subject, the authority list, an issued-at
   stamp and an expiration computed from the configured days + hours + minutes,
   then signed with the HMAC key derived from the configured secret.
9. The `Bearer `-prefixed token is written to the `Authorization` response header
   **and** to the response body.

## W-USER-02 Guest session

1. Client `POST`s a person profile to `/public/guest`.
2. The guest filter persists it through the customer service. An already-exists
   failure is logged and tolerated.
3. The filter authenticates with the configured guest name as both username and
   password.
4. The account-details loader recognises the guest name only when guest access is
   enabled; otherwise the lookup falls through to a normal account lookup and
   fails.
5. On success a token is issued exactly as in W-USER-01, carrying the guest
   role's authorities.

## W-USER-03 Per-request token verification

1. The verification filter reads the `Authorization` header.
2. Blank header → the chain continues with no security context (anonymous).
3. Otherwise the bearer prefix is stripped by replace-all and the token is
   parsed with signature verification.
4. Parse or signature failure → the full token is logged and an unauthorized
   status is set with no body.
5. Expiration is compared to the current instant:
   - still valid → a security context is populated from the subject and the
     `authorities` claim (read as a list of objects each with an `authority` key);
   - already expired → **no context is set and the chain continues anonymously**.
6. Method-level authority checks then run against whatever context exists.

## W-USER-04 Self-registration

1. Client `POST`s `{name, password, profile}` to `/public/register` (public).
2. Transport validation enforces non-blank name and password and validates the
   nested profile.
3. Account-name uniqueness is checked → `That username is taken.`
4. The profile is converted to a person; identification-number uniqueness is
   checked → `That ID number is already registered and associated to an account.`
5. The person is saved and flushed.
6. The account is built with the BCrypt-hashed password and the role resolved by
   the literal name `Customer`; a missing role raises
   `No user role matches 'Customer', database might be compromised`.
7. The account is saved and a creation log line is written.
8. A customer row is created for the person.

**No transaction wraps steps 5–8.** A failure in step 8 leaves a persisted person
and account with no customer row.

## W-USER-05 Read own profile

1. Authenticated client `GET`s `/account/profile`.
2. The account name is taken from the authenticated principal.
3. Account missing → `There is no account with the specified username`.
4. Account has no person → `The account does not have an associated profile`.
5. Otherwise the person is converted and returned.

Both failures are unmapped runtime conditions and surface as 500-class responses.

## W-USER-06 Replace own profile

1. Authenticated client `PUT`s a person model to `/account/profile`.
   **The body is not validated** (no validation marker on this route).
2. The account is resolved by principal name; a miss raises the account-missing
   condition.
3. Account has **no** person:
   - look for an existing person by identification number;
   - found and already referenced by another account →
     `Person profile is associated to another account. Cannot use it.`;
   - found and unowned → attach it;
   - not found → create, save and attach a new person.
4. Account **has** a person: delegate to the person patch service (non-blank-only
   semantics for names, identification number and e-mail; unconditional for the
   two phone fields), then save.

This method **is** transactional.

## W-USER-07 Discover accessible routes

1. Authenticated client `GET`s `/access`.
2. The controller extracts the `Authorization` header and strips the bearer
   prefix.
3. Header missing or unparseable → a null body is returned.
4. Otherwise the token is parsed, the account details are loaded, and the distinct
   resource prefixes of its authorities are returned.
5. Account-not-found or an illegal-state condition → unauthorized with an empty
   body.

## W-USER-08 Discover actions for one route

As W-USER-07, but the response is the action suffixes of every authority whose
resource part **contains** the supplied route segment — substring matching, so
broader than expected.

## W-USER-09 Account administration

Read, create, replace and delete at `/data/users` behind the four `users:*`
authorities — **there is no patch route**:

- **create** — blank name rejected with `Invalid user name`; an existing name is
  a conflict; the password is BCrypt-hashed.
- **replace** — full overwrite from the body, including the password. This is the
  only way to modify an account, and therefore the only way to change a password.
- **delete** — the protected-account guard runs first on the single matched
  account; otherwise every matching row is deleted. The account's person row is
  left behind.
- **(unreachable) patch** — `UsersPatchServiceImpl` would apply `name` when
  non-blank and `role` by name, silently ignoring an unknown role name, and would
  never touch the password. No route invokes it.

## W-USER-10 Role administration

Read, create, replace and delete at `/data/user_roles` behind the four
`user_roles:*` authorities; **no patch route exists**. A blank name is rejected
with `Invalid user role name`; the name is the business key; deletion of any role
that has accounts or grants fails on a foreign-key constraint, which with the
shipped seed data means every seeded role is undeletable.

## W-USER-11 Changing what a role may do (out of band)

No API exists. An operator must insert or delete `app_user_role_permissions`
rows directly. The change takes effect for a given principal only when that
principal's current token expires, because authorities travel inside the token
(BR-USER-022).

## State model

Neither accounts nor roles nor authorities have a status column. The only
lifecycle states in this domain are **session states**, and they live entirely in
the token, not in the database:

```text
anonymous            no header, blank header, or an expired token
authenticated        valid signed token within its expiration window
unauthorized         malformed or untrusted token (status set, chain stopped)
```

There is no logout, no revocation, no refresh and no account-level
enabled/disabled/locked state — all four account-status flags are hardcoded
permissive.
