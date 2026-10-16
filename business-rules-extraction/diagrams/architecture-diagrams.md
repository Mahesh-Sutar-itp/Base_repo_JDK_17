# Architecture Diagrams

System-wide structural views. Domain-scoped component diagrams live in each
`../detailed-domain-bre/domain-*/diagrams/component-diagram.md`.

## 1. Layered architecture

```mermaid
flowchart TB
    subgraph clients["Clients"]
        Storefront([Storefront / browser])
        Operator([Operator console])
        Provider([Transbank Webpay Plus])
    end

    subgraph filters["Security filter chain (no route-matching rules)"]
        Login["JwtLoginAuthenticationFilter<br/>POST /public/login"]
        Guest["JwtGuestAuthenticationFilter<br/>POST /public/guest"]
        Verify["JwtTokenVerifierFilter<br/>every request"]
    end

    subgraph web["Web layer - 22 controllers, 67 routes"]
        PublicC["Public controllers<br/>about, checkout, receipt, register, root"]
        AccountC["Account controllers<br/>access, account profile"]
        DataC["Data controllers<br/>15 resource controllers"]
        Advice["ExceptionsControllerAdvice<br/>4 mapped conditions"]
    end

    subgraph appsvc["Application services - 7"]
        Checkout["CheckoutServiceImpl"]
        Process["OrdersProcessServiceImpl<br/>THE STATE MACHINE"]
        Receipt["ReceiptServiceImpl"]
        Registration["RegistrationServiceImpl"]
        Profile["ProfileServiceImpl"]
        Company["CompanyServiceImpl"]
        Pagination["PaginationServiceImpl"]
    end

    subgraph persvc["Persistence services - 58"]
        Crud["14 crud services<br/>+ CrudGenericService base"]
        Conv["15 converters<br/>incl. all monetary arithmetic"]
        Patch["14 patch services<br/>5 unreachable"]
        Pred["15 predicate services"]
        Sort["14 sort specifications"]
        Tree["ProductCategoryTreeResolverServiceImpl"]
    end

    subgraph data["Data layer"]
        Repos["23 repositories<br/>paging + predicate execution"]
        Entities["22 entities"]
        DB[(22 tables)]
    end

    subgraph integrations["Outbound adapters - owned by the order domain"]
        Pay["WebpayplusPaymentServiceImpl"]
        Mail["MailgunMailingServiceImpl<br/>profile-gated, OPTIONAL"]
    end

    Storefront --> filters
    Operator --> filters
    filters --> web
    Provider -.->|"public unauthenticated callbacks"| PublicC
    web --> appsvc
    web --> persvc
    appsvc --> persvc
    persvc --> Repos
    Repos --> Entities
    Entities --> DB
    Checkout --> Pay
    PublicC --> Mail
    DataC --> Mail
    Pay --> Provider
    Mail -->|"HTTP"| MG[(Mailgun)]
    web -.-> Advice
```

Note two structural facts visible here: authorization is applied **only** at the
controller-method level (the filter chain declares no route rules), and the two
outbound adapters are reached from both the application-service layer and the web
layer.

## 2. Domain map with coupling styles

```mermaid
flowchart LR
    subgraph tier1["Tier 1 - no outbound domain dependency"]
        companyprofile["company-profile<br/>app_params"]
        person["person<br/>people"]
        image["image<br/>images"]
        prodcat["product-category<br/>product_categories"]
        billing["billing<br/>billing_types<br/>billing_companies"]
        shipper["shipper<br/>shippers"]
    end
    subgraph tier2["Tier 2 - depend on tier 1"]
        customer["customer<br/>customers"]
        salesperson["salesperson<br/>salespeople"]
        product["product<br/>products<br/>product_images"]
    end
    subgraph tier3["Tier 3 - depend on tier 2"]
        prodlist["product-list<br/>product_lists<br/>product_list_items"]
        user["user<br/>app_users + 3"]
    end
    subgraph tier4["Tier 4 - orchestrator"]
        order["order<br/>orders + 4"]
    end

    customer ==>|"ORM cascade"| person
    salesperson ==>|"ORM cascade"| person
    product -->|service| image
    product -->|service| prodcat
    prodlist -->|service| product
    user ==>|"repository + internals"| person
    user -->|service| customer
    order -->|service| customer
    order ==>|repository| product
    order ==>|repository| billing
    order ==>|repository| shipper
    order -.->|"dead edge"| salesperson
```

Legend: thin arrow = service-level call (survives a split unchanged);
thick arrow = repository-level call or ORM cascade (needs a contract);
dotted = modelled but never executed.

`company-profile` has no edge in either direction.

## 3. Request paths by access level

```mermaid
flowchart TB
    Anon([Anonymous caller]) --> P1["GET /"]
    Anon --> P2["GET /public/about"]
    Anon --> P3["POST /public/register"]
    Anon --> P4["GET /public/receipt/{token}"]
    Anon --> P5["GET + POST /public/checkout/validate"]
    Anon --> P6["GET /data/billing_types"]
    Anon --> P7["GET /data/shippers"]
    Anon --> P8["GET /data/products"]
    Anon --> P9["GET /data/product_categories"]
    Anon --> P10["GET /data/product_lists"]
    Anon --> P11["GET /data/product_list_contents"]
    Anon --> L1["POST /public/login"]
    Anon --> L2["POST /public/guest"]

    L1 --> Token{{"Bearer token<br/>subject + authorities<br/>+ iat + exp"}}
    L2 --> Token
    Token --> Buyer([Customer role<br/>1 authority])
    Token --> Staff([Salesperson role<br/>14 authorities])
    Token --> Mgr([Manager role<br/>31 authorities])
    Token --> Adm([Administrator role<br/>54 authorities])

    Buyer --> C1["POST /public/checkout<br/>authority checkout"]
    Buyer --> C2["GET + PUT /account/profile"]
    Buyer --> C3["GET /access<br/>GET /access/{apiRoute}"]
    Staff --> C2
    Staff --> C3
    Staff --> S1["POST + GET /data/orders"]
    Staff --> S2["product writes, people/customer/salespeople reads"]
    Mgr --> C2
    Mgr --> M1["catalogue, image, list, shipper, category writes<br/>order read + update"]
    Adm --> A1["everything, including /data/users<br/>and /data/user_roles"]
```

## 4. Package structure to domain mapping

```mermaid
flowchart LR
    subgraph pkgs["org.trebol.*"]
        api["api/<br/>controllers, models,<br/>services, 4 base classes"]
        jpa["jpa/<br/>entities, repositories,<br/>services, sortspecs"]
        security["security/<br/>filters, services"]
        config["config/<br/>6 property holders,<br/>security, JWT key"]
        common["common/<br/>utils, annotations,<br/>converters, exceptions"]
        payment["payment/<br/>webpayplus"]
        mailing["mailing/<br/>mailgun"]
    end
    subgraph doms["12 domains"]
        d1["order 58 files / 4,243 LOC"]
        d2["user 56 files / 3,404 LOC"]
        d3["product-list 21 / 1,286"]
        d4["billing 24 / 1,095"]
        d5["product 16 / 1,120"]
        d6["product-category 14 / 903"]
        d7["person 14 / 755"]
        d8["image 13 / 691"]
        d9["shipper 13 / 648"]
        d10["salesperson 12 / 664"]
        d11["customer 12 / 657"]
        d12["company-profile 5 / 249"]
    end
    kernel["shared kernel<br/>36 files / 2,085 LOC"]

    api --> doms
    jpa --> doms
    security --> d2
    payment --> d1
    mailing --> d1
    config --> kernel
    config --> d2
    common --> kernel
    api --> kernel
    jpa --> kernel
```

**The packages are organised by technical layer, not by domain.** Every domain's
code is spread across `api/controllers`, `api/models`, `jpa/entities`,
`jpa/repositories` and four `jpa/services/*` subpackages. A first step toward
extraction is repackaging by domain — a pure code-movement exercise with no
behavioural change, and the fastest way to make the boundaries visible in the
source tree.

## 5. The generic CRUD pattern (repeated 11 times)

```mermaid
flowchart LR
    Req([Request on /data/X]) --> Ctl["DataXController<br/>extends DataCrudGenericController"]
    Ctl --> Pag["PaginationService<br/>pageIndex / pageSize"]
    Ctl --> SortP["SortSpecParserService<br/>+ XSortSpec whitelist"]
    Ctl --> PredS["XPredicateService<br/>query params to a filter"]
    Ctl --> Crud["XCrudServiceImpl<br/>extends CrudGenericService"]
    Crud --> Conv["XConverterService<br/>model to/from entity"]
    Crud --> PatchS["XPatchService<br/>sparse change map"]
    Crud --> Repo["XRepository<br/>extends Repository&lt;E&gt;"]
    Repo --> Ent["X entity"]

    Crud -.->|"already exists"| E1[/"400 EXISTS_01"/]
    Crud -.->|"&gt;1 match on update"| E2[/"500 Cannot update more than one item at a time"/]
    Crud -.->|"0 match on delete"| E3[/"404 Requested item(s) not found"/]
    Ctl -.->|"empty params on update"| E4[/"400 Missing request params"/]
    Ctl -.->|"0 match on update"| E5[/"404 No element was found to update"/]
    Ctl -.->|"empty params on delete"| E6([204 - nothing deleted])
```

Eleven domains follow this shape exactly. The deliberate exceptions are worth
knowing:

| Component | Deviation |
|---|---|
| `DataProductListContentsController` | extends **no** base class; inverts the empty-filter delete convention |
| `PublicAboutController` + `CompanyServiceImpl` | opts out of the pattern entirely — no converter, patch, predicate or crud service |
| `ProductsCrudServiceImpl` | overrides four hooks to manage the image-link table |
| `ProductListsCrudServiceImpl` | overrides delete to clean up membership first — the only pre-delete child cleanup in the system |
| `ProductCategoriesCrudServiceImpl` | overrides the patch flush to re-attach the parent |
| `OrdersCrudServiceImpl` | overrides the patch flush to enforce the edit lock, and `readOne` to hydrate addresses and lines |
| `UsersCrudServiceImpl` | overrides delete to enforce the protected-account guard |

## Related documentation

- per-domain component diagrams: `../detailed-domain-bre/domain-*/diagrams/component-diagram.md`
- boundary justification: `../domain-decomposition-overview/bounded-contexts.md`
- coupling detail: `../reference-documentation/coupling-hotspots.md`
