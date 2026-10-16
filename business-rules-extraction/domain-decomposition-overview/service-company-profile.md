# Service: Company Profile (`company-profile`)

**Proposed service name:** `storefront-profile-service`  
**Extraction position:** 6 of 12  
**Complexity:** small  
**Size:** 5 source files, 249 lines

## Responsibility

The application's entity-attribute-value parameter store and the one feature built on it: the public storefront profile (name, description, banner and logo URLs).

## Owned data

| Table | Entity |
|---|---|
| `app_params` | `Param` |

No other domain writes these tables. See `data-ownership.md` for the ownership basis.

## Component breakdown

| Role | Count |
|---|---|
| entity | 1 |
| service | 2 |
| repository | 1 |
| other | 1 |

## API surface

- GET /public/about — **public**
- _(there is no write route of any kind)_

## Inbound contracts (who would call this service)

**None.** No domain consumes this service, so it can be extracted without
introducing any new contract on the inbound side.

### Contract this service must expose

```text
_(none — nothing consumes this domain)_
```

## Outbound contracts (what this service would call)

**None.** This service has no dependency on any other business domain.

## Risks and decisions required

1. No write path exists at all (`BR-COMPANY-008`): changing the shop name requires a direct database statement, and no authority code covers parameters. A rewrite must decide between adding a guarded write route and promoting the four values to typed configuration.
2. A mistyped parameter name is indistinguishable from a missing one — both yield an absent response field with no diagnostic (`BR-COMPANY-004`).
3. The two image URLs are unvalidated free text rendered directly by storefront clients (`BR-COMPANY-009`).

## Extraction readiness

**Highest in the system.** One table, no foreign key in either direction, no consumer, and the only domain that does not inherit the generic CRUD or controller base classes. Extract first.

## Cross-references

- behavioural detail: `../detailed-domain-bre/domain-company-profile/` (13 rules, 5 features)
- boundary justification: `bounded-contexts.md`
- table ownership: `data-ownership.md`
- translation contracts: `anti-corruption-layers.md`
- extraction sequencing: `execution-order.md`
