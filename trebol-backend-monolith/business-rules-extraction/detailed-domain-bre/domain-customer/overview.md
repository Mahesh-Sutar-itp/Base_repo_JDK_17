# Domain: Customer (`customer`)

## Responsibility

Owns the buyer role: the thin record that marks a person as someone who can place
orders. A customer row carries **no attributes of its own** — only a surrogate key
and a mandatory, unique reference to a person. All customer-facing data (names,
identification number, e-mail, phones) lives in the `person` domain and is
surfaced through this domain's API as if it belonged to the customer.

## Bounded context summary

Buyer-role context. Single aggregate root (`Customer`) that transactionally
governs its person reference through a full-cascade association: creating a
customer creates or reuses a person in the same transaction. Kept separate from
`person` because the buyer role has its own authority set, its own REST surface
and its own uniqueness invariant (one customer per person), and separate from
`order` because customers exist and are maintained independently of any order.

## Owned data

| Table | Entity | Source file |
|---|---|---|
| `customers` | `Customer` | `jpa/entities/Customer.java` |

### `Customer` fields

| Field | Column | Nullable | Unique | Mapping |
|---|---|---|---|---|
| `id` | `customer_id` | no | — | identity-generated |
| `person` | `person_id` | no | **yes** | one-to-one, mandatory, full cascade, eager |

The uniqueness constraint `UK_CUSTOM_PERSON_ID` plus the one-to-one mapping is
what guarantees one customer per person. Compare `salesperson`, which uses a
many-to-one mapping with no unique constraint — the two roles are deliberately
asymmetric.

## The transport-model substitution

This is the most surprising structural fact in the domain: the customer CRUD
stack is typed on **`PersonPojo`**, not on a customer-specific model. There is no
`CustomerPojo` anywhere in the codebase. Consequences:

- `GET /data/customers` returns what looks like a page of people.
- `POST /data/customers` accepts a person payload and creates both rows.
- The customer surrogate key is never exposed in a response body.
- Filtering and sorting keys are person attributes, reached by traversing the
  association.

## Components

| Role | Component | File |
|---|---|---|
| controller | `DataCustomersController` | `api/controllers/DataCustomersController.java` |
| crud service | `CustomersCrudServiceImpl` | `jpa/services/crud/impl/CustomersCrudServiceImpl.java` |
| converter | `CustomersConverterServiceImpl` | `jpa/services/conversion/impl/CustomersConverterServiceImpl.java` |
| patch service | `CustomersPatchServiceImpl` | `jpa/services/patch/impl/CustomersPatchServiceImpl.java` |
| predicate service | `CustomersPredicateServiceImpl` | `jpa/services/predicates/impl/CustomersPredicateServiceImpl.java` |
| sort spec | `CustomersSortSpec` | `jpa/sortspecs/CustomersSortSpec.java` |
| repository | `CustomersRepository` | `jpa/repositories/CustomersRepository.java` |
| transport model | `PersonPojo` (shared with `person`) | `api/models/PersonPojo.java` |

`CustomersRepository` declares one custom finder, a join-fetch lookup by the
person's identification number.

## Dependencies

- **Depends on**: `person` — every customer row requires a person row, created or
  reused through the cascading association.
- **Depended on by**: `order` (every order must reference a customer), `user`
  (registration creates a customer; guest authentication creates one before
  issuing a token).

## Shared entity status

`Customer` is a **shared entity**: owned by `customer`, referenced by `order`
through the mandatory `orders.customer_id`. `Person` (owned by `person`) is used
here. See `cross-cutting-documentation/shared-entities.md`.

## Exposure

`/data/customers` exposes read, create, replace and delete behind class-level
authentication and the `customers:*` authorities. There is **no
partial-update route**: `CustomersPatchServiceImpl` is implemented and wired as a
bean but no `PATCH` mapping exposes it. The same is true of `salespeople`,
`users` and `user_roles` — the four person-and-account domains are exactly the
ones that omit `PATCH`, while `images`, `products`, `product_lists`,
`product_categories`, `shippers` and `orders` all expose it.

Seeded authority holders: Administrator holds all four `customers:*` codes;
Manager holds only `customers:read`; Salesperson holds only `customers:read`;
the Customer role holds none.
