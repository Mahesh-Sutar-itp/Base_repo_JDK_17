# Shared Entities

An entity is **shared** when more than one domain reads or writes it. Nine of the
22 entities qualify. Each has exactly one owning domain; the others reference it.

Anchors in this file are the canonical specification targets referenced by
`../domain-decomposition-overview/domain-manifest.json`
(`shared_entities[].canonical_spec_anchor`).

---

## Person {#person}

| Property | Value |
|---|---|
| Table | `people` |
| Source | `jpa/entities/Person.java` |
| Owning domain | `person` |
| Used by | `person`, `user`, `customer`, `salesperson` |

**Why shared:** three other domains hold a foreign key to it and all three
*write* person rows — `user` during registration and profile editing, `customer`
and `salesperson` through cascading associations.

**Referencing columns:** `app_users.person_id` (nullable, **not unique**),
`customers.person_id` (mandatory, **unique**), `salespeople.person_id`
(mandatory, **not unique**).

**Invariant:** one person per national identification number, database-enforced.

**Consumer obligations:** the one-person-one-account rule is enforced **only in
application code** with no constraint behind it (`BR-USER-004`); the
one-customer-per-person rule **is** database-enforced; the salesperson equivalent
is **not** enforced at all (`BR-SALESPERSON-002`). Three similar rules, three
different enforcement levels — resolve deliberately.

**Lifecycle note:** no code path deletes a person row, and no consumer cascades
removal, so deleting an account, customer or salesperson leaves an orphan.

---

## Customer {#customer}

| Property | Value |
|---|---|
| Table | `customers` |
| Source | `jpa/entities/Customer.java` |
| Owning domain | `customer` |
| Used by | `customer`, `order` |

**Why shared:** every order must reference a customer
(`orders.customer_id`, mandatory).

**Consumer obligations:** `order` resolves or creates the customer during
checkout through the **service interface** — a clean edge. Because the reference
is mandatory with no cascade, any buyer with order history is permanently
undeletable (`BR-CUSTOMER-015`).

**Note:** the domain publishes `PersonPojo` as its own transport model, so the
customer identifier is never exposed on the wire (`BR-CUSTOMER-008`).

---

## Salesperson {#salesperson}

| Property | Value |
|---|---|
| Table | `salespeople` |
| Source | `jpa/entities/Salesperson.java` |
| Owning domain | `salesperson` |
| Used by | `salesperson`, `order` |

**Why shared:** `orders.salesperson_id` exists (nullable) with a persist/merge
cascade on the association.

**Consumer obligations:** none in practice — **no route ever populates the
column** (`BR-SALESPERSON-015`), so the sharing is modelled and dead. The cascade
is a latent validation bypass if attribution is ever implemented
(`BR-SALESPERSON-016`).

---

## Product {#product}

| Property | Value |
|---|---|
| Table | `products` |
| Source | `jpa/entities/Product.java` |
| Owning domain | `product` |
| Used by | `product`, `order`, `product-list` |

**Why shared:** order lines and product-list membership both reference products.

**Referencing columns:** `order_details.product_id`
(**`ON DELETE SET NULL`**), `product_list_items.product_id`
(**`ON DELETE CASCADE`**), `product_images.product_id` (internal, no cascade).

**Consumer obligations:**

- `order` resolves products by barcode through the **repository** (a leak) and
  **copies the current price and the generated description onto the line**, which
  is what makes order history price-stable and survivable across product deletion
  (`BR-ORDER-009`, `BR-ORDER-010`);
- `product-list` resolves through the **service** and silently skips
  non-resolving products (`BR-PRODLIST-015`);
- **neither consumer reads stock.** An out-of-stock product can be ordered and
  paid for (`BR-PRODUCT-022`).

**Three different delete rules** cross this entity's boundary — block, set-null
and cascade. A split must re-express all three and must not unify them.

---

## ProductCategory {#productcategory}

| Property | Value |
|---|---|
| Table | `product_categories` |
| Source | `jpa/entities/ProductCategory.java` |
| Owning domain | `product-category` |
| Used by | `product-category`, `product` |

**Why shared:** `products.product_category_id` (nullable, no cascade).

**Consumer obligations:** `product` consumes the **descendant-resolution service
interface** — the cleanest cross-domain edge in the system — but also holds two
dead repository helpers that reach the entity directly. The foreign key blocks
category deletion, and the operation that would make deletion safe
(`orphanizeByCategories`) is never called (`BR-PRODCAT-016`).

**Also self-referencing:** the parent column makes this the only entity with a
foreign key to its own table, and **cycles are creatable and undetected**
(`BR-PRODCAT-009`).

---

## Image {#image}

| Property | Value |
|---|---|
| Table | `images` |
| Source | `jpa/entities/Image.java` |
| Owning domain | `image` |
| Used by | `image`, `product` |

**Why shared:** `product_images.image_id` (mandatory, no cascade).

**Consumer obligations:** `product` resolves images by filename through the
**service interface**, but fabricates an **unsaved placeholder image** when the
filename does not resolve — so image data can enter the system without passing
the owning domain's validation (`BR-PRODUCT-014`). The foreign key blocks image
deletion with no pre-delete cleanup (`BR-IMAGE-014`).

---

## BillingType {#billingtype}

| Property | Value |
|---|---|
| Table | `billing_types` |
| Source | `jpa/entities/BillingType.java` |
| Owning domain | `billing` |
| Used by | `billing`, `order` |

**Why shared:** `orders.billing_type_id` (mandatory, **not updatable**).

**Consumer obligations:** `order` resolves the type by name through the
**repository** (a leak) and **branches on the literal names** `Bill` and
`Enterprise Invoice` (`BR-BILLING-002`) — so the behaviour that gives this entity
meaning lives in the consumer, not the owner. An unknown name rejects the order.
The billing type on an order is immutable after creation
(`BR-BILLING-004`).

---

## BillingCompany {#billingcompany}

| Property | Value |
|---|---|
| Table | `billing_companies` |
| Source | `jpa/entities/BillingCompany.java` |
| Owning domain | `billing` |
| Used by | `billing`, `order` |

**Why shared:** `orders.billing_company_id` (nullable), populated only on the
enterprise-invoice branch.

**Consumer obligations:** `order` **creates** company rows during checkout
through the repository — the only write path that exists for this entity, since
the domain has no controller (`BR-BILLING-014`). Resolution is by identification
number only, and an existing company is reused with the **submitted name
discarded** (`BR-BILLING-010`).

---

## Shipper {#shipper}

| Property | Value |
|---|---|
| Table | `shippers` |
| Source | `jpa/entities/Shipper.java` |
| Owning domain | `shipper` |
| Used by | `shipper`, `order` |

**Why shared:** `orders.shipper_id` (nullable, **not updatable**).

**Consumer obligations:** `order` resolves the carrier by name through the
**repository** at two call sites, with a deliberate asymmetry: an unknown carrier
**rejects** order creation but is **silently ignored** on an order patch
(`BR-SHIPPER-015`). The foreign key blocks carrier deletion and there is no
disabled flag, so a referenced carrier can never be withdrawn
(`BR-SHIPPER-013`).

---

## Entities that are NOT shared

Thirteen entities are read and written by exactly one domain:

| Entity | Table | Sole domain |
|---|---|---|
| `Param` | `app_params` | `company-profile` |
| `User` | `app_users` | `user` |
| `UserRole` | `app_user_roles` | `user` |
| `Permission` | `app_permissions` | `user` |
| `UserRolePermission` | `app_user_role_permissions` | `user` |
| `ProductImage` | `product_images` | `product` |
| `ProductList` | `product_lists` | `product-list` |
| `ProductListItem` | `product_list_items` | `product-list` |
| `Order` | `orders` | `order` |
| `OrderDetail` | `order_details` | `order` |
| `OrderStatus` | `order_statuses` | `order` |
| `Address` | `addresses` | `order` |
| `PaymentType` | `payment_types` | `order` |

Note that `Permission` is consumed conceptually by every domain, but only as
**string literals** in annotations — no other domain reads the table
(`BR-USER-031`). The sharing is design-level, not data-level, which is why it is
not listed as a shared entity.

## Summary

| Shared entity | Owner | Consumers | Edge style |
|---|---|---|---|
| `Person` | `person` | `user`, `customer`, `salesperson` | repository + ORM cascades |
| `Customer` | `customer` | `order` | **service** |
| `Salesperson` | `salesperson` | `order` | dead (never populated) |
| `Product` | `product` | `order`, `product-list` | repository / **service** |
| `ProductCategory` | `product-category` | `product` | **service** (+ dead repository helpers) |
| `Image` | `image` | `product` | **service** |
| `BillingType` | `billing` | `order` | repository |
| `BillingCompany` | `billing` | `order` | repository |
| `Shipper` | `shipper` | `order` | repository |

Four of the nine are consumed through service interfaces and survive a split
unchanged; four are consumed through repositories and need contracts; one is
dead. See `../domain-decomposition-overview/anti-corruption-layers.md`.
