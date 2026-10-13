# Workflow diagram — Customer (`customer`)

## The three creation paths that converge on one service

```mermaid
flowchart TD
    A1[["Operator:<br/>POST /data/customers<br/>authority customers:create"]] --> Svc
    A2[["Visitor:<br/>POST /public/register<br/>PUBLIC - third write of<br/>a NON-transactional flow"]] --> Svc
    A3[["Visitor:<br/>POST /public/guest<br/>before a token is issued"]] --> Svc
    A4[["Checkout:<br/>POST /public/checkout<br/>via the order converter"]] --> Resolve

    Svc[CustomersCrudServiceImpl.create] --> Blank{idNumber blank?}
    Blank -- yes --> R1[/Bad input:<br/>"Customer does not have an ID card"<br/>guest path maps this to<br/>"Insufficient or invalid profile data for guest"/]
    Blank -- no --> Lookup[findByPersonIdNumber<br/>JOIN FETCH person]
    Lookup --> Exists{customer exists?}
    Exists -- yes --> R2[/Already exists:<br/>"The item already exists"/]
    Exists -- no --> Create[Convert + saveAndFlush;<br/>cascade writes the people row<br/>in the SAME transaction]
    Create --> Done([customer + person persisted])

    R2 --> Who{which caller?}
    Who -- "direct POST" --> Fail400[/400 EXISTS_01 returned/]
    Who -- "guest filter" --> Tolerate[Logged "Guest with idNumber={} is<br/>already registered", flow CONTINUES]
    Tolerate --> Done

    Resolve[OrdersConverterServiceImpl<br/>.convertCustomerInformationForEntity] --> RFound{getExisting hits?}
    RFound -- yes --> Reuse[Reuse the stored customer;<br/>SUBMITTED NAMES DISCARDED]
    RFound -- no --> Create
    Reuse --> Done
```

## Correction resolution: replacement is the only route, patching is unreachable

```mermaid
flowchart TD
    P([PUT /data/customers with a full body.<br/>PATCH is NOT routed: the patch service<br/>below is wired but unreachable]) --> Sel{selecting params present?}
    Sel -- no --> E1[/400 "Missing request params"/]
    Sel -- yes --> Cnt{match count}
    Cnt -- "&gt; 1" --> E2[/500 "Cannot update more than one item at a time"/]
    Cnt -- "0" --> E3[/404 "No element was found to update"/]
    Cnt -- "1" --> Replace[FULL OVERWRITE: body becomes a new<br/>entity, existing id assigned, saved]
    Replace --> Out([204 No Content])
    Copy[/UNREACHABLE patch path below:<br/>copy-construct the customer/] --> Extract[Extract keys beginning with 'person.'<br/>and strip the prefix]
    Extract --> Empty{sub-map empty?}
    Empty -- yes --> NoOp([204: nothing applied.<br/>The customer has NO patchable<br/>field of its own, so unrecognised<br/>keys are silently ignored])
    Empty -- no --> Fields[Person patch semantics]
    Fields --> NB{field is a name,<br/>idNumber or email?}
    NB -- yes --> NBv{value non-blank?}
    NBv -- no --> Skip[Field skipped:<br/>blank never clears]
    NBv -- yes --> Set[Field applied]
    NB -- "no: phone1 / phone2" --> Uncond[Applied UNCONDITIONALLY:<br/>a blank value DOES overwrite]
    Skip --> Eq
    Set --> Eq
    Uncond --> Eq{patched equals existing?}
    Eq -- yes --> NoWrite([204, no UPDATE issued])
    Eq -- no --> Write([204, saveAndFlush])
```

## Deletion reachability

```mermaid
flowchart LR
    Del([DELETE /data/customers]) --> Params{params empty?}
    Params -- yes --> Nothing([204, nothing deleted])
    Params -- no --> Count{match count}
    Count -- "0" --> NF[/404 "Requested item(s) not found"/]
    Count -- "&gt;= 1" --> Ref{any order references<br/>this customer?}
    Ref -- yes --> Blocked[/500 FK_ORDER_CUSTOM_ID violation.<br/>Because orders.customer_id is NOT NULL,<br/>ANY buyer with history is<br/>permanently undeletable./]
    Ref -- no --> Gone([204 customer removed;<br/>the people row SURVIVES as an orphan])
```

## Lifecycle

```mermaid
stateDiagram-v2
    [*] --> Created: direct POST, self-registration,<br/>guest session, or checkout
    Created --> Created: PUT full replacement<br/>(the only correction path:<br/>no PATCH route exists)
    Created --> Referenced: an order points at it<br/>(orders.customer_id is mandatory)
    Referenced --> Referenced: still patchable and replaceable
    Created --> [*]: DELETE while unreferenced<br/>(person row left behind)
    Referenced --> Referenced: DELETE blocked by the foreign key
    note right of Referenced
        No status column exists: there is no
        deactivated, blocked or archived
        customer. A buyer who should no longer
        transact can only be stopped by
        changing or deleting the linked account,
        not by anything in this domain.
    end note
    note right of Created
        A "guest" customer is indistinguishable
        from a registered one at this level -
        guest checkout creates a permanent record.
    end note
```
