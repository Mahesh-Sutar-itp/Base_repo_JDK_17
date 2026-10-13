# Integrations — Person Registry (`person`)

## External systems

**None.** This domain makes no outbound network call. It does not publish events,
consume messages, read files or call any third-party API.

## Inbound integrations (who calls into this domain)

| Caller domain | Entry point | Purpose |
|---|---|---|
| `user` | `PeopleCrudService.getExisting`, `PeopleRepository.findByIdNumber`, `PeoplePatchService.patchExistingEntity` | registration de-duplication, profile attach, profile patch |
| `customer` | `Customer.person` cascade + `CustomersRepository.findByPersonIdNumber` | creating a customer creates or reuses a person |
| `salesperson` | `Salesperson.person` cascade + `SalespeopleRepository.findByPersonIdNumber` | creating a salesperson creates or reuses a person |

Both customer and salesperson creation cascade persistence to the person row
(`cascade = ALL` on the association), so the person is written as part of the same
transaction as the owning aggregate.

## Outbound integrations (what this domain calls)

| Target | Nature |
|---|---|
| shared pagination service | page index/size resolution |
| shared sort-spec parser | `sortBy` / `order` resolution |
| shared persistence abstraction | paged + predicate query execution |

All three are shared-kernel components, not other business domains.

## Events

**None.** The codebase publishes no application events and registers no
listeners; person creation and mutation are silent. See
`cross-cutting-documentation/event-publishing-patterns.md`.

## Scheduled work

**None.** No scheduled, cron or asynchronous task touches `people`.

## Anti-corruption surface

The transport model `PersonPojo` is decoupled from the `Person` entity by a
dedicated converter and patch service, so the REST contract and the table can
evolve independently. The `person.` key prefix used by customer and salesperson
patch payloads is the translation convention between the two contexts; see
`domain-decomposition-overview/anti-corruption-layers.md`.
