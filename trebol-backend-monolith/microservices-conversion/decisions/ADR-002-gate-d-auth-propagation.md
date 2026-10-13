# ADR-002 — Gate D: authentication propagation model

**Status:** accepted · **Gate:** D (transformation definition §5.4) · **Phase:** 0

## Context — verified against source

`org.trebol.config.SecurityConfig` builds its `SecurityFilterChain` with
`authenticationManager`, `headers(frameOptions sameOrigin)`, `csrf disable`,
`sessionManagement STATELESS` and three filters. It declares **no
`authorizeHttpRequests` block and no request-matcher rules of any kind**.
Authorization is `@EnableMethodSecurity` plus `@PreAuthorize` on controller
methods, exclusively.

| Baseline property | Consequence for a split |
|---|---|
| Stateless HMAC-SHA bearer token; claims are subject, `authorities`, issued-at, expiration. **No `iss`, `aud` or `jti`.** | Each service can verify locally with a shared key, but there is no token id for revocation and no audience to scope a token to one service. |
| **Authorities are resolved at login and embedded in the token**, never re-resolved per request (BR-USER-022). | Favourable: no service needs to call Identity to authorize. But a revoked grant stays effective until expiry, across all three services. |
| **Enforcement is entirely method-level; no request-matcher rules exist.** | **Any endpoint without an authority annotation is publicly reachable** (BR-USER-025). A gateway therefore **cannot** derive authorization from paths. |
| Expired token → the filter chain continues **anonymously**, no error (BR-USER-019). | A caller cannot distinguish "expired" from "never sent". |
| `/public/login` and `/public/guest` are **servlet filters, not controllers**. | Invisible to any controller-derived route inventory; they belong to Identity and must be ported as filters (Phase 2). |
| No logout, no revocation list, no refresh. CSRF disabled. | Nothing to distribute — and nothing to revoke. |
| Malformed token → unauthorized with an empty body, and **the full token value is written to the log** (BR-USER-020). | Credential leak; must not be replicated into three services' logs. |

## Decision

1. **Shared HMAC signing key**, distributed to all three services by external
   configuration (environment / secret store). **Never committed** — see G7 and
   register item D20. The baseline commits the key in `application.properties`;
   that must not be replicated three times.
2. **Local verification per service.** No service calls Identity to validate a
   token. Identity remains the sole **issuer**.
3. **Method-level enforcement retained in every service**, using the Gate B
   authority catalogue. This is mandatory, not stylistic: because there are no
   request-matcher rules, an endpoint without an annotation is public.
4. **The gateway performs routing and TLS termination only. No authorization.**
   It must never be relied on as an access-control layer.
5. **Add `iss` and `aud` claims in Phase 2**, when Identity owns token issuance:
   `iss` = identity service, `aud` = the set of services accepting the token.
   Verification of both is enforced at each service.
6. **Add `jti` in Phase 2** so that revocation becomes possible later. A
   revocation list is **not** built now — there is no logout today and building
   one is new product work, not conversion. Recorded as an accepted limitation.
7. **Anonymous continuation on an expired token is preserved** (BR-USER-019). It
   is client-observable (G1/G2) and three of the baseline tests assert it. Fixing
   it is a contract change and is registered, not bundled in.
8. **D2 is fixed in Phase 0** — the token value is removed from the log statement.
   This is the one Gate D item that changes before any extraction, because a
   credential leak replicated into three services is strictly worse.

## Consequences

- Every new service module must enable method security and annotate every
  non-public route. A service that forgets gets a silently public endpoint. A
  per-service route-authority inventory test is required at each port.
- X10 ("no secret in committed configuration in any service") is enforced from the
  first extraction, not retrofitted at the end.
- Because authorities are token-embedded, Identity does **not** become a
  synchronous dependency of Catalogue or Orders on the request path.
