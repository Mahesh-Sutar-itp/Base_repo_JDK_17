# Data Ownership

Every table in `../database-documentation/00-global-schema.sql` is assigned to
**exactly one** owning domain. There are no orphans and no shared-kernel
infrastructure tables.

## Ownership map (22 tables, 12 domains)

| Table | Owning domain | Basis | Referenced by other domains |
|---|---|---|---|
| `people` | `person` | aggregate root | `user` (`app_users.person_id`), `customer`, `salesperson` |
| `images` | `image` | aggregate root | `product` (`product_images.image_id`) |
| `product_categories` | `product-category` | aggregate root | `product` (`products.product_category_id`) |
| `billing_types` | `billing` | aggregate root | `order` (`orders.billing_type_id`) |
| `billing_companies` | `billing` | same aggregate decision | `order` (`orders.billing_company_id`) |
| `shippers` | `shipper` | aggregate root | `order` (`orders.shipper_id`) |
| `app_params` | `company-profile` | aggregate root | — none |
| `app_users` | `user` | aggregate root | — none |
| `app_user_roles` | `user` | same aggregate | — none |
| `app_permissions` | `user` | same aggregate | — none |
| `app_user_role_permissions` | `user` | join entity inside the aggregate | — none |
| `customers` | `customer` | aggregate root | `order` (`orders.customer_id`) |
| `salespeople` | `salesperson` | aggregate root | `order` (`orders.salesperson_id`, never populated) |
| `products` | `product` | aggregate root | `order` (`order_details.product_id`), `product-list` (`product_list_items.product_id`) |
| `product_images` | `product` | join entity inside the aggregate | — none |
| `product_lists` | `product-list` | aggregate root | — none |
| `product_list_items` | `product-list` | join entity inside the aggregate | — none |
| `orders` | `order` | aggregate root | — none |
| `order_details` | `order` | join/child entity inside the aggregate | — none |
| `order_statuses` | `order` | operational ownership | — none |
| `addresses` | `order` | operational ownership | — none |
| `payment_types` | `order` | operational ownership | — none |

### Count verification

```
person 1 + image 1 + product-category 1 + billing 2 + shipper 1
+ company-profile 1 + user 4 + customer 1 + salesperson 1
+ product 2 + product-list 2 + order 5
= 22 tables
```

Matches `grep -c "^CREATE TABLE" ../database-documentation/00-global-schema.sql`.

### Shared-kernel tables

**None.** Every table belongs to a business domain. There is no scheduler table,
no framework metadata table and no generic entity-attribute-value framework
table. (`app_params` *is* entity-attribute-value shaped, but it holds storefront
branding read by one business service, so it is owned by `company-profile` rather
than treated as infrastructure.) The manifest's
`shared_kernel_components[].tables[]` arrays are therefore empty.

---

## Ownership conflicts and their resolutions

Five tables had a plausible second claimant. Each is resolved below using the
**operational ownership** rule: the owner is the primary reader and actor.

### Conflict 1 — `addresses`

| Claimant | Argument |
|---|---|
| `order` (**chosen**) | rows are created **only** during checkout, by the order converter; both referencing columns are on `orders`; both are mapped not-updatable; the rows are read only through an order |
| a standalone `address` domain | the table has its own entity, converter and a six-field repository lookup |
| `billing` | the enterprise-invoice branch creates the billing address |
| `customer` | addresses are conceptually customer data |

**Resolution:** `order`. The table has **no controller, no crud service, no patch
service and no predicate service** (`BR-ORDER-058`), so a standalone domain would
fail the phantom-domain guard on the service-implementation test. `billing`
creates billing addresses but only as a step inside order creation, and
`customer` never touches the table at all — addresses are not linked to a
customer in the schema.

**Consequence to record:** addresses are immutable once referenced
(`BR-ORDER-057`) and cannot be listed, searched or corrected. A rewrite inherits
two tables with no management capability.

### Conflict 2 — `payment_types`

| Claimant | Argument |
|---|---|
| `order` (**chosen**) | read by the order converter and the order patch service, and by nothing else; `orders.payment_type_id` is mandatory and not updatable |
| `billing` | payment is conceptually adjacent to billing |
| a standalone `payment` domain | there is a payment adapter package |

**Resolution:** `order`. The table has no controller and no crud service, so a
standalone domain fails the guard. `billing` never reads it. The payment adapter
package contains no entity and no table — it is an outbound integration, not a
data owner.

### Conflict 3 — `order_statuses`

| Claimant | Argument |
|---|---|
| `order` (**chosen**) | the vocabulary of the order state machine; read by the process service, the converter and the patch service, all in this domain |
| a standalone `order-status` domain | it has a crud service, a converter, a patch service, a predicate service, a sort spec and a read controller |

**Resolution:** `order`. This is the closest call in the decomposition — the
candidate *would* pass the phantom-domain guard. It is rejected because the status
rows have no meaning outside the order aggregate's invariants: the transition
guards compare status **names** (`BR-ORDER-020`), the edit lock compares status
**codes** (`BR-ORDER-029`), and both live in the order domain. Splitting the
vocabulary from the machine that interprets it would create a context whose data
has no independent invariant to protect. The read-only controller is retained as
an order-domain endpoint.

### Conflict 4 — `app_permissions`

| Claimant | Argument |
|---|---|
| `user` (**chosen**) | the authority vocabulary, resolved transactionally with roles and grants at login |
| every other domain | each enforces authority codes that name *its* resources |

**Resolution:** `user`. Although the codes name other domains' resources, the
rows are written only by the bootstrap seed and read only by the identity
context's authority-resolution service. The other domains consume **string
literals**, not rows — a design-level coupling with no data access
(`BR-USER-031`). A rewrite should publish the catalogue as a shared versioned
artefact; that does not change the data owner.

### Conflict 5 — `people`

| Claimant | Argument |
|---|---|
| `person` (**chosen**) | master-data context whose uniqueness invariant three other contexts depend on |
| `user` | registration and profile editing write person rows |
| `customer` / `salesperson` | both cascade-create person rows |

**Resolution:** `person`. All three claimants *write* person rows, but none
governs the lifecycle: a person row survives the deletion of the account,
customer or salesperson that created it (`BR-USER-048`, `BR-CUSTOMER-014`,
`BR-SALESPERSON-018`). The writes are recorded as outbound couplings in
`anti-corruption-layers.md`.

---

## Cross-boundary foreign keys

These are the constraints that will stop working if the data is physically split.
Each needs an explicit application-level replacement.

| Foreign key | From → to | Rule today | Replacement needed |
|---|---|---|---|
| `FK_USER_PERSON_ID` | `app_users` → `people` | nullable, no cascade; **no unique constraint**, so the one-person-one-account rule is application-only (`BR-USER-004`) | cross-service uniqueness check with an explicit race policy |
| `FK_CUSTOM_PERSON_ID` | `customers` → `people` | **unique**, mandatory, cascade-all on write | distributed create or a saga; the atomicity in `BR-CUSTOMER-003` is lost |
| `FK_SPERSON_PERSON_ID` | `salespeople` → `people` | mandatory, **not unique** (`BR-SALESPERSON-002`) | decide the invariant before splitting; duplicates may already exist |
| `FK_PRODUCT_CAT_ID` | `products` → `product_categories` | nullable, no cascade; **blocks category deletion** (`BR-PRODCAT-016`) | the unused `orphanizeByCategories` operation becomes the real mechanism |
| `FK_PIMGS_IMAGE_ID` | `product_images` → `images` | no cascade; **blocks image deletion** (`BR-IMAGE-014`) | explicit pre-delete reference check |
| `FK_PLIST_ITEM_PROD_ID` | `product_list_items` → `products` | **`ON DELETE CASCADE`** — the only cascade in the schema (`BR-PRODLIST-023`) | the unused `deleteByProductId` operation, driven by a product-deleted notification that does not exist today |
| `FK_ORDER_DETAIL_PROD_ID` | `order_details` → `products` | **`ON DELETE SET NULL`** — history survives (`BR-PRODUCT-029`) | explicit null-out, or keep the denormalised description and drop the reference entirely |
| `FK_ORDER_CUSTOM_ID` | `orders` → `customers` | mandatory, no cascade; **makes any buyer with history undeletable** (`BR-CUSTOMER-015`) | cross-service deletion check |
| `FK_ORDER_BILL_TYP_ID` | `orders` → `billing_types` | mandatory, no cascade | reference by code, with a validation call |
| `FK_ORDER_BILL_CY_ID` | `orders` → `billing_companies` | nullable, no cascade | reference by identification number |
| `FK_ORDER_SHIPPER_ID` | `orders` → `shippers` | nullable, no cascade; **blocks carrier deletion** (`BR-SHIPPER-013`) | reference by name, with a validation call |
| `FK_ORDER_SPERSON` | `orders` → `salespeople` | nullable; **never populated** (`BR-SALESPERSON-015`) | none needed unless attribution is implemented |

Internal foreign keys (`FK_PROD_CAT_PARENT`, `FK_PIMGS_PROD_ID`,
`FK_PLIST_ITEM_PARENT_ID`, `FK_ORDER_DETAIL_PARENT_ID`, `FK_ORDER_STATUS_ID`,
`FK_ORDER_ADDR_BILL`, `FK_ORDER_ADDR_SHIP`, `FK_ORDER_PAYMT_TYP_ID`,
`FK_USER_UROLE_ID`, `FK_UROLEPERMS_*`) stay within one domain and survive a
split unchanged.

Note that `FK_ORDER_DETAIL_PARENT_ID` references a table named `sales` in the
source dump, which does not exist — a stale name recorded as a TODO in the global
schema file rather than silently corrected.

## Orphan tables

**None.** Every table has exactly one owner, so criterion S2-23 has nothing to
justify.
