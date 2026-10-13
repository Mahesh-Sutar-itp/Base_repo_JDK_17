# Person Registry — Integrations

**No external integrations applicable to this domain.**

This domain's only I/O is the relational database holding its one owned table, `people`.
There is no outbound HTTP or RPC client, no message broker, no e-mail or SMS sender, no
payment or shipping provider, no identity-verification service, no cache, no search index
and no file or object-store export. There is also no no-op or mock client standing in for
an absent provider.

Verified against the domain's complete 14-file source inventory and the system-wide
`reference-documentation/external-integrations-map.md`.

## Two absences worth recording explicitly

1. **Nothing contacts the stored contact details.** The domain persists an e-mail address
   and two phone numbers, and no code in the system ever sends anything to them. They are
   stored data, not communication channels.
2. **The national identification number is never verified.** It is the domain's business
   key and its only uniqueness invariant, yet it is accepted as an opaque string of up to
   20 characters with no format check, no checksum and no lookup against any external
   registry. A rewrite that wants verification has no existing integration to model it on.

## Cross-domain integration

The domain has substantial **in-process** coupling — seventeen inbound call sites from
three consumer domains, six of which reach its repository directly. These are not external
integrations today, but they are exactly what a service extraction must convert into real
ones.

Specified in full in `specs/06-integration-points.md` §2, with the extraction consequences
in §2.3 and §4. The headline risk: the `user` domain's registration flow currently creates
a person, a user and a customer in **one shared database transaction**, and splitting this
domain out removes that atomicity with no delete path available to compensate.
