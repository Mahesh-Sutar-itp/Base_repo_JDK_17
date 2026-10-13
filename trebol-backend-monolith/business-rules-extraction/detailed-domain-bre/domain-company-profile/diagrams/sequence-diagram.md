# Sequence diagrams — Company Profile (`company-profile`)

## Public company profile retrieval (`GET /public/about`)

```mermaid
sequenceDiagram
    actor Visitor
    participant PublicAboutController
    participant CompanyServiceImpl
    participant ParamsRepository
    participant Database

    Visitor->>PublicAboutController: GET /public/about
    Note over PublicAboutController: PUBLIC: no authentication,<br/>no authority, no route rules
    PublicAboutController->>CompanyServiceImpl: readDetails()
    CompanyServiceImpl->>ParamsRepository: findParamsByCategory("company")
    ParamsRepository->>Database: SELECT p FROM Param p WHERE p.category = 'company'
    Database-->>ParamsRepository: 0..n Param rows
    ParamsRepository-->>CompanyServiceImpl: Iterable<Param>
    CompanyServiceImpl->>CompanyServiceImpl: new CompanyDetailsPojo()
    loop each Param row
        alt name == "name"
            CompanyServiceImpl->>CompanyServiceImpl: setName(value)
        else name == "description"
            CompanyServiceImpl->>CompanyServiceImpl: setDescription(value)
        else name == "bannerImageURL"
            CompanyServiceImpl->>CompanyServiceImpl: setBannerImageURL(value)
        else name == "logoImageURL"
            CompanyServiceImpl->>CompanyServiceImpl: setLogoImageURL(value)
        else any other name
            Note over CompanyServiceImpl: default branch: ignored.<br/>No error, no log entry.
        end
    end
    CompanyServiceImpl-->>PublicAboutController: CompanyDetailsPojo
    Note over PublicAboutController: Jackson default-property-inclusion=non_default<br/>omits every field that was never assigned
    PublicAboutController-->>Visitor: 200 OK (possibly an empty object)
```

## Degenerate cases

```mermaid
sequenceDiagram
    actor Visitor
    participant PublicAboutController
    participant CompanyServiceImpl
    participant ParamsRepository

    rect rgb(243,243,243)
    Note over Visitor,ParamsRepository: empty app_params table
    Visitor->>PublicAboutController: GET /public/about
    PublicAboutController->>CompanyServiceImpl: readDetails()
    CompanyServiceImpl->>ParamsRepository: findParamsByCategory("company")
    ParamsRepository-->>CompanyServiceImpl: empty iterable
    CompanyServiceImpl-->>PublicAboutController: untouched CompanyDetailsPojo
    PublicAboutController-->>Visitor: 200 OK {}
    end

    rect rgb(248,248,248)
    Note over Visitor,ParamsRepository: mis-keyed row (name = "logoImageUrl")
    Visitor->>PublicAboutController: GET /public/about
    PublicAboutController->>CompanyServiceImpl: readDetails()
    CompanyServiceImpl->>ParamsRepository: findParamsByCategory("company")
    ParamsRepository-->>CompanyServiceImpl: row with unrecognised name
    Note over CompanyServiceImpl: default branch swallows it;<br/>logoImageURL stays unset
    PublicAboutController-->>Visitor: 200 OK, logoImageURL key ABSENT
    end

    rect rgb(243,243,243)
    Note over Visitor,ParamsRepository: rows exist in another category
    Visitor->>PublicAboutController: GET /public/about
    PublicAboutController->>CompanyServiceImpl: readDetails()
    CompanyServiceImpl->>ParamsRepository: findParamsByCategory("company")
    Note over ParamsRepository: rows with category != 'company'<br/>are never selected and are inert
    ParamsRepository-->>CompanyServiceImpl: only 'company' rows
    PublicAboutController-->>Visitor: 200 OK
    end
```

## The absent write path

```mermaid
sequenceDiagram
    actor Operator
    participant NoRoute as "(no controller route)"
    participant Database

    Operator--xNoRoute: PUT / PATCH / POST /public/about
    Note over NoRoute: No write route exists anywhere.<br/>No converter, no patch service,<br/>no CRUD service, no authority code.
    Operator->>Database: direct UPDATE app_params SET param_value = ...<br/>WHERE param_category = 'company' AND param_name = ...
    Note over Database: composite key UK_PARAM and the<br/>500-character value limit apply
    Database-->>Operator: committed
    Note over Operator,Database: effective on the very next request:<br/>nothing is cached
```
