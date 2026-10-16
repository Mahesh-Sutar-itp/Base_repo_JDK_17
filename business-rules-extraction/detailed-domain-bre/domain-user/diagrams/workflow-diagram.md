# Workflow diagram — Identity & Access (`user`)

## Session state progression (state lives in the token, not the database)

```mermaid
stateDiagram-v2
    [*] --> Anonymous

    Anonymous --> Authenticated: POST /public/login with valid credentials<br/>(token issued in header AND body)
    Anonymous --> Authenticated: POST /public/guest when<br/>guest-user-enabled = true
    Anonymous --> Anonymous: POST /public/login with bad credentials<br/>or an undeserialisable body
    Anonymous --> Anonymous: request with no Authorization header

    Authenticated --> Anonymous: token expiration passes<br/>(NO error - the chain simply<br/>continues unauthenticated)
    Authenticated --> Unauthorized: malformed or untrusted token<br/>(full token value logged)
    Authenticated --> Authenticated: any request within the<br/>expiration window

    Unauthorized --> Anonymous: client retries without a header

    note right of Authenticated
        Authorities are frozen inside the token.
        Revoking a grant has NO effect until the
        token expires (default 30 minutes).
        There is no logout, no revocation list
        and no refresh.
    end note
    note right of Anonymous
        Because SecurityConfig declares no
        route-matching rules, an anonymous caller
        can still reach every endpoint whose
        method carries no authority annotation.
    end note
```

## Authentication decision flow

```mermaid
flowchart TD
    Req([Incoming request]) --> Hdr{Authorization header}
    Hdr -- "absent or blank" --> Anon[No security context set]
    Hdr -- present --> Strip[Strip 'Bearer ' by replace-all]
    Strip --> Parse{parse + verify signature}
    Parse -- fails --> Log[/Log the FULL token value<br/>set 401, empty body/]
    Parse -- succeeds --> Exp{expiration after now?}
    Exp -- no --> Anon
    Exp -- yes --> Ctx[Set context from subject<br/>+ authorities claim]
    Anon --> Method{endpoint has an<br/>authority annotation?}
    Ctx --> Method
    Method -- no --> Open[/Endpoint executes:<br/>publicly reachable/]
    Method -- "isAuthenticated()" --> IsAuth{context present?}
    IsAuth -- no --> Deny[/403 access denied/]
    IsAuth -- yes --> Run([Endpoint executes])
    Method -- "hasAuthority(code)" --> Has{code in the token's<br/>authority list?}
    Has -- no --> Deny
    Has -- yes --> Run
```

## Registration flow with its non-transactional window

```mermaid
flowchart TD
    Start([POST /public/register]) --> Valid{name, password non-blank<br/>and nested profile valid?}
    Valid -- no --> V1[/400 REJECTED_02 validation envelope/]
    Valid -- yes --> NameFree{account name already taken?}
    NameFree -- yes --> V2[/400 EXISTS_01 "That username is taken."/]
    NameFree -- no --> Convert[Convert profile to a new Person]
    Convert --> IdFree{identification number<br/>already registered?}
    IdFree -- yes --> V3[/400 EXISTS_01 "That ID number is already<br/>registered and associated to an account."/]
    IdFree -- no --> W1

    subgraph NoTx["NOT transactional - partial writes are possible"]
        W1[Write 1: saveAndFlush the person] --> Role{role 'Customer' exists?}
        Role -- no --> V4[/500 "No user role matches 'Customer',<br/>database might be compromised"<br/>PERSON ALREADY PERSISTED/]
        Role -- yes --> Hash[BCrypt-encode the password<br/>at the configured strength]
        Hash --> W2[Write 2: saveAndFlush the account<br/>with the Customer role]
        W2 --> W3[Write 3: saveAndFlush a customer<br/>row for the person]
        W3 --> Fail3{write 3 fails?}
        Fail3 -- yes --> Orphan[/500: person + account persisted,<br/>NO customer row.<br/>No repair path exists./]
    end
    Fail3 -- no --> Done([200 - registration complete])
```

## Profile replacement decision flow (`PUT /account/profile`)

```mermaid
flowchart TD
    P([PUT /account/profile]) --> NoValid[Body is NOT validated:<br/>this route omits the validation marker]
    NoValid --> Load[Resolve the account by<br/>the authenticated principal's name]
    Load --> Acc{account found?}
    Acc -- no --> E1[/500 "There is no account with<br/>the specified username"<br/>unmapped by the error advice/]
    Acc -- yes --> HasPerson{account already<br/>has a person?}

    HasPerson -- yes --> Patch[Delegate to the person patch service:<br/>names / idNumber / email applied only<br/>when non-blank; phone1 / phone2<br/>applied unconditionally]
    Patch --> Save1[Save the account]
    Save1 --> OK([200])

    HasPerson -- no --> Lookup[Look for an existing person<br/>by identification number]
    Lookup --> Found{person found?}
    Found -- no --> Create[Create, save and attach<br/>a new person]
    Create --> OK
    Found -- yes --> Owned{another account already<br/>references that person?}
    Owned -- yes --> E2[/400 REJECTED_01 "Person profile is associated<br/>to another account. Cannot use it."<br/>NOTE: no unique constraint backs this,<br/>so concurrent requests can bypass it/]
    Owned -- no --> Attach[Attach the existing person]
    Attach --> OK
```

## Authority resolution and the staleness window

```mermaid
flowchart LR
    Grant[(app_user_role_permissions<br/>grant rows)] --> Resolve[At LOGIN only:<br/>role id -> grants -> authority codes<br/>de-duplicated into a set]
    Resolve --> Token[Authorities embedded in the<br/>'authorities' token claim]
    Token --> Check[Every subsequent request checks<br/>the TOKEN, not the database]
    Grant -. "operator deletes a grant<br/>directly in the database -<br/>no API exists" .-> Stale{{Holder keeps the authority<br/>until the token expires:<br/>up to 30 minutes by default}}
    Stale --> Check
```
