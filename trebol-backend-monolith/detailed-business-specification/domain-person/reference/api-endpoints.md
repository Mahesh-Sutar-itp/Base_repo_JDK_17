# Person Registry — API Endpoint Inventory

System-wide route catalog: `reference-documentation/api-endpoint-catalog.md`. Full request
and response contracts for the route below: `specs/05-api-contract.md`.

## Endpoints owned by this domain

| Method | Path | Auth | Authority | Summary | Handler |
|---|---|---|---|---|---|
| `GET` | `/data/people` | authenticated | `people:read` | List people, with filtering, sorting and pagination. | `DataPeopleController.readMany` |

**One endpoint. Read-only.** The controller extends the read-only generic data controller,
so no create, update, patch, delete or single-fetch route exists (BR-PERSON-013).

## Routes that write person data but belong to other domains

Listed because they are the only way person records are created or modified today, and a
reader looking for "the person write API" will otherwise find nothing.

| Method | Path | Auth | Owning domain | Effect on `people` |
|---|---|---|---|---|
| `POST` | `/public/register` | **none — unauthenticated** | `user` | Inserts a person row, then a user row, then a customer row, in one transaction. |
| profile update route | — | authenticated account holder | `user` | Creates or links a person when the account has none; **always fails with 500 when the account already has one** (BR-PERSON-027). |
| customer write routes | — | per the `customer` domain | `customer` | Insert person rows by association cascade. |
| salesperson write routes | — | per the `salesperson` domain | `salesperson` | Insert person rows by association cascade. |

See `specs/06-integration-points.md` §2.2 for the exact call sites and
`specs/05-api-contract.md` §5 for what an extraction must add.
