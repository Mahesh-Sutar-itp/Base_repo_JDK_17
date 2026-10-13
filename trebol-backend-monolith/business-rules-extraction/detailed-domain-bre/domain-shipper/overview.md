# Domain: Shipper (`shipper`)

## Responsibility

Owns the catalogue of delivery carriers. A shipper is a name and nothing else —
there is no contact detail, no rate card, no service area and no carrier API
integration. The sole business purpose is to let an order record *which* carrier
is responsible for delivery.

## Bounded context summary

Minimal reference-data context. Single invariant: carrier names are unique. It
exists as its own context rather than being folded into `order` because shipper
rows have a lifecycle entirely independent of any order (they are created and
maintained by operators before any order references them) and because the `order`
domain only ever reads them by name.

## Owned data

| Table | Entity | Source file |
|---|---|---|
| `shippers` | `Shipper` | `src/main/java/org/trebol/jpa/entities/Shipper.java` |

### `Shipper` fields

| Field | Column | Nullable | Unique | Size constraint |
|---|---|---|---|---|
| `id` | `shipper_id` | no | — | — |
| `name` | `shipper_name` | no | yes | **none declared** |

`shipper_name` is `varchar(255)` in the DDL. `Shipper.name` is the only business
column in the codebase with a uniqueness constraint but **no** `@Size`
annotation, so the 255-character ceiling is enforced only by the database.

## Components

| Role | Component | File |
|---|---|---|
| controller | `DataShippersController` | `api/controllers/DataShippersController.java` |
| crud service | `ShippersCrudServiceImpl` | `jpa/services/crud/impl/ShippersCrudServiceImpl.java` |
| converter | `ShippersConverterServiceImpl` | `jpa/services/conversion/impl/ShippersConverterServiceImpl.java` |
| patch service | `ShippersPatchServiceImpl` | `jpa/services/patch/impl/ShippersPatchServiceImpl.java` |
| predicate service | `ShippersPredicateServiceImpl` | `jpa/services/predicates/impl/ShippersPredicateServiceImpl.java` |
| sort spec | `ShippersSortSpec` | `jpa/sortspecs/ShippersSortSpec.java` |
| repository | `ShippersRepository` | `jpa/repositories/ShippersRepository.java` |
| transport model | `ShipperPojo` | `api/models/ShipperPojo.java` |

## Dependencies

- **Depends on**: nothing.
- **Depended on by**: `order`. The order converter resolves a shipper by name
  when building a new order, and the order patch service re-points an existing
  order at a different shipper by name.

## Shared entity status

`Shipper` is a **shared entity**: owned by `shipper`, referenced by `order`
through the nullable `orders.shipper_id` column.
See `cross-cutting-documentation/shared-entities.md`.

## Authority asymmetry worth noting

`GET /data/shippers` carries **no** authority requirement and the controller has
**no** class-level authentication requirement, so the carrier list is publicly
readable. The write verbs do require `shippers:create` / `shippers:update` /
`shippers:delete`. The seeded `shippers:read` authority is granted to
Administrator and Manager but is never checked by any route.
