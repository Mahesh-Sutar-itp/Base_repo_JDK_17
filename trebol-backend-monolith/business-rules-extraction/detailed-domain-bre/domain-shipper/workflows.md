# Workflows — Shipper (`shipper`)

## W-SHIPPER-01 List carriers (public)

1. Any caller, authenticated or not, issues `GET /data/shippers`.
2. Page index and size are resolved from the parameter map.
3. If the map is non-empty, `sortBy` is resolved against the single sortable key
   `name`, and `id` / `name` / `nameLike` become filter conditions.
4. A count query and a page query run; each row becomes a shipper transport model.
5. The page envelope is returned.

## W-SHIPPER-02 Register a carrier

1. Operator `POST`s a body carrying the carrier name; authority
   `shippers:create` is required.
2. Transport validation enforces a non-blank name.
3. The service re-checks for blankness, raising the (mis-worded) bad-input error.
4. The name is looked up; a hit raises `The item already exists`.
5. Otherwise the row is converted, saved and flushed, converted back and returned
   with status `201`.

## W-SHIPPER-03 Replace a carrier

1. Operator `PUT`s with a non-empty selecting parameter map; authority
   `shippers:update`.
2. Empty parameters → `Missing request params`.
3. Filter resolved; more than one match → `Cannot update more than one item at a
   time`; zero matches → `No element was found to update`.
4. The body is converted to a new entity, the existing identifier is assigned and
   the row is saved. Since `name` is the only column, replace and patch converge
   in effect.
5. Responds `204`.

## W-SHIPPER-04 Patch a carrier

Same selection and multiplicity rules. The change map's `name` is applied only
when non-blank; an unchanged result issues no write. Responds `204`.

## W-SHIPPER-05 Remove a carrier

1. Operator `DELETE`s with a parameter map; authority `shippers:delete`.
2. Empty map → nothing deleted, success response.
3. Zero matches → `Requested item(s) not found`.
4. Matching rows are deleted. Any row referenced by an order aborts on the
   foreign-key constraint.

## W-SHIPPER-06 Carrier resolution during checkout (driven by `order`)

1. The order converter reads the shipper name from the submitted order.
2. The name is resolved through this domain's repository.
3. Unknown name → the order is rejected with `Specified shipper does not exist`.
4. Known name → the shipper is attached to the new order; the shipping address is
   resolved or created alongside it.

Note: `Order.shipper` is mapped `updatable=false`, so the carrier chosen at
creation cannot be changed through the ordinary replace path — only through the
dedicated patch key.

## W-SHIPPER-07 Carrier reassignment on an existing order (driven by `order`)

1. Operator patches an order with a `shipper` key.
2. The order patch service resolves the name.
3. Found → the order's carrier is replaced.
4. Not found → the key is discarded with no error and no log entry.

## State model

`Shipper` has no status column and no lifecycle states. Rows are created, renamed
in place and hard-deleted. There is no disabled or retired carrier concept, so the
only way to stop using a carrier is to delete it — which is blocked as soon as any
historical order references it (BR-SHIPPER-013). In practice carriers therefore
accumulate permanently.
