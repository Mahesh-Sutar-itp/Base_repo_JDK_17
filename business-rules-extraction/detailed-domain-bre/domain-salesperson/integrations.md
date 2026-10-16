# Integrations — Salesperson (`salesperson`)

## External systems

**None.** No human-resources system, no payroll integration, no identity
provider, no commission engine and no directory synchronisation. A salesperson
record is a local row and nothing else.

## Inbound integrations

| Caller domain | Entry point | Purpose | Status |
|---|---|---|---|
| `order` | `Order.salesperson` association with a persist/merge cascade | credit an order to a salesperson | **unreachable** — no route populates the field |

This is the only inbound edge, and it is dead at the API boundary
(BR-SALESPERSON-015). In a shipped deployment **no component calls into this
domain at all**: no service, no repository, no entity reference is ever resolved.

That makes `salesperson` the only domain in the decomposition whose consumers are
entirely hypothetical, which has two consequences for a rewrite:

1. Extraction is risk-free from a coupling standpoint — nothing depends on it
   today.
2. The attribution feature it exists to serve is **not** a port; it is new work.

## Outbound integrations

| Target domain | Entry point | Purpose |
|---|---|---|
| `person` | cascading many-to-one association, `PeoplePatchService` via the (unreachable) salesperson patch service | create or reuse the person behind a salesperson |

| Shared-kernel target | Nature |
|---|---|
| generic CRUD base | create/read/replace/delete behaviour |
| generic controller bases | the data controller |
| pagination service, sort-spec parser | listing behaviour |
| prefix-stripping utility | nested `person.` key handling in the unreachable patch path |
| persistence abstraction | paged + predicate query execution |

Note the salesperson patch service hardcodes the `person.` prefix literal rather
than referencing the shared constant that the customer patch service uses
(BR-SALESPERSON-012).

## Events

**None.** Creating, replacing or deleting a salesperson publishes nothing.

## Scheduled work

**None.**

## Caching

**None.**

## Relationship to the access-control model (important non-integration)

There is no integration between this domain and `user`, despite the naming
overlap:

```text
app_user_roles row "Salesperson"   -> a ROLE in the user domain
app_users row     "salesperson"    -> a seeded ACCOUNT in the user domain
salespeople row                    -> a RECORD in this domain

No foreign key, no service call and no code path connects the three.
```

Asserting: granting the Salesperson role does not create a salesperson record,
and creating a salesperson record grants no access. The seeded Salesperson role's
`salespeople:read` authority lets such an account *list* salesperson records, but
it cannot identify *which* record is its own, because nothing links an account to
a salesperson row.

A rewrite intending real sales attribution must introduce that link. It is absent
today.

## Anti-corruption surface

Weak, for the same reason as `customer`: the domain has no transport model of its
own and re-uses the person model, so there is no translation layer isolating the
salesperson concept from the person concept. Extracting the domain as-is would
publish the person schema as part of the salesperson contract.

The latent cascade from the order entity is the other concern — it would let an
external aggregate write into this domain's table without passing its service
layer. See `domain-decomposition-overview/anti-corruption-layers.md`.
