# Integrations — Image Registry (`image`)

## External systems

**None.** The domain stores URLs that point at externally hosted images but never
dereferences them. There is no HTTP client, no object-store SDK, no filesystem
access and no content-delivery integration anywhere in the image code path.

## Inbound integrations

| Caller domain | Entry point | Purpose |
|---|---|---|
| `product` | `ImagesCrudService.getExisting(ImagePojo)` | resolve an existing image while linking images to a product |

The `product` domain owns the `product_images` join table and the
`ProductImagesRepository`; this domain owns only the `images` rows themselves.
The coupling is one-directional: `image` has no compile-time or runtime reference
to `product`.

## Outbound integrations

| Target | Nature |
|---|---|
| shared pagination service | page index/size resolution |
| shared sort-spec parser | `sortBy` / `order` resolution |
| shared generic CRUD base | create/read/update/patch/delete behaviour |
| shared persistence abstraction | paged + predicate query execution |

All shared-kernel components; no business-domain dependency.

## Events

**None.** No event is published when an image is registered, changed or deleted.
See `cross-cutting-documentation/event-publishing-patterns.md`.

## Scheduled work

**None.** No job prunes orphaned images, validates URLs or reconciles the registry
against external storage.

## Anti-corruption surface

`ImagePojo` ↔ `Image` translation is isolated in a dedicated converter, and
patching goes through a dedicated patch service that never touches the entity's
identifier. The `product` domain consumes this domain only through the crud
service interface, never through the repository or the entity directly — the one
clean service-level boundary in the image code path.
