# Features — Image Registry (`image`)

## F-001 Paged image search

`GET /data/images`, authority `images:read`, class-level authentication required.
Returns a page envelope of image metadata. Pagination, sorting and filtering all
travel in the same query-parameter map.

## F-002 Image filtering

`ImagesPredicateServiceImpl` keys: `id`, `code`, `filename` (all three
short-circuit and discard other filters), plus `codeLike` and `filenameLike` for
partial matching. There is **no** filter on `url`.

## F-003 Image sorting

`ImagesSortSpec` whitelists `code`, `filename` and `url`.

## F-004 Image registration

`POST /data/images`, authority `images:create`, responds `201`. The submitted
code, filename and URL are all mandatory and all must be unique. Attempting to
register an image whose filename already exists is a conflict.

## F-005 Image replacement

`PUT /data/images`, authority `images:update`, responds `204`. The request
parameter map selects the target row; the body fully replaces it. Replacement
semantics are a convert-to-new-entity-then-assign-the-existing-id overwrite, so
any field omitted from the body is overwritten with its default, not preserved.

## F-006 Image partial update

`PATCH /data/images`, authority `images:update`, responds `204`. Accepts a sparse
change map; `code`, `filename` and `url` are applied only when non-blank.

## F-007 Image deletion

`DELETE /data/images`, authority `images:delete`, responds `204`. Requires a
non-empty request parameter map — an empty map performs nothing at all rather
than deleting everything. All rows matching the resolved filter are deleted.

## F-008 Image lookup for product linking (internal)

`ImagesCrudServiceImpl.getExisting` resolves an image by filename. The `product`
domain calls it while linking images to a product; when no image matches, the
product domain fabricates an unsaved placeholder image instead of failing.
