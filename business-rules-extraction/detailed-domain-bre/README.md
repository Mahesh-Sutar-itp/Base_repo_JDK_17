# Detailed Domain BRE — README

This directory holds the per-domain business-rules extraction for the Trébol
backend monolith. It is the **behavioural** half of the analysis; the
**structural** half (boundaries, ownership, extraction order) lives in
`../domain-decomposition-overview/`.

## How this directory is organised

```
detailed-domain-bre/
├── README.md                  (this file)
├── cross-domain-features.md   features whose behaviour spans two or more domains
├── requirements-summary.md     consolidated counts and the system-wide findings
├── traceability-matrix.md      rule and feature IDs mapped to source files
└── domain-{slug}/             one directory per domain, 12 in total
    ├── overview.md            responsibility, bounded context, owned data, components
    ├── features.md            F-NNN headings — what the domain does
    ├── business-rules.md      BR-{DOMAIN}-NNN headings — testable assertions with citations
    ├── workflows.md           step-by-step flows and the domain's state model
    ├── validations.md         transport / persistence / service validation and the gaps
    ├── integrations.md        inbound, outbound, events, scheduled work, caching
    └── diagrams/
        ├── sequence-diagram.md    primary flows as Mermaid sequence diagrams
        ├── component-diagram.md   internal components and cross-domain edges
        └── workflow-diagram.md    decision flows and state progressions
```

## The twelve domains

| Slug | Name | Complexity | Owned tables | Rules | Features |
|---|---|---|---|---|---|
| `person` | Person Registry | small | 1 | 13 | 7 |
| `image` | Image Registry | small | 1 | 15 | 8 |
| `product-category` | Product Category Tree | medium | 1 | 20 | 14 |
| `billing` | Billing | small | 2 | 19 | 10 |
| `shipper` | Shipper | small | 1 | 15 | 9 |
| `company-profile` | Company Profile | small | 1 | 13 | 5 |
| `user` | Identity & Access | large | 4 | 55 | 15 |
| `customer` | Customer | small | 1 | 22 | 11 |
| `salesperson` | Salesperson | small | 1 | 23 | 8 |
| `product` | Product Catalog | medium | 2 | 32 | 13 |
| `product-list` | Product List | medium | 2 | 30 | 16 |
| `order` | Order, Checkout & Fulfilment | large | 5 | 70 | 23 |

Twelve domains own all 22 tables with no overlap and no orphans. The table order
above is the recommended extraction order; see
`../domain-decomposition-overview/execution-order.md`.

## Identifier conventions

| Prefix | Meaning | Example |
|---|---|---|
| `F-NNN` | a feature, numbered within its domain | `F-001` in `domain-order/features.md` |
| `BR-{DOMAIN}-NNN` | a business rule, numbered within its domain | `BR-ORDER-014` |
| `W-{DOMAIN}-NN` | a workflow | `W-ORDER-03` |

Domain tokens in rule identifiers are upper-case and abbreviated where the slug
is long: `PRODCAT` for `product-category`, `PRODLIST` for `product-list`,
`COMPANY` for `company-profile`.

## How to read a domain

1. **`overview.md`** first — it states the responsibility, the aggregate root,
   the owned tables and every component by file path.
2. **`features.md`** to see the capability inventory, including the explicit
   "deliberately absent" tables that record what the domain does **not** do.
3. **`business-rules.md`** for the testable assertions. Every rule cites the file
   that enforces it and quotes real literals (status names, error messages,
   constants) so a rewrite can be verified against them.
4. **`workflows.md`** for the ordered steps and the state model.
5. **`validations.md`** for the validation layers and, importantly, the gaps.
6. **`integrations.md`** for coupling, and for the documented absence of events,
   scheduled work and caching.
7. **`diagrams/`** for the same information in visual form.

## Evidence standard applied

- Every rule is grounded in a specific source file. Where a literal matters
  (an error message, a status name, a tax rate, a configuration key) the literal
  is quoted.
- **Absence is documented as a finding**, not omitted. The system has no events,
  no caching, no scheduled work, no stock control and no password change; each
  absence is stated explicitly in the relevant domain.
- **Defects are recorded as observed behaviour**, not silently corrected. Where a
  message is mis-worded or truncated, or a guard has a hole, the rule states what
  the code does today and flags it.
- **Unreachable code is distinguished from missing code.** Several domains have
  fully implemented services with no route exposing them; those are recorded as
  "wired but unreachable" rather than as features.

## Cross-references

| For | See |
|---|---|
| the full table schema and seed data | `../database-documentation/` |
| boundaries, ownership and extraction order | `../domain-decomposition-overview/` |
| authentication, authorization, error handling, shared entities | `../cross-cutting-documentation/` |
| the endpoint catalogue, service inventory, integrations map, coupling, dead code | `../reference-documentation/` |
| system-wide architecture, sequence and entity-relationship diagrams | `../diagrams/` |
| the interactive summary | `../analysis-dashboard.html` |
