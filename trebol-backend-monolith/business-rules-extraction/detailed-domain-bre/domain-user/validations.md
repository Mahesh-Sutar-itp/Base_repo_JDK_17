# Validations — Identity & Access (`user`)

## Transport-model validation

| Model | Field | Constraint |
|---|---|---|
| `LoginPojo` | `name` | `@NotBlank` |
| `LoginPojo` | `password` | `@NotBlank` |
| `RegistrationPojo` | `name` | `@NotBlank` |
| `RegistrationPojo` | `password` | `@NotBlank` |
| `RegistrationPojo` | `profile` | nested validation applied |
| `UserPojo` | `name` | `@NotBlank` |
| `UserPojo` | `password` | `@NotBlank` |
| `UserPojo` | `person` | **no nested validation** |
| `UserPojo` | `role` | **none** |
| `UserRolePojo` | `name` | `@NotNull` (not `@NotBlank` — a blank name passes) |
| `AuthorizedAccessPojo` | — | outbound only, no constraints |

Source: `api/models/`.

Two important gaps visible in this table:

- **`LoginPojo` constraints never run.** The login body is deserialised inside a
  filter with a directly constructed object mapper, bypassing the validation
  infrastructure entirely. A blank name or password reaches the authentication
  manager and simply fails as bad credentials.
- **`UserRolePojo.name` uses the weaker annotation.** Unlike every other
  name-bearing model in the system it permits a whitespace-only value at the
  transport layer; the service-level blank check is the only thing that stops it.

## Persistence-layer validation

| Column | Constraint |
|---|---|
| `user_name` | not null, unique, max 50, ORM min 1, indexed |
| `user_password` | not null, max 100, ORM min 1 |
| `app_users.person_id` | nullable, foreign key to `people` — **no unique constraint** |
| `app_users.user_role_id` | not null, foreign key to `app_user_roles` |
| `user_role_name` | not null, unique, max 50, ORM min 1, indexed |
| `permission_code` | not null, unique, max 25, ORM min 1 |
| `permission_description` | nullable, max 100 |
| `app_user_role_permissions.permission_id` | not null, foreign key, `updatable=false` |
| `app_user_role_permissions.user_role_id` | not null, foreign key, `updatable=false` |

## Service-layer validation

| Check | Component | Failure |
|---|---|---|
| account name present | `UsersCrudServiceImpl.getExisting` | bad input, `Invalid user name` |
| role name present | `UserRolesCrudServiceImpl.getExisting` | bad input, `Invalid user role name` |
| account name unused at registration | `RegistrationServiceImpl` | already exists, `That username is taken.` |
| identification number unused at registration | `RegistrationServiceImpl` | already exists, `That ID number is already registered and associated to an account.` |
| the `Customer` role must exist | `RegistrationServiceImpl.convertToUser` | illegal state, `No user role matches 'Customer', database might be compromised` |
| account must exist for a profile operation | `ProfileServiceImpl` | account-not-found, `There is no account with the specified username` |
| account must have a profile to read it | `ProfileServiceImpl` | profile-not-found, `The account does not have an associated profile` |
| a person may not be claimed twice | `ProfileServiceImpl` | bad input, `Person profile is associated to another account. Cannot use it.` |
| the protected account may not be deleted | `UsersCrudServiceImpl.delete` | protection failure, `Protected account cannot be deleted` |
| account/role must not already exist on create | `CrudGenericService` | already exists, `The item already exists` |
| login body must be deserialisable | `JwtLoginAuthenticationFilter` | bad credentials, `Request body is not a login request` |
| guest body must be deserialisable | `JwtGuestAuthenticationFilter` | bad credentials, `Invalid request body for guest session` |
| guest profile must be sufficient | `JwtGuestAuthenticationFilter` | bad credentials, `Insufficient or invalid profile data for guest` |
| token must be parseable and trusted | `ClaimsAuthorizationHeaderParserServiceImpl` | illegal state, `Token %s can't be trusted` |

## Configuration-level validation

| Key | Declared constraint |
|---|---|
| `trebol.security.jwt-secret-key` | must not be blank |
| `trebol.security.jwt-expiration-after-minutes` | zero or positive |
| `trebol.security.jwt-expiration-after-hours` | zero or positive |
| `trebol.security.jwt-expiration-after-days` | zero or positive |
| `trebol.security.bcrypt-encoder-strength` | minimum 6 |

Asserting: the configuration holder is validated at startup, so a blank secret
key or a BCrypt strength below 6 prevents the application from starting. But
**all three expiration components may legitimately be zero**, which yields
immediately-expired tokens with no warning.

## Error-mapping gaps

The global error advice maps only four conditions (entity-not-found,
entity-exists, bad-input and the framework validation failure). Every other
condition raised by this domain is unmapped and therefore surfaces as a generic
500-class response:

| Condition | Should be | Actually is |
|---|---|---|
| account not found (profile) | 404 | 500 |
| profile not found | 404 | 500 |
| protected-account deletion | 409 or 403 | 500 |
| `Customer` role missing | 500 (correct — it is a data-integrity fault) | 500 |

The access-discovery controller is the exception: it maps its two conditions to
an unauthorized status locally.

## Gaps summary

- **No password change or reset** anywhere; replacement is the only path.
- **No token revocation**, so authority changes take up to one token lifetime to
  apply, and a compromised token cannot be invalidated.
- **Expired tokens are indistinguishable from absent ones** to the client.
- **Full token values are written to the application log** on parse failure.
- **The signing key is a committed placeholder** whose strength equals the length
  of a property string.
- **Registration is non-transactional** across three writes.
- **The protected-account guard misses multi-row deletes.**
- **`app_users.person_id` lacks a unique constraint**, so the one-person-one-account
  rule is enforced only by a non-atomic application check.
- **The profile replace route skips body validation** entirely.
- **No audit trail** of logins, failures or access decisions beyond ad-hoc log
  lines.
