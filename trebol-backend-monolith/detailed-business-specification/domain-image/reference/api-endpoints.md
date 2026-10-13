# Image Registry — API Endpoint Inventory

System-wide route catalog: `reference-documentation/api-endpoint-catalog.md`. Full request
and response contracts: `specs/05-api-contract.md`.

## Endpoints owned by this domain

All five share the collection path `/data/images`. There is **no path-identifier route
form** — writes select their target through query parameters (BR-IMAGE-021).

| Method | Path | Auth | Authority | Success | Summary | Handler |
|---|---|---|---|---|---|---|
| `GET` | `/data/images` | authenticated | `images:read` | 200 | List image links data, with filtering, sorting and pagination. | `DataImagesController.readMany` |
| `POST` | `/data/images` | authenticated | `images:create` | **201**, empty body | Define new image links. | `DataImagesController.create` |
| `PUT` | `/data/images` | authenticated | `images:update` | **204** | Replace image links data. Destructive full replacement. | `DataImagesController.update` |
| `PATCH` | `/data/images` | authenticated | `images:update` | **204** | Update parts of image links data. | `DataImagesController.partialUpdate` |
| `DELETE` | `/data/images` | authenticated | `images:delete` | **204** | Remove image links. **Unbounded** — see below. | `DataImagesController.delete` |

Documentation tag: `Images management`.

## Operational notes a route-level reader needs

| Note | Detail |
|---|---|
| **No single-fetch route** | `readOne` exists on the service layer but no endpoint calls it. To fetch one image, use `GET` with an identity selector. |
| **No `url` selector** | Images cannot be addressed by URL for any operation, despite `url` being unique and sortable (BR-IMAGE-019). |
| **Writes are query-addressed** | `PUT`, `PATCH` and `DELETE` use the same predicate as `GET`, so its short-circuiting and unescaped-metacharacter behaviour applies to writes (BR-IMAGE-008, BR-IMAGE-011). |
| **`PUT`/`PATCH` reject an empty selector; `DELETE` does not** | 400 `"Missing request params"` versus a silent 204 no-op (BR-IMAGE-022). |
| **`DELETE` with a mistyped selector deletes everything** | The map is non-empty so the guard passes, nothing is recognised so the predicate matches all rows (BR-IMAGE-013). Highest-severity finding in the domain. |
| **`POST` returns no body and no identifier** | A creator cannot learn what it created and must re-query by filename (BR-IMAGE-012). |
| **Duplicate `code` or `url` returns 500, not 409** | The service guard checks filename only (BR-IMAGE-002). |
| **`PUT` and `PATCH` share one authority** | No separate replace-versus-patch privilege. |

## Routes that write image data but belong to another domain

Listed because they are the only **internal** write path, and a reader looking for how
images are created in practice will otherwise miss them.

| Method | Path | Auth | Owning domain | Effect on `images` |
|---|---|---|---|---|
| product create / update routes | — | the `product` domain's own authorities — **not** `images:create` | `product` | Resolves each payload image by filename; **inserts a new row by association cascade** when unresolved, bypassing this domain's create operation, duplicate guard and route validation. A blank filename silently drops that image. |

Source: `ProductsCrudServiceImpl.makeProductImageRelationships`. Full census in
`specs/06-integration-points.md` §2; authority implications in `permissions.md` §3.
