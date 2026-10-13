# Service: Person Registry (`person`)

**Proposed service name:** `person-service`  
**Extraction position:** 1 of 12  
**Complexity:** small  
**Size:** 14 source files, 755 lines

## Responsibility

Canonical record of a natural person: legal name, national identification number, e-mail and two phone numbers.

## Owned data

| Table | Entity |
|---|---|
| `people` | `Person` |

No other domain writes these tables. See `data-ownership.md` for the ownership basis.

## Component breakdown

| Role | Count |
|---|---|
| entity | 1 |
| controller | 1 |
| service | 8 |
| repository | 1 |
| helper | 1 |
| other | 2 |

## API surface

- GET /data/people — authority `people:read`

## Inbound contracts (who would call this service)

| Caller | Purpose | Current mechanism |
|---|---|---|
| `user` | create / attach / patch a person during registration and profile editing | repository + converter + patch internals — **contract needed** |
| `customer` | cascading one-to-one create of the person behind a customer | ORM cascade — **distributed write** |
| `salesperson` | cascading many-to-one create of the person behind a salesperson | ORM cascade — **distributed write** |

### Contract this service must expose

```text
findByIdNumber(idNumber)            -> person or empty
createPerson(profile)               -> person
patchPerson(id, sparseChanges)       -> person
isClaimedByAnotherAccount(idNumber)  -> boolean
```

## Outbound contracts (what this service would call)

**None.** This service has no dependency on any other business domain.

## Risks and decisions required

1. Two ORM cascades write this domain's table from other aggregates; after a split both become distributed creates and the atomicity in `BR-CUSTOMER-003` is lost.
2. `app_users.person_id` has **no** unique constraint, so the one-person-one-account rule is application-only (`BR-USER-004`) and becomes a cross-service race after a split.
3. No delete path exists anywhere, so person rows accumulate; a split inherits an append-only table with orphans created by every account, customer and salesperson deletion.

## Extraction readiness

**Medium.** Trivial internally (one table, one small service) but three inbound writers reach past its service layer. Recommended: co-deploy with `customer` and `salesperson` (2,076 lines combined) rather than splitting all three.

## Cross-references

- behavioural detail: `../detailed-domain-bre/domain-person/` (13 rules, 7 features)
- boundary justification: `bounded-contexts.md`
- table ownership: `data-ownership.md`
- translation contracts: `anti-corruption-layers.md`
- extraction sequencing: `execution-order.md`
