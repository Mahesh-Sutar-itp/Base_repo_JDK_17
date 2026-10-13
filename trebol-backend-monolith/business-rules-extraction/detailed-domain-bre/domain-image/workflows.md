# Workflows — Image Registry (`image`)

## W-IMAGE-01 Register an image

1. Caller `POST`s an image payload with code, filename and URL.
2. Transport validation enforces all three as non-blank.
3. The service checks for an existing image **by filename**; if found it raises
   the already-exists error `The item already exists`.
4. The payload is converted to a new entity and saved and flushed.
5. The saved entity is converted back and returned with status `201`.

Residual risk: a duplicate code or URL with a fresh filename slips past step 3
and fails at the database constraint in step 4.

## W-IMAGE-02 Search images

1. Caller `GET`s with an optional parameter map.
2. Page index and size are resolved (defaults `0` and 10, hard cap 1000).
3. When the map is non-empty, `sortBy`/`order` are resolved against the three
   sortable keys and the remaining keys become filter conditions.
4. If `id`, `code` or `filename` is present, that condition alone is used and
   every other filter is discarded.
5. A count query and a page query run; each row is converted to its transport
   model and wrapped in the page envelope.

## W-IMAGE-03 Replace an image

1. Caller `PUT`s with a non-empty parameter map plus a full body.
2. An empty parameter map is rejected with `Missing request params`.
3. The filter is resolved; more than one match raises
   `Cannot update more than one item at a time`; zero matches raises
   `No element was found to update`.
4. The body becomes a new entity, the existing identifier is assigned, and the
   row is saved — a complete overwrite of all three columns.
5. Responds `204`.

## W-IMAGE-04 Patch an image

Same selection and multiplicity rules as W-IMAGE-03. The change map is applied
onto a copy of the existing row, skipping blank values. If the patched copy equals
the original, no write is issued. Responds `204`.

## W-IMAGE-05 Delete images

1. Caller `DELETE`s with a parameter map.
2. Empty map → nothing happens, success response.
3. Non-empty map → filter resolved; zero matches raises
   `Requested item(s) not found`.
4. Every matching row is deleted. Rows still referenced by `product_images`
   abort the transaction on the foreign-key constraint.

## W-IMAGE-06 Image linking during product creation (driven by `product`)

1. The product service iterates the image list on the product payload.
2. For each entry it calls this domain's `getExisting`.
3. A blank filename raises `Invalid filename`; the product service catches that
   bad-input error, logs
   `An image was not linked to product with barcode '{}'` at debug level and
   continues with the next image.
4. When no image matches, the product service builds an unsaved placeholder image
   from the submitted fields rather than failing.

## State model

`Image` has no status column and no lifecycle states. Rows are created, mutated in
place and hard-deleted.
