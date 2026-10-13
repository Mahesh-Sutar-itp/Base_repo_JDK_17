# Workflows — Company Profile (`company-profile`)

## W-COMPANY-01 Retrieve the public company profile

1. Any caller, authenticated or not, issues `GET /public/about`.
2. The controller delegates to the company service with no arguments.
3. The service queries every `app_params` row whose category equals the literal
   `company`.
4. An empty response object is created.
5. For each returned row the service switches on the row's name:
   - `name` → the profile name field
   - `description` → the profile description field
   - `bannerImageURL` → the banner URL field
   - `logoImageURL` → the logo URL field
   - anything else → ignored, no error, no log entry
6. The populated object is returned with status `200`.
7. Serialisation omits any field that was never assigned.

No exception path exists in this workflow. The only way it fails is an
infrastructure failure reaching the database, which surfaces as a generic
500-class error through the default error handling.

## W-COMPANY-02 Changing the company profile (out of band)

There is no API workflow. The sequence an operator must follow today is:

1. Connect directly to the database.
2. Update the `param_value` of the relevant `(category='company', name=...)` row,
   or insert the row if it is missing, respecting the composite uniqueness
   constraint and the 500-character value limit.
3. The change takes effect on the very next request, because nothing is cached.

A rewrite that adds a write surface must decide on an authority code (none
exists), a validation policy for the URL values (none exists) and whether to keep
the EAV shape or promote the four values to a typed configuration record.

## Request/response shape

```text
GET /public/about            (no headers required)

200 OK
{
  "name":           "Trébol Demo",
  "description":    "This is a demostration of integrated eCommerce system Trébol",
  "bannerImageURL": "https://fakeimg.pl/400x150",
  "logoImageURL":   "https://fakeimg.pl/250"
}
```

With an empty or mis-keyed `app_params` table the same call returns `200 {}`.

## State model

`Param` has no status column and no lifecycle states. In the running system
parameter rows are effectively immutable seed data: created by the bootstrap
script and never touched again through any application code path.
