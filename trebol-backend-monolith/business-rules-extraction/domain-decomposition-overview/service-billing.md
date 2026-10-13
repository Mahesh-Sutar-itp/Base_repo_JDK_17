# Service: Billing (`billing`)

**Proposed service name:** `billing-service`  
**Extraction position:** 4 of 12  
**Complexity:** small  
**Size:** 24 source files, 1,095 lines

## Responsibility

Invoicing classification: the billing-type catalogue that selects the individual-versus-enterprise branch, and the register of invoiced companies.

## Owned data

| Table | Entity |
|---|---|
| `billing_types` | `BillingType` |
| `billing_companies` | `BillingCompany` |

No other domain writes these tables. See `data-ownership.md` for the ownership basis.

## Component breakdown

| Role | Count |
|---|---|
| entity | 2 |
| controller | 1 |
| service | 16 |
| repository | 2 |
| helper | 1 |
| other | 2 |

## API surface

- GET /data/billing_types — **public, no authority of any kind exists for billing**
- _(billing companies have no route at all: the full service stack exists with no controller)_

## Inbound contracts (who would call this service)

| Caller | Purpose | Current mechanism |
|---|---|---|
| `order` | resolve the billing branch by name, and resolve-or-create the invoiced company by identification number | repository-level — **contract needed** |

### Contract this service must expose

```text
resolveBillingType(name)            -> { name, requiresCompany }  or empty
resolveOrCreateCompany(idNumber,name) -> { id, idNumber, name }
   (resolution by idNumber ONLY; an existing company is reused and the
    submitted NAME IS DISCARDED - BR-BILLING-010)
```

## Outbound contracts (what this service would call)

**None.** This service has no dependency on any other business domain.

## Risks and decisions required

1. The individual-versus-enterprise **branch decision currently lives in the order converter**, not here (`BR-BILLING-002`). A split should move it into this domain and expose a `requiresCompany` flag.
2. Billing companies are **write-only from checkout**: no read, correction or delete route exists, so a typo captured at checkout is permanent (`BR-BILLING-014`).
3. No `billing_types:*` or `billing_companies:*` authority code exists anywhere, so billing is outside the role model entirely (`BR-BILLING-019`).
4. Company name carries a unique constraint, so two distinct entities with the same trading name cannot coexist and the failure surfaces mid-checkout (`BR-BILLING-009`).

## Extraction readiness

**Medium.** Internally simple, but the behaviour that matters is implemented in `order` and must be moved before the boundary means anything.

## Cross-references

- behavioural detail: `../detailed-domain-bre/domain-billing/` (19 rules, 10 features)
- boundary justification: `bounded-contexts.md`
- table ownership: `data-ownership.md`
- translation contracts: `anti-corruption-layers.md`
- extraction sequencing: `execution-order.md`
