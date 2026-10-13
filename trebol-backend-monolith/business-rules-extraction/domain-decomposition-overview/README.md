# Domain Decomposition Overview — README

This directory is the **structural** half of the analysis: where the boundaries
are, who owns which data, what is shared, where the translation layers are, and
in what order the domains can be extracted. The **behavioural** half — features,
rules, workflows, validations — lives in `../detailed-domain-bre/`.

## Contents

| File | Purpose |
|---|---|
| `README.md` | this file |
| `domain-manifest.json` | the machine-readable contract; the only structured handoff artefact |
| `bounded-contexts.md` | the twelve contexts, the boundary rule applied, and the evidence for each boundary |
| `data-ownership.md` | every table mapped to exactly one owning domain, with the conflict resolutions |
| `shared-kernel.md` | the framework and utility components used by more than one domain |
| `anti-corruption-layers.md` | the existing translation surfaces and the contracts a split would need |
| `execution-order.md` | the recommended extraction sequence and its dependency justification |
| `service-{slug}.md` | one per domain: proposed service boundary, owned data, contracts, risks |

## The twelve domains

| # | Slug | Name | Complexity | Tables | Files | LOC |
|---|---|---|---|---|---|---|
| 1 | `person` | Person Registry | small | 1 | 14 | 755 |
| 2 | `image` | Image Registry | small | 1 | 13 | 691 |
| 3 | `product-category` | Product Category Tree | medium | 1 | 14 | 903 |
| 4 | `billing` | Billing | small | 2 | 24 | 1,095 |
| 5 | `shipper` | Shipper | small | 1 | 13 | 648 |
| 6 | `company-profile` | Company Profile | small | 1 | 5 | 249 |
| 7 | `user` | Identity & Access | large | 4 | 56 | 3,404 |
| 8 | `customer` | Customer | small | 1 | 12 | 657 |
| 9 | `salesperson` | Salesperson | small | 1 | 12 | 664 |
| 10 | `product` | Product Catalog | medium | 2 | 16 | 1,120 |
| 11 | `product-list` | Product List | medium | 2 | 21 | 1,286 |
| 12 | `order` | Order, Checkout & Fulfilment | large | 5 | 58 | 4,243 |

Plus 36 shared-kernel files totalling 2,085 lines. All 294 main source files and
all 22 tables are accounted for: see `data-ownership.md` and `shared-kernel.md`.

The numbered order above **is** the recommended extraction order.

## How the boundaries were drawn

The default rule applied throughout: **split into separate domains unless the
concepts share a single aggregate root** — one entity whose lifecycle
transactionally governs the others. Concepts that merely reference each other
across a boundary stay separate; concepts mutated together within one aggregate's
invariants merge.

Applied consequences:

- explicit join entities stay with their governing aggregate
  (`ProductImage` → `product`, `ProductListItem` → `product-list`,
  `UserRolePermission` → `user`, `OrderDetail` → `order`);
- the four identity tables merge into one context because effective authorities
  are derived transactionally from account → role → grant → authority;
- `product`, `product-category` and `product-list` stay separate because each has
  its own aggregate root and its own invariants, and they only reference each
  other;
- `customer` and `salesperson` stay separate from `person` and from each other
  because each has its own uniqueness invariant, authority set and REST surface.

Full reasoning per boundary is in `bounded-contexts.md`.

## Phantom-domain guard

Every domain above has at least one service implementation **and** at least one
owned table. Two plausible-looking candidates were deliberately **not** promoted
to domains:

| Rejected candidate | Reason |
|---|---|
| `checkout` | owns no table; it is the orchestration surface of `order` and is documented as features `F-001` … `F-005` there |
| `mailing` | owns no table; it is an outbound integration of `order` and is documented in that domain's integrations file and in `../reference-documentation/external-integrations-map.md` |

Likewise `payment` is an adapter inside `order`, not a domain.

## What this decomposition is for

The twelve boundaries are an **extraction plan**, not a prescription to build
twelve services. Several of the small domains (`person`, `customer`,
`salesperson`; or `product`, `product-category`, `product-list`) are reasonable
candidates to deploy together while keeping the module boundaries internal. The
value of the decomposition is that it identifies:

1. which data each piece owns exclusively;
2. which cross-boundary reads already go through a service interface and will
   survive a split unchanged;
3. which cross-boundary reads go through a repository and need a new contract;
4. which database constraints and cascades will stop working once the data is
   split.

Items 3 and 4 are the actual work. They are enumerated in
`anti-corruption-layers.md` and `../reference-documentation/coupling-hotspots.md`.

## Key structural findings

- **Only two domains orchestrate.** `order` and `user` coordinate other domains;
  the remaining ten only participate. This is why they extract last.
- **`order` is a pure consumer.** No domain reads orders, order lines, order
  statuses, addresses or payment types, so it can be extracted with zero impact
  on anything else.
- **Four of `order`'s five domain dependencies are repository-level**, which is
  the single largest body of extraction work in the system.
- **The security kernel depends outward on business domains.** `user` writes
  `person` and `customer` rows during registration and guest authentication —
  the reverse of the usual direction, and the main obstacle to extracting
  identity.
- **Three cross-boundary reads are already clean service calls** and are the
  pattern to copy: `product` → `product-category`, `product-list` → `product`,
  and `order` → `customer`.
- **`company-profile` is completely isolated** — one table, no foreign key in
  either direction, no consumer. It is the lowest-risk extraction available.
- **No events, no caching, no scheduled work exist anywhere**, so any
  split-related eventual consistency has no existing infrastructure to build on.
