# Feature Flag Inventory

## Finding: there is no feature-flag framework

A repository-wide search returned **zero matches** for conditional-bean
annotations, feature-flag libraries, remote configuration clients and
flag-evaluation services. There is no percentage rollout, no targeting, no
runtime toggle and no flag audit.

What exists instead: **four boolean configuration properties** and **one
framework profile**, all evaluated at startup or per request from static
configuration. They are the only switches in the system.

## The four boolean toggles

### 1. `trebol.security.guest-user-enabled` (default `false`)

| Aspect | Detail |
|---|---|
| Read by | `security/UserDetailsServiceImpl.java` |
| Effect when true | the configured guest name is recognised as an account, and its authorities are loaded from `trebol.security.guest-user-role-id` (default 4, the `Customer` role) |
| Effect when false | `POST /public/guest` falls through to a normal account lookup and fails |
| Scope | affects authentication for every request |
| Risk | when enabled, the guest password **equals the guest name**, which is public configuration (`BR-USER-026`); pointing the role identifier at the Administrator role would grant guests administrative authorities (`BR-USER-030`) |
| Rules | `BR-USER-026`, `BR-USER-027`, `BR-USER-030` |

### 2. `trebol.security.account-protection-enabled` (default `true`)

| Aspect | Detail |
|---|---|
| Read by | `jpa/services/crud/impl/UsersCrudServiceImpl.java` |
| Effect when true | deleting the account whose identifier equals `trebol.security.protected-account-id` (default 1 — the seeded administrator) raises a protection failure |
| Effect when false | that account becomes deletable |
| Risk | the guard inspects only a **single matched account**, so a filter matching several accounts bypasses it entirely while the generic delete removes them all (`BR-USER-042`); the failure is unmapped and surfaces as a 500-class response (`BR-USER-043`) |
| Rules | `BR-USER-041`, `BR-USER-042`, `BR-USER-043` |

### 3. `trebol.api.able-to-edit-orders-after-being-processed` (default `false`)

| Aspect | Detail |
|---|---|
| Read by | `jpa/services/crud/impl/OrdersCrudServiceImpl.java` |
| Effect when false | an order whose status code is 3 or above, or negative, cannot be partially updated — `Pending` (1) and `Payment Started` (2) remain editable |
| Effect when true | **every** processed order becomes freely patchable, including its status |
| Risk | this is effectively a switch that disables order immutability system-wide. Combined with the patch bypass (`BR-ORDER-028`) it allows an operator with `orders:update` to move any order to any status. Note the lock **never** covered full replacement, regardless of the toggle (`BR-ORDER-031`) |
| Rules | `BR-ORDER-029`, `BR-ORDER-030`, `BR-ORDER-031` |

### 4. `trebol.payment.webpayplus.production` (default `false`)

| Aspect | Detail |
|---|---|
| Read by | `payment/impl/webpayplus/WebpayplusPaymentServiceImpl.java` |
| Effect when false | the provider's published **integration sandbox** commerce code, API key and test mode are used |
| Effect when true | the configured `commerceCode` and `apiKey` are used with live mode |
| Risk | the single switch between taking real money and not. There is no intermediate state and no runtime indication in any response which mode is active |
| Rules | `BR-ORDER-034` (sandbox default) |

## The one profile

### `mailgun`

| Aspect | Detail |
|---|---|
| Declared on | `mailing/impl/mailgun/MailgunMailingProperties.java` and `MailgunMailingServiceImpl.java` |
| Effect when active | the Mailgun mailing bean exists and order-status notifications are attempted |
| Effect when inactive | **no mailing bean exists at all** |
| How absence is handled | the mailing component is injected **optionally** and null-checked at every call site, so the entire order flow works with no mail provider (`BR-ORDER-052`) |
| Configuration | `src/main/resources/application-mailgun.empty.properties` is a template with **all nine keys blank**; it must be copied to `application-mailgun.properties` and populated |
| Risk | the template's blank subject and template values would be supplied to an immutable-map construction at startup, so an incompletely configured profile is a startup failure rather than a runtime degradation |

This is the closest thing in the system to a real feature flag: it switches a
whole capability on or off, and the capability is designed to be absent.

## Configuration that looks like a flag but is not

| Property | Why it is not a flag |
|---|---|
| `trebol.api.items-per-page`, `max-allowed-page-size` | numeric tuning, affects every listing response |
| `trebol.api.max-category-fetching-recursion-depth` | numeric ceiling; exceeding it **silently truncates** descendant resolution (`BR-PRODCAT-010`) |
| `trebol.security.jwt-expiration-after-days/hours/minutes` | numeric tuning; all three may be zero, which issues already-expired tokens (`BR-USER-017`) |
| `trebol.security.bcrypt-encoder-strength` | numeric tuning, minimum 6 |
| `trebol.security.guest-user-name`, `guest-user-role-id`, `protected-account-id` | parameters of the two security toggles above |
| `trebol.validation.id-number-regexp`, `phonenumber-regexp` | patterns that are configured and **never applied** to any field (`BR-PERSON-006`) |
| `trebol.cors.*` (four properties, including a 36-line mapping list) | **dead configuration** — the builder that reads them is wired into nothing (see `dead-code-decomposition-notes.md`) |
| `trebol.mailing.*` (12 properties) | content configuration for the mailgun profile; two subjects are explicitly commented "Not used" |
| `trebol.payment.webpayplus.callback-url`, `browser-redirection-url` | endpoint configuration |

## Summary

| Switch | Type | Default | Blast radius |
|---|---|---|---|
| `trebol.security.guest-user-enabled` | boolean | false | authentication |
| `trebol.security.account-protection-enabled` | boolean | true | one account's deletability |
| `trebol.api.able-to-edit-orders-after-being-processed` | boolean | false | **order immutability, system-wide** |
| `trebol.payment.webpayplus.production` | boolean | false | **real money versus sandbox** |
| `mailgun` | profile | inactive | all outbound notification |

Five switches in a 17,800-line application, none of them runtime-changeable.

## Implications for a rewrite

1. **Two of the five are high-blast-radius and silently defaulted.** The order
   edit toggle and the payment production flag both change materially important
   behaviour with no runtime signal. A rewrite should surface the active mode.
2. **Nothing is runtime-togglable.** Every switch requires a restart, and nothing
   is audited. Introducing a flag framework is new work.
3. **The configuration file in source control is explicitly marked
   non-production** and contains a placeholder signing key, committed bootstrap
   credentials and a blank-value mail template. A rewrite must separate
   configuration from the repository.
4. **Dead configuration should be removed deliberately**, not carried forward:
   the four CORS properties and the two validation patterns configure nothing.

## Related documentation

- the security toggles in context: `../cross-cutting-documentation/authentication-authorization.md`
- the order edit lock: `../detailed-domain-bre/domain-order/business-rules.md`
- the mailing and payment adapters: `external-integrations-map.md`
- dead configuration: `dead-code-decomposition-notes.md`
