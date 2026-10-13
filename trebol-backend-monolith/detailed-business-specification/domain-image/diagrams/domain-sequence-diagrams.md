# Image Registry — Sequence Diagrams

Five flows: the list read, creation, patch, the destructive delete paths, and the
cross-domain write from the `product` domain. Step-level behaviour for every participant is
in document `02`.

---

## 1. List images — `GET /data/images`

```mermaid
sequenceDiagram
    actor Caller as Catalogue administrator
    participant Sec as Security layer
    participant Ctl as DataImagesController
    participant Pag as PaginationService
    participant Srt as SortSpecParserService
    participant Spec as ImagesSortSpec
    participant Pred as ImagesPredicateServiceImpl
    participant Crud as ImagesCrudServiceImpl
    participant Repo as ImagesRepository
    participant Conv as ImagesConverterServiceImpl
    participant DB as images table

    Caller->>Sec: GET /data/images?selectors,paging,sort
    alt not authenticated
        Sec-->>Caller: 401
    else lacks images:read
        Sec-->>Caller: 403
    else authorized
        Sec->>Ctl: readMany(allRequestParams)
        Ctl->>Pag: determineRequestedPageIndex / PageSize
        Pag-->>Ctl: pageIndex (0 default), pageSize (capped)
        opt params not empty
            Ctl->>Spec: ORDER_SPEC_MAP
            Spec-->>Ctl: allow-list {code, filename, url}
            Ctl->>Srt: parse(allowList, params)
            Srt-->>Ctl: Sort (unsorted if no sortBy)
            Ctl->>Pred: parseMap(params)
            Pred-->>Ctl: Predicate (matches ALL if nothing recognised)
        end
        Ctl->>Crud: readMany(pageIndex, pageSize, sort, predicate)
        Crud->>Repo: count(predicate)
        Repo->>DB: SELECT COUNT(*)
        DB-->>Crud: totalCount
        Crud->>Repo: findAll(predicate, pageable)
        Repo->>DB: SELECT ... LIMIT/OFFSET
        DB-->>Crud: Page of Image
        loop each Image
            Crud->>Conv: convertToPojo(image)
            Conv-->>Crud: ImagePojo (no identifier)
        end
        Crud-->>Ctl: DataPagePojo
        Ctl-->>Caller: 200 + page
    end
```

Two database round trips per request, not atomic with respect to concurrent writes. An
out-of-range page yields an empty list with a non-zero total, not a 404.

---

## 2. Create an image — and the uniqueness defect

```mermaid
sequenceDiagram
    actor Caller as Catalogue administrator
    participant Ctl as DataImagesController
    participant Crud as ImagesCrudServiceImpl<br/>(transactional)
    participant Repo as ImagesRepository
    participant Conv as ImagesConverterServiceImpl
    participant DB as images table

    Caller->>Ctl: POST /data/images {code, filename, url}
    Ctl->>Ctl: validate body - all three non-blank
    alt a field is blank or absent
        Ctl-->>Caller: 400 route validation
    else body valid
        Ctl->>Crud: create(input)
        Crud->>Crud: validateInputPojoBeforeCreation
        Crud->>Crud: getExisting(input)
        alt filename blank
            Crud-->>Caller: BadInputException "Invalid filename" → 400
        else filename present
            Crud->>Repo: findByFilename(filename)
            Repo->>DB: SELECT ... WHERE image_filename = ?
            DB-->>Crud: row or none

            alt FILENAME already exists
                Crud-->>Caller: EntityExistsException<br/>"The item already exists" → 409
            else filename free
                Crud->>Conv: convertToNewEntity(input)
                Conv-->>Crud: unsaved Image, id null
                Crud->>Repo: saveAndFlush(image)
                Repo->>DB: INSERT INTO images

                rect rgb(245, 235, 235)
                note over Crud,DB: The guard checked FILENAME only -<br/>two of three unique keys are unprotected
                alt UK_IMG_CODE violated
                    DB-->>Caller: raw constraint violation → 500 (NOT 409)
                else UK_IMG_URL violated
                    DB-->>Caller: raw constraint violation → 500 (NOT 409)
                else UK_IMG_FILENAME violated by a concurrent create
                    DB-->>Caller: raw constraint violation → 500
                end
                end

                DB-->>Crud: row with generated image_id
                Crud->>Conv: convertToPojo(saved)
                Conv-->>Crud: ImagePojo (no identifier)
                Crud-->>Ctl: ImagePojo (discarded)
                Ctl-->>Caller: 201 Created, EMPTY BODY
            end
        end
    end
```

**Notes.** BR-IMAGE-002: two of the three duplicate cases report 500 rather than 409. The
guard and the insert are separate statements with no lock between them, so the concurrent
branch is reachable (BR-IMAGE-010). The 201 carries no body and the model has no
identifier, so a creator learns nothing about what it created (BR-IMAGE-012).

---

## 3. Patch an image

```mermaid
sequenceDiagram
    actor Caller as Catalogue administrator
    participant Ctl as DataCrudGenericController
    participant Pred as ImagesPredicateServiceImpl
    participant Crud as ImagesCrudServiceImpl<br/>(transactional)
    participant Repo as ImagesRepository
    participant Patch as ImagesPatchServiceImpl
    participant Conv as ImagesConverterServiceImpl
    participant DB as images table

    Caller->>Ctl: PATCH /data/images?filename=x {changes}
    alt requestParams empty
        Ctl-->>Caller: BadInputException "Missing request params" → 400
    else params present
        Ctl->>Pred: parseMap(requestParams)
        Pred-->>Ctl: Predicate
        Ctl->>Crud: partialUpdate(changes, predicate)
        Crud->>Repo: count(predicate)

        alt count > 1
            Crud-->>Caller: RuntimeException<br/>"Cannot update more than one item at a time" → 500
        else count <= 1
            Crud->>Repo: findOne(predicate)
            alt no match
                Crud-->>Ctl: empty Optional
                Ctl-->>Caller: EntityNotFoundException<br/>"No element was found to update" → 404
            else found
                Crud->>Patch: patchExistingEntity(changes, existing)
                Patch->>Patch: clone via copy constructor (id included)
                loop code, filename, url
                    Patch->>Patch: if key present AND value not blank → set<br/>else leave unchanged (NO field can be cleared)
                end
                Patch->>Patch: unknown keys silently ignored

                alt a value is not a string
                    Patch-->>Caller: ClassCastException, wrapped → 500
                else patch applied
                    Patch-->>Crud: modified clone
                    Crud->>Crud: existing.equals(clone)?
                    alt equal - nothing changed
                        Crud->>Conv: convertToPojo(existing)
                        Crud-->>Ctl: ImagePojo, NO DATABASE WRITE
                        Ctl-->>Caller: 204
                    else differs
                        Crud->>Repo: saveAndFlush(clone)
                        Repo->>DB: UPDATE images
                        alt new value collides with another row
                            DB-->>Caller: raw constraint violation → 500<br/>(no clean 409 on PATCH for ANY field)
                        else write succeeds
                            DB-->>Crud: saved row
                            Crud-->>Ctl: ImagePojo
                            Ctl-->>Caller: 204
                        end
                    end
                end
            end
        end
    end
```

**Notes.** The equality comparison covers all four entity fields, which is what makes the
no-write branch reachable (BR-IMAGE-009). All three fields are blank-guarded, so no field
can be cleared and no null can reach a `NOT NULL` column (BR-IMAGE-007). A uniqueness
collision on patch is a 500 for every field, including filename (BR-IMAGE-002).

---

## 4. Delete — the four hazardous paths

```mermaid
sequenceDiagram
    actor Caller
    participant Ctl as DataCrudGenericController
    participant Pred as ImagesPredicateServiceImpl
    participant Crud as ImagesCrudServiceImpl
    participant DB as images table

    rect rgb(235, 240, 245)
    note over Caller,DB: (a) No parameters - SILENT NO-OP (BR-IMAGE-022)
    Caller->>Ctl: DELETE /data/images
    Ctl->>Ctl: if (!requestParams.isEmpty()) - FALSE
    Ctl-->>Caller: 204 No Content, NOTHING DELETED
    end

    rect rgb(245, 235, 235)
    note over Caller,DB: (b) A TYPO - deletes the entire registry (BR-IMAGE-013)
    Caller->>Ctl: DELETE /data/images?fileName=banner.png
    Ctl->>Ctl: map is non-empty - guard PASSES
    Ctl->>Pred: parseMap({fileName: ...})
    Pred->>Pred: "fileName" is not recognised - ignored
    Pred-->>Ctl: EMPTY accumulator = MATCHES EVERYTHING
    Ctl->>Crud: delete(predicate)
    Crud->>DB: COUNT -> all rows, non-zero
    Crud->>DB: DELETE every row
    Ctl-->>Caller: 204 - ENTIRE REGISTRY DELETED
    end

    rect rgb(245, 235, 235)
    note over Caller,DB: (c) Unescaped metacharacter (BR-IMAGE-011)
    Caller->>Ctl: DELETE /data/images?codeLike=%
    Ctl->>Pred: parseMap
    Pred-->>Ctl: Predicate matching EVERY image
    Ctl->>Crud: delete(predicate)
    Crud->>DB: DELETE every row
    Ctl-->>Caller: 204 - ENTIRE REGISTRY DELETED
    end

    rect rgb(240, 240, 240)
    note over Caller,DB: (d) Correct usage
    Caller->>Ctl: DELETE /data/images?filename=banner.png
    Ctl->>Pred: parseMap
    Pred-->>Ctl: Predicate on filename (short-circuits)
    Ctl->>Crud: delete(predicate)
    Crud->>DB: COUNT
    alt count == 0
        Crud-->>Caller: EntityNotFoundException<br/>"Requested item(s) not found" → 404
    else count >= 1
        Crud->>DB: DELETE all matches - NO UPPER BOUND
        Ctl-->>Caller: 204
    end
    end
```

**Why these are grouped.** Path (a) is safe but misreported — 204 is indistinguishable from
a real deletion. Paths (b) and (c) are the severe findings: an empty parameter map is safe
while a *typo* destroys the registry, because the controller's guard tests only
non-emptiness while the predicate defaults to matching everything. A rewrite MUST require at
least one **recognised** selector before deleting.

---

## 5. Cross-domain: the `product` domain writes images

```mermaid
sequenceDiagram
    actor Editor as Product editor
    participant PCtl as Product routes<br/>(domain product)
    participant PCrud as ProductsCrudServiceImpl<br/>(domain product)
    participant ICrud as ImagesCrudService<br/>(this domain)
    participant IRepo as ImagesRepository
    participant DB as images + product_images

    Editor->>PCtl: create/update product with images[]
    PCtl->>PCrud: create(productPojo)
    PCrud->>PCrud: makeProductImageRelationships(product, images)

    loop for each ImagePojo in payload
        PCrud->>ICrud: getExisting(img)
        ICrud->>IRepo: findByFilename(img.filename)
        IRepo->>DB: SELECT ... WHERE image_filename = ?

        alt filename blank
            ICrud-->>PCrud: BadInputException "Invalid filename"
            PCrud->>PCrud: CATCH, log at debug, SKIP this image
            note over PCrud: Image SILENTLY DROPPED.<br/>Product still succeeds with fewer images.<br/>Client is never told.
        else match found
            DB-->>PCrud: existing Image
            note over PCrud: payload code and url are DISCARDED -<br/>stored row wins, no error
        else no match
            DB-->>PCrud: none
            PCrud->>PCrud: Image.builder() directly - BYPASSES create()
            note over PCrud: no duplicate guard,<br/>no 409 path,<br/>no route validation
        end
        PCrud->>PCrud: build ProductImage(product, image)
    end

    PCrud->>DB: save product + cascade
    alt a new Image collides on code or url
        DB-->>Editor: constraint violation → PRODUCT creation fails<br/>(an image fault reported as a product error)
    else success
        DB-->>Editor: product created, image rows inserted
    end
```

**Notes.** This is the domain's entire internal write path and it bypasses the service's
creation operation (BR-IMAGE-024). Three behaviours a rewrite must decide about
deliberately: images created without `images:create`; a blank filename silently dropping an
image; and a payload's `code`/`url` being discarded when the filename already resolves.
Detail in `specs/06-integration-points.md` §2.3.

---

## 6. Flows that do not exist

Their absence is load-bearing:

- **No upload or download flow.** No binary content is ever received or served.
- **No URL dereference.** No HTTP client exists; the URL is never fetched or verified.
- **No image processing.** Nothing resizes, converts, compresses or inspects.
- **No event publication.** Nothing emits a domain event, message or notification.
- **No background job.** No scheduled or asynchronous flow touches this domain.
- **No cache interaction.** No flow reads or writes a cache.
- **No single-image fetch route.** `readOne` exists on the service but no endpoint calls it.
