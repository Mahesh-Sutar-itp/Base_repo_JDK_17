# Person Registry — Entity-Relationship Diagram

The domain owns one table and declares **no** relationships of its own. Every association
shown here is held by the far side, in a consumer domain — which is why `people` is a leaf
with three inbound arrows and no outbound ones.

## Owned table in context

```mermaid
erDiagram
    PEOPLE {
        bigint person_id PK "AUTO_INCREMENT"
        varchar_200 person_first_name "NOT NULL, indexed"
        varchar_200 person_last_name "NOT NULL, indexed"
        varchar_20 person_id_number "NOT NULL, UNIQUE, indexed"
        varchar_100 person_email "NOT NULL, indexed"
        varchar_255 person_phone1 "NOT NULL, app default empty"
        varchar_255 person_phone2 "NOT NULL, app default empty"
    }

    APP_USERS {
        bigint user_id PK
        bigint person_id FK "nullable - external, domain user"
    }

    CUSTOMERS {
        bigint customer_id PK
        bigint person_id FK "external, domain customer"
    }

    SALESPEOPLE {
        bigint salesperson_id PK
        bigint person_id FK "NOT NULL - external, domain salesperson"
    }

    PEOPLE ||--o| APP_USERS : "referenced by"
    PEOPLE ||--o| CUSTOMERS : "referenced by"
    PEOPLE ||--o| SALESPEOPLE : "referenced by"
```

`APP_USERS`, `CUSTOMERS` and `SALESPEOPLE` are **external reference nodes** — they are not
owned by this domain and are shown only with the columns needed to express the
relationship. Their authoritative definitions live in the `user`, `customer` and
`salesperson` domain specifications.

## Ownership and cardinality

| Relationship | Cardinality | Owned by | Constraint |
|---|---|---|---|
| `people` → `app_users` | one person to at most one account | `user` | FK on `app_users.person_id` |
| `people` → `customers` | one person to at most one customer | `customer` | FK on `customers.person_id` |
| `people` → `salespeople` | one person to at most one salesperson | `salesperson` | FK `FK_SPERSON_PERSON_ID`, `NOT NULL` |

## Integrity notes

- **No cascade deletes.** No consumer removes a person when its own record is removed, and
  nothing deletes a person directly (BR-PERSON-013). Orphan accumulation is steady-state.
- **The one-person-one-account rule has no database constraint.** None of the three
  `person_id` columns is declared unique, so the database permits two accounts, two
  customers or two salespeople to point at the same person. That the system behaves
  otherwise is an application-level property only.
- **`person_id_number` uniqueness is the only structural invariant** this domain
  contributes, and three domains depend on it.

## Index layout

```mermaid
erDiagram
    PEOPLE_INDEXES {
        string PRIMARY "person_id - unique"
        string UK_PERSON_ID_NO "person_id_number - UNIQUE"
        string IDX_PERSON_ID_NO "person_id_number - redundant with the unique key"
        string IDX_PERSON_FIRSTNAME "person_first_name"
        string IDX_PERSON_LASTNAME "person_last_name"
        string IDX_PERSON_EMAIL "person_email"
    }
```

The four non-unique indexes exist to support the search filters in
`specs/05-api-contract.md` §1.1. Note that the case-insensitive substring filters
(`*Like`) cannot use these indexes for a leading-wildcard pattern, so partial-match
searches scan — relevant if the registry grows large.
