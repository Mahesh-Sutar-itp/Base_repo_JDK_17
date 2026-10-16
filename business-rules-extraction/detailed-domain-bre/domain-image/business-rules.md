# Business Rules — Image Registry (`image`)

## BR-IMAGE-001 Code, filename and URL are each globally unique

All three columns carry independent UNIQUE constraints. Asserting: three separate
conflicts are possible — same code, same filename, or same URL — each rejected by
the database.
Source: `jpa/entities/Image.java`, `database-documentation/00-global-schema.sql`.

## BR-IMAGE-002 Filename is the business key used for existence checks

`ImagesCrudServiceImpl.getExisting` resolves exclusively via
`ImagesRepository.findByFilename`. Asserting: creating an image with a new
filename but a duplicate URL passes the service-level existence check and then
fails at the database constraint instead — the two checks are not aligned.
Source: `jpa/services/crud/impl/ImagesCrudServiceImpl.java`.

## BR-IMAGE-003 A blank filename is rejected before any query runs

`getExisting` throws a bad-input error with the literal message
`Invalid filename` when the submitted filename is blank.
Source: `jpa/services/crud/impl/ImagesCrudServiceImpl.java`.

## BR-IMAGE-004 Code, filename and URL are all mandatory on the transport model

`ImagePojo` marks all three `@NotBlank`. Asserting: a create or replace payload
missing any of them is rejected with the validation envelope (`REJECTED_02`)
before service code executes.
Source: `api/models/ImagePojo.java`.

## BR-IMAGE-005 The URL is never validated as a URL

There is no format check, scheme check or reachability check on `image_url` — only
the 500-character length limit. Asserting: `not a url` is an acceptable value.
Source: `jpa/entities/Image.java`, `api/models/ImagePojo.java`.

## BR-IMAGE-006 No binary content is ever stored or served

The domain has no upload route, no multipart handling, no filesystem write and no
object-store client. Asserting: the API cannot accept image bytes; it only records
where an image lives.
Source: `api/controllers/DataImagesController.java` (no multipart parameters).

## BR-IMAGE-007 Blank values never clear stored image metadata

`ImagesPatchServiceImpl` applies `code`, `filename` and `url` only when non-blank.
Asserting: patching `url` to `""` leaves the stored URL unchanged.
Source: `jpa/services/patch/impl/ImagesPatchServiceImpl.java`.

## BR-IMAGE-008 Replacement is a full overwrite, not a merge

The inherited replace path converts the body into a brand-new entity and assigns
the existing identifier before saving. Asserting: a `PUT` body carrying only
`filename` blanks out `code` and `url` — unless transport validation rejects it
first, which it does here because all three fields are `@NotBlank`.
Source: `jpa/services/crud/CrudGenericService.java`.

## BR-IMAGE-009 Update and patch require a selecting parameter map

The generic CRUD controller rejects an empty request parameter map on both
`PUT` and `PATCH` with the bad-input message `Missing request params`.
Asserting: `PUT /data/images` with no query parameters is a 400, never a mass
update.
Source: `api/DataCrudGenericController.java`.

## BR-IMAGE-010 Update and patch refuse to touch more than one row

When the resolved filter matches more than one row, the generic service raises
`Cannot update more than one item at a time`. Asserting: `?codeLike=x` matching
two images fails rather than updating both.
Source: `jpa/services/crud/CrudGenericService.java`.

## BR-IMAGE-011 Update and patch on a non-matching filter are a not-found

The generic CRUD controller throws a not-found error with the message
`No element was found to update` when the service returns no match.
Source: `api/DataCrudGenericController.java`.

## BR-IMAGE-012 An empty delete filter deletes nothing

The generic CRUD controller silently returns when the request parameter map is
empty on `DELETE`. Asserting: `DELETE /data/images` with no parameters removes no
rows and still responds successfully — there is deliberately no mass-delete path.
Source: `api/DataCrudGenericController.java`.

## BR-IMAGE-013 Deleting a filter that matches nothing is a not-found

The generic delete raises `Requested item(s) not found` when the count is zero.
Source: `jpa/services/crud/CrudGenericService.java`.

## BR-IMAGE-014 Deleting an image does not unlink it from products

Deletion removes the `images` row directly. `product_images` holds a foreign key
to `images.image_id` with no cascade and no pre-delete cleanup in this domain.
Asserting: deleting an image that is linked to a product fails at the foreign-key
constraint (surfacing as a 500-class error, not a business error).
Source: `database-documentation/00-global-schema.sql` (`FK_PIMGS_IMAGE_ID`),
absence of any unlink logic in `ImagesCrudServiceImpl`.

## BR-IMAGE-015 Every image operation requires its own authority

`images:read`, `images:create`, `images:update` (shared by `PUT` and `PATCH`) and
`images:delete`. Seeded roles holding all four: Administrator and Manager.
Salesperson holds none of them.
Source: `api/controllers/DataImagesController.java`,
`database-documentation/00-seed-data.json`.
