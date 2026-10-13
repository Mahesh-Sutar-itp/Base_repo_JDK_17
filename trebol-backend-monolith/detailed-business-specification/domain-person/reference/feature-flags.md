# Person Registry — Feature Flags

**No feature flags applicable to this domain.**

No component of this domain reads a flag, toggle, switch, environment-conditional branch or
runtime-configurable behaviour selector. Every branch in the domain's 14 source files is
driven by request data or persisted state — never by configuration. Verified against the
system-wide `reference-documentation/feature-flag-inventory.md`.

## Configuration that does affect this domain's behaviour

Two application-wide settings alter the observable behaviour of the one endpoint. They are
**typed configuration values, not flags**, and they are owned by the shared kernel rather
than by this domain:

| Setting | Effect on this domain | Owner |
|---|---|---|
| default items-per-page | The page size applied to `GET /data/people` when the caller supplies no `pageSize`. | shared kernel (`PaginationService`) |
| maximum allowed page size | The ceiling applied to any caller-supplied `pageSize`, so a caller cannot request an unbounded page. | shared kernel (`PaginationService`) |

Behaviour specified in document `02` §7.2. Because these are not domain-owned and are not
typed per-entity configuration, no `specs/08-config-parameters.md` is produced — see
`specs/00-plan.md`.

## Dormant behaviour that is not a flag

The transport model carries a **commented-out** phone-number format rule (document `01`
§2.5). It is inert source code, not a disabled feature flag: there is no runtime switch
that enables it. Enabling it requires a code change and is a behavioural change, since it
would reject phone values the current system accepts (BR-PERSON-006).
