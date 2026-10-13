# Component diagram — Company Profile (`company-profile`)

```mermaid
flowchart TB
    subgraph companyprofile["domain: company-profile"]
        PublicAboutController["PublicAboutController<br/>GET /public/about<br/>(public, no authority)"]
        CompanyService["CompanyService (interface)<br/>readDetails()"]
        CompanyServiceImpl["CompanyServiceImpl<br/>category literal 'company'<br/>name switch, default = ignore"]
        ParamsRepository["ParamsRepository<br/>findParamsByCategory(category)"]
        Param["Param entity<br/>table app_params<br/>EAV: category + name + value"]
        CompanyDetailsPojo["CompanyDetailsPojo<br/>name, description,<br/>bannerImageURL, logoImageURL<br/>(outbound only, no validation)"]

        PublicAboutController --> CompanyService
        CompanyServiceImpl -.implements.-> CompanyService
        CompanyServiceImpl --> ParamsRepository
        ParamsRepository --> Param
        CompanyServiceImpl --> CompanyDetailsPojo
    end

    subgraph kernel["shared kernel (minimal usage)"]
        RepositoryBase["Repository&lt;E&gt;"]
        ExceptionsControllerAdvice["ExceptionsControllerAdvice<br/>(no exception is ever raised here)"]
        JacksonConfig["Jackson configuration<br/>default-property-inclusion=non_default"]
    end

    ParamsRepository -.extends.-> RepositoryBase
    PublicAboutController -.response shaped by.-> JacksonConfig
    PublicAboutController -.errors would be handled by.-> ExceptionsControllerAdvice

    subgraph notused["Shared-kernel components this domain does NOT use"]
        X1["CrudGenericService"]
        X2["DataGenericController / DataCrudGenericController"]
        X3["PaginationService"]
        X4["SortSpecParserService"]
        X5["ConverterService / PatchService / PredicateService"]
    end

    companyprofile -.->|"opts out entirely"| notused

    subgraph others["every other domain"]
        NoConsumer["no domain reads or writes app_params"]
    end

    others -.->|"no edge exists"| Param
```

## Edge summary

| Edge | Direction | Nature |
|---|---|---|
| `company-profile` → shared persistence | outbound | single category query |
| `company-profile` → Jackson configuration | outbound | response field omission behaviour |
| any domain → `company-profile` | — | **none** |
| `app_params` → any other table | — | **none**; no foreign key in either direction |

## Extraction notes

- This is the **lowest-risk extraction in the entire system**: one table, one
  entity, one repository method, one service, one controller, one outbound-only
  transport model, no shared entity, no foreign key, no consumer.
- It is also the only domain that does not inherit from the generic CRUD or
  generic controller base classes, so extracting it does not require untangling
  any framework inheritance.
- The absent write surface is the main design decision a rewrite must make. Two
  reasonable targets: keep the EAV table and add a guarded write route with a new
  authority code, or promote the four branding values to typed configuration and
  retire the table. The existing code supports neither; both are new work.
- Because nothing is cached and the endpoint is public and high-traffic, this is
  the first place a rewrite should introduce caching — but note that no caching
  pattern exists in the current system to follow.
