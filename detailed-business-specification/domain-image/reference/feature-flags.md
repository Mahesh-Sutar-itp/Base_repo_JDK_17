# Image Registry — Feature Flags

**No feature flags applicable to this domain.**

No component reads a flag, toggle, switch, environment-conditional branch or runtime
behaviour selector. Every branch across the domain's 13 source files is driven by request
data or persisted state — never by configuration. Verified against the system-wide
`reference-documentation/feature-flag-inventory.md`.

## Configuration that does affect this domain's behaviour

Two application-wide settings alter the observable behaviour of the list endpoint. They are
**typed configuration values, not flags**, and they belong to the shared kernel rather than
to this domain:

| Setting | Effect on this domain | Owner |
|---|---|---|
| default items-per-page | The page size applied to `GET /data/images` when the caller supplies no `pageSize`. | shared kernel (`PaginationService`) |
| maximum allowed page size | The ceiling applied to any caller-supplied `pageSize`, so a caller cannot request an unbounded page. | shared kernel (`PaginationService`) |

Behaviour specified in document `02` §7.2. Because these are not domain-owned and are not
typed per-entity configuration, no `specs/08-config-parameters.md` is produced — see
`specs/00-plan.md` §4.

## Behaviour that looks configurable but is not

Three things a reader might expect to find behind a flag, and will not:

| Expectation | Reality |
|---|---|
| A toggle for URL validation or allow-listing | **None exists.** There is no validation to enable — no HTTP client, no scheme check, no host list (BR-IMAGE-005). Adding it is a code change. |
| A limit on how many images a `DELETE` may remove | **None exists.** Deletion is unbounded and not configurable (BR-IMAGE-013). |
| A switch between filename-only and all-fields duplicate detection | **None exists.** `getExisting` resolves by filename unconditionally (BR-IMAGE-002). |

Each of these is a hard-coded behaviour a rewrite may want to change, but none is currently
parameterised, so there is no existing configuration surface to carry forward.
