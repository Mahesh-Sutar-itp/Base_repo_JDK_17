# Sequence diagrams — Identity & Access (`user`)

## Password login and token issuance (`POST /public/login`)

```mermaid
sequenceDiagram
    actor Client
    participant JwtLoginAuthenticationFilter
    participant AuthenticationManager
    participant UserDetailsServiceImpl
    participant UsersRepository
    participant UserPermissionsServiceImpl
    participant UserRolePermissionsRepository
    participant BCryptPasswordEncoder
    participant GenericJwtAuthenticationFilter

    Client->>JwtLoginAuthenticationFilter: POST /public/login {name, password}
    alt method is not POST
        JwtLoginAuthenticationFilter-->>Client: no authentication attempted (null)
    end
    JwtLoginAuthenticationFilter->>JwtLoginAuthenticationFilter: deserialise body into LoginPojo
    Note over JwtLoginAuthenticationFilter: own ObjectMapper instance:<br/>LoginPojo @NotBlank constraints NEVER run
    alt body not deserialisable
        JwtLoginAuthenticationFilter-->>Client: BadCredentials "Request body is not a login request"
    end
    JwtLoginAuthenticationFilter->>AuthenticationManager: authenticate(name, password)
    AuthenticationManager->>UserDetailsServiceImpl: loadUserByUsername(name)
    alt guest enabled AND name == configured guest name
        UserDetailsServiceImpl->>UserRolePermissionsRepository: deepFindPermissionsByUserRoleId(guestUserRoleId)
        Note over UserDetailsServiceImpl: username AND password both = guest name
    else regular account
        UserDetailsServiceImpl->>UsersRepository: findByNameWithRole(name)
        alt no such account
            UserDetailsServiceImpl-->>Client: UsernameNotFound(name)
        end
        UserDetailsServiceImpl->>UserPermissionsServiceImpl: loadPermissionsForUser(user)
        UserPermissionsServiceImpl->>UserRolePermissionsRepository: deepFindPermissionsByUserRoleId(user.userRole.id)
        UserRolePermissionsRepository-->>UserPermissionsServiceImpl: grants with authorities
        Note over UserPermissionsServiceImpl: de-duplicated into a Set<Permission><br/>(the table allows duplicate grants)
    end
    UserDetailsServiceImpl-->>AuthenticationManager: UserDetailsPojo<br/>(all four status flags hardcoded permissive)
    AuthenticationManager->>BCryptPasswordEncoder: matches(raw, storedHash) at configured strength
    alt mismatch
        AuthenticationManager-->>Client: BadCredentials
    end
    AuthenticationManager-->>GenericJwtAuthenticationFilter: successfulAuthentication(authResult)
    GenericJwtAuthenticationFilter->>GenericJwtAuthenticationFilter: expiry = now + days + hours + minutes
    Note over GenericJwtAuthenticationFilter: claims: sub, authorities, iat, exp<br/>no issuer, audience or token id<br/>signed with HMAC key from the secret property
    GenericJwtAuthenticationFilter-->>Client: Authorization: Bearer <token><br/>AND the same value in the response body
```

## Per-request token verification

```mermaid
sequenceDiagram
    actor Client
    participant JwtTokenVerifierFilter
    participant ClaimsAuthorizationHeaderParserServiceImpl
    participant SecurityContext
    participant Endpoint

    Client->>JwtTokenVerifierFilter: any request with/without Authorization
    alt header blank or absent
        JwtTokenVerifierFilter->>Endpoint: continue chain ANONYMOUSLY
    else header present
        JwtTokenVerifierFilter->>JwtTokenVerifierFilter: strip "Bearer " via replace-all
        Note over JwtTokenVerifierFilter: the shared isAValidAuthorizationHeader()<br/>validator exists but is NOT used
        JwtTokenVerifierFilter->>ClaimsAuthorizationHeaderParserServiceImpl: parseToken(token)
        alt signature invalid or malformed
            ClaimsAuthorizationHeaderParserServiceImpl-->>JwtTokenVerifierFilter: IllegalState "Token %s can't be trusted"
            Note over JwtTokenVerifierFilter: logs the FULL TOKEN VALUE<br/>then sets 401 with no body
            JwtTokenVerifierFilter-->>Client: 401 (empty body)
        else parsed
            ClaimsAuthorizationHeaderParserServiceImpl-->>JwtTokenVerifierFilter: Claims
            alt expiration is after now
                JwtTokenVerifierFilter->>JwtTokenVerifierFilter: extractAuthorities from the "authorities" claim<br/>(list of objects, each with an "authority" key)
                JwtTokenVerifierFilter->>SecurityContext: set authentication(subject, authorities)
                JwtTokenVerifierFilter->>Endpoint: continue chain AUTHENTICATED
            else already expired
                Note over JwtTokenVerifierFilter: NO context set, NO error raised
                JwtTokenVerifierFilter->>Endpoint: continue chain ANONYMOUSLY
                Endpoint-->>Client: 403 from the method-level authority check<br/>(client cannot tell "expired" from "absent")
            end
        end
    end
```

## Self-registration (`POST /public/register`)

```mermaid
sequenceDiagram
    actor Visitor
    participant PublicRegisterController
    participant RegistrationServiceImpl
    participant UsersRepository
    participant PeopleConverterServiceImpl
    participant PeopleRepository
    participant UserRolesRepository
    participant BCryptPasswordEncoder
    participant CustomersRepository

    Visitor->>PublicRegisterController: POST /public/register {name, password, profile}
    Note over PublicRegisterController: PUBLIC route;<br/>body and nested profile are validated
    PublicRegisterController->>RegistrationServiceImpl: register(registration)
    RegistrationServiceImpl->>UsersRepository: exists(name == submitted name)
    alt taken
        RegistrationServiceImpl-->>Visitor: 400 EXISTS_01 "That username is taken."
    end
    RegistrationServiceImpl->>PeopleConverterServiceImpl: convertToNewEntity(profile)
    RegistrationServiceImpl->>PeopleRepository: exists(idNumber == profile.idNumber)
    alt already registered
        RegistrationServiceImpl-->>Visitor: 400 EXISTS_01 "That ID number is already registered..."
    end
    rect rgb(255,240,240)
    Note over RegistrationServiceImpl,CustomersRepository: NOT @Transactional - three independent writes
    RegistrationServiceImpl->>PeopleRepository: saveAndFlush(person)
    RegistrationServiceImpl->>UserRolesRepository: findByName("Customer")
    alt role missing
        RegistrationServiceImpl-->>Visitor: 500 IllegalState "No user role matches 'Customer',<br/>database might be compromised"<br/>(person row already persisted)
    end
    RegistrationServiceImpl->>BCryptPasswordEncoder: encode(password)
    RegistrationServiceImpl->>UsersRepository: saveAndFlush(user)
    RegistrationServiceImpl->>CustomersRepository: saveAndFlush(customer for person)
    Note over CustomersRepository: a failure here leaves a person<br/>and an account with NO customer row
    end
    RegistrationServiceImpl-->>Visitor: 200
```

## Authority and route discovery (`GET /access`, `GET /access/{apiRoute}`)

```mermaid
sequenceDiagram
    actor Client
    participant AccessController
    participant ClaimsAuthorizationHeaderParserServiceImpl
    participant UserDetailsServiceImpl
    participant AuthorizedApiServiceImpl

    Client->>AccessController: GET /access (Authorization header)
    Note over AccessController: class-level: isAuthenticated()
    AccessController->>ClaimsAuthorizationHeaderParserServiceImpl: extractAuthorizationHeader(headers)
    alt header missing or unparseable
        AccessController-->>Client: 200 with a NULL body
    end
    AccessController->>AccessController: strip "Bearer " prefix
    AccessController->>ClaimsAuthorizationHeaderParserServiceImpl: parseToken(token)
    AccessController->>UserDetailsServiceImpl: loadUserByUsername(subject)
    alt account missing or illegal state
        AccessController-->>Client: 401 UNAUTHORIZED, empty body<br/>(deliberately no explanation)
    end
    AccessController->>AuthorizedApiServiceImpl: getAuthorizedApiRoutes(userDetails)
    Note over AuthorizedApiServiceImpl: route = authority with everything<br/>from ":" onward removed<br/>("checkout" has no colon -> route "checkout")
    AuthorizedApiServiceImpl-->>AccessController: distinct resource prefixes
    AccessController-->>Client: 200 {routes: [...]}

    Client->>AccessController: GET /access/products
    AccessController->>AuthorizedApiServiceImpl: getAuthorizedApiRouteAccess(userDetails, "products")
    Note over AuthorizedApiServiceImpl: SUBSTRING match, not equality:<br/>route "product" would also match<br/>product_lists:* and product_categories:*
    AuthorizedApiServiceImpl-->>AccessController: action suffixes
    AccessController-->>Client: 200 {permissions: [...]}
```

## Protected-account deletion guard (`DELETE /data/users`)

```mermaid
sequenceDiagram
    actor Administrator
    participant DataUsersController
    participant UsersPredicateServiceImpl
    participant UsersCrudServiceImpl
    participant UsersRepository

    Administrator->>DataUsersController: DELETE /data/users?name=admin
    Note over DataUsersController: authority users:delete
    alt request params empty
        DataUsersController-->>Administrator: 204, nothing deleted
    end
    DataUsersController->>UsersPredicateServiceImpl: parseMap(params)
    DataUsersController->>UsersCrudServiceImpl: delete(filters)
    UsersCrudServiceImpl->>UsersRepository: findOne(filters)
    alt account protection enabled AND matched id == protectedAccountId
        UsersCrudServiceImpl-->>Administrator: 500 AccountProtectionViolation<br/>"Protected account cannot be deleted"<br/>(unmapped by the error advice)
    else filter matches SEVERAL accounts
        Note over UsersCrudServiceImpl: findOne returns no single match,<br/>so the GUARD DOES NOT RUN
        UsersCrudServiceImpl->>UsersRepository: deleteAll(findAll(filters))
        Note over UsersRepository: the protected account can be removed<br/>as part of a multi-row delete
    else single unprotected match
        UsersCrudServiceImpl->>UsersRepository: deleteAll(findAll(filters))
        Note over UsersRepository: the linked people row is LEFT BEHIND
    end
    DataUsersController-->>Administrator: 204 No Content
```
