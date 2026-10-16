# Service / Component Inventory

Flat inventory of every service, controller, filter and resolver component in
`src/main/java/org/trebol/`, in the form
**[ComponentName — interface file — implementation file — package]**, with its
owning domain.

Total entries: 106. Components with no separate interface show `—`.

| Component | Interface file | Implementation file | Package | Domain |
|---|---|---|---|---|
| `AccessController` | — | `api/controllers/AccessController.java` | `org/trebol/api/controllers` | `user` |
| `AccountProfileController` | — | `api/controllers/AccountProfileController.java` | `org/trebol/api/controllers` | `user` |
| `AddressesConverterServiceImpl` | `jpa/services/conversion/AddressesConverterService.java` | `jpa/services/conversion/impl/AddressesConverterServiceImpl.java` | `org/trebol/jpa/services/conversion/impl` | `order` |
| `AuthorizedApiServiceImpl` | `security/services/AuthorizedApiService.java` | `security/services/impl/AuthorizedApiServiceImpl.java` | `org/trebol/security/services/impl` | `user` |
| `BillingCompaniesConverterServiceImpl` | `jpa/services/conversion/BillingCompaniesConverterService.java` | `jpa/services/conversion/impl/BillingCompaniesConverterServiceImpl.java` | `org/trebol/jpa/services/conversion/impl` | `billing` |
| `BillingCompaniesCrudServiceImpl` | `jpa/services/crud/BillingCompaniesCrudService.java` | `jpa/services/crud/impl/BillingCompaniesCrudServiceImpl.java` | `org/trebol/jpa/services/crud/impl` | `billing` |
| `BillingCompaniesPatchServiceImpl` | `jpa/services/patch/BillingCompaniesPatchService.java` | `jpa/services/patch/impl/BillingCompaniesPatchServiceImpl.java` | `org/trebol/jpa/services/patch/impl` | `billing` |
| `BillingCompaniesPredicateServiceImpl` | `jpa/services/predicates/BillingCompaniesPredicateService.java` | `jpa/services/predicates/impl/BillingCompaniesPredicateServiceImpl.java` | `org/trebol/jpa/services/predicates/impl` | `billing` |
| `BillingTypesConverterServiceImpl` | `jpa/services/conversion/BillingTypesConverterService.java` | `jpa/services/conversion/impl/BillingTypesConverterServiceImpl.java` | `org/trebol/jpa/services/conversion/impl` | `billing` |
| `BillingTypesCrudServiceImpl` | `jpa/services/crud/BillingTypesCrudService.java` | `jpa/services/crud/impl/BillingTypesCrudServiceImpl.java` | `org/trebol/jpa/services/crud/impl` | `billing` |
| `BillingTypesPatchServiceImpl` | `jpa/services/patch/BillingTypesPatchService.java` | `jpa/services/patch/impl/BillingTypesPatchServiceImpl.java` | `org/trebol/jpa/services/patch/impl` | `billing` |
| `BillingTypesPredicateServiceImpl` | `jpa/services/predicates/BillingTypesPredicateService.java` | `jpa/services/predicates/impl/BillingTypesPredicateServiceImpl.java` | `org/trebol/jpa/services/predicates/impl` | `billing` |
| `CheckoutServiceImpl` | `api/services/CheckoutService.java` | `api/services/impl/CheckoutServiceImpl.java` | `org/trebol/api/services/impl` | `order` |
| `ClaimsAuthorizationHeaderParserServiceImpl` | `security/services/AuthorizationHeaderParserService.java` | `security/services/impl/ClaimsAuthorizationHeaderParserServiceImpl.java` | `org/trebol/security/services/impl` | `user` |
| `CompanyServiceImpl` | `api/services/CompanyService.java` | `api/services/impl/CompanyServiceImpl.java` | `org/trebol/api/services/impl` | `company-profile` |
| `CrudGenericService` | `jpa/services/CrudService.java` | `jpa/services/crud/CrudGenericService.java` | `org/trebol/jpa/services/crud` | `(shared kernel)` |
| `CustomersConverterServiceImpl` | `jpa/services/conversion/CustomersConverterService.java` | `jpa/services/conversion/impl/CustomersConverterServiceImpl.java` | `org/trebol/jpa/services/conversion/impl` | `customer` |
| `CustomersCrudServiceImpl` | `jpa/services/crud/CustomersCrudService.java` | `jpa/services/crud/impl/CustomersCrudServiceImpl.java` | `org/trebol/jpa/services/crud/impl` | `customer` |
| `CustomersPatchServiceImpl` | `jpa/services/patch/CustomersPatchService.java` | `jpa/services/patch/impl/CustomersPatchServiceImpl.java` | `org/trebol/jpa/services/patch/impl` | `customer` |
| `CustomersPredicateServiceImpl` | `jpa/services/predicates/CustomersPredicateService.java` | `jpa/services/predicates/impl/CustomersPredicateServiceImpl.java` | `org/trebol/jpa/services/predicates/impl` | `customer` |
| `DataBillingTypesController` | — | `api/controllers/DataBillingTypesController.java` | `org/trebol/api/controllers` | `billing` |
| `DataCrudGenericController` | — | `api/DataCrudGenericController.java` | `org/trebol/api` | `(shared kernel)` |
| `DataCustomersController` | — | `api/controllers/DataCustomersController.java` | `org/trebol/api/controllers` | `customer` |
| `DataGenericController` | `api/DataController.java` | `api/DataGenericController.java` | `org/trebol/api` | `(shared kernel)` |
| `DataImagesController` | — | `api/controllers/DataImagesController.java` | `org/trebol/api/controllers` | `image` |
| `DataOrderStatusesController` | — | `api/controllers/DataOrderStatusesController.java` | `org/trebol/api/controllers` | `order` |
| `DataOrdersController` | — | `api/controllers/DataOrdersController.java` | `org/trebol/api/controllers` | `order` |
| `DataPeopleController` | — | `api/controllers/DataPeopleController.java` | `org/trebol/api/controllers` | `person` |
| `DataProductCategoriesController` | — | `api/controllers/DataProductCategoriesController.java` | `org/trebol/api/controllers` | `product-category` |
| `DataProductListContentsController` | — | `api/controllers/DataProductListContentsController.java` | `org/trebol/api/controllers` | `product-list` |
| `DataProductListsController` | — | `api/controllers/DataProductListsController.java` | `org/trebol/api/controllers` | `product-list` |
| `DataProductsController` | — | `api/controllers/DataProductsController.java` | `org/trebol/api/controllers` | `product` |
| `DataSalespeopleController` | — | `api/controllers/DataSalespeopleController.java` | `org/trebol/api/controllers` | `salesperson` |
| `DataShippersController` | — | `api/controllers/DataShippersController.java` | `org/trebol/api/controllers` | `shipper` |
| `DataUserRolesController` | — | `api/controllers/DataUserRolesController.java` | `org/trebol/api/controllers` | `user` |
| `DataUsersController` | — | `api/controllers/DataUsersController.java` | `org/trebol/api/controllers` | `user` |
| `ExceptionsControllerAdvice` | — | `api/ExceptionsControllerAdvice.java` | `org/trebol/api` | `(shared kernel)` |
| `GenericJwtAuthenticationFilter` | — | `security/GenericJwtAuthenticationFilter.java` | `org/trebol/security` | `user` |
| `ImagesConverterServiceImpl` | `jpa/services/conversion/ImagesConverterService.java` | `jpa/services/conversion/impl/ImagesConverterServiceImpl.java` | `org/trebol/jpa/services/conversion/impl` | `image` |
| `ImagesCrudServiceImpl` | `jpa/services/crud/ImagesCrudService.java` | `jpa/services/crud/impl/ImagesCrudServiceImpl.java` | `org/trebol/jpa/services/crud/impl` | `image` |
| `ImagesPatchServiceImpl` | `jpa/services/patch/ImagesPatchService.java` | `jpa/services/patch/impl/ImagesPatchServiceImpl.java` | `org/trebol/jpa/services/patch/impl` | `image` |
| `ImagesPredicateServiceImpl` | `jpa/services/predicates/ImagesPredicateService.java` | `jpa/services/predicates/impl/ImagesPredicateServiceImpl.java` | `org/trebol/jpa/services/predicates/impl` | `image` |
| `JwtGuestAuthenticationFilter` | — | `security/JwtGuestAuthenticationFilter.java` | `org/trebol/security` | `user` |
| `JwtLoginAuthenticationFilter` | — | `security/JwtLoginAuthenticationFilter.java` | `org/trebol/security` | `user` |
| `JwtTokenVerifierFilter` | — | `security/JwtTokenVerifierFilter.java` | `org/trebol/security` | `user` |
| `MailgunMailingServiceImpl` | `mailing/MailingService.java` | `mailing/impl/mailgun/MailgunMailingServiceImpl.java` | `org/trebol/mailing/impl/mailgun` | `order` |
| `MailingServiceException` | — | `mailing/MailingServiceException.java` | `org/trebol/mailing` | `order` |
| `OrderStatusesConverterServiceImpl` | `jpa/services/conversion/OrderStatusesConverterService.java` | `jpa/services/conversion/impl/OrderStatusesConverterServiceImpl.java` | `org/trebol/jpa/services/conversion/impl` | `order` |
| `OrderStatusesCrudServiceImpl` | `jpa/services/crud/OrderStatusesCrudService.java` | `jpa/services/crud/impl/OrderStatusesCrudServiceImpl.java` | `org/trebol/jpa/services/crud/impl` | `order` |
| `OrderStatusesPatchServiceImpl` | `jpa/services/patch/OrderStatusesPatchService.java` | `jpa/services/patch/impl/OrderStatusesPatchServiceImpl.java` | `org/trebol/jpa/services/patch/impl` | `order` |
| `OrderStatusesPredicateServiceImpl` | `jpa/services/predicates/OrderStatusesPredicateService.java` | `jpa/services/predicates/impl/OrderStatusesPredicateServiceImpl.java` | `org/trebol/jpa/services/predicates/impl` | `order` |
| `OrdersConverterServiceImpl` | `jpa/services/conversion/OrdersConverterService.java` | `jpa/services/conversion/impl/OrdersConverterServiceImpl.java` | `org/trebol/jpa/services/conversion/impl` | `order` |
| `OrdersCrudServiceImpl` | `jpa/services/crud/OrdersCrudService.java` | `jpa/services/crud/impl/OrdersCrudServiceImpl.java` | `org/trebol/jpa/services/crud/impl` | `order` |
| `OrdersPatchServiceImpl` | `jpa/services/patch/OrdersPatchService.java` | `jpa/services/patch/impl/OrdersPatchServiceImpl.java` | `org/trebol/jpa/services/patch/impl` | `order` |
| `OrdersPredicateServiceImpl` | `jpa/services/predicates/OrdersPredicateService.java` | `jpa/services/predicates/impl/OrdersPredicateServiceImpl.java` | `org/trebol/jpa/services/predicates/impl` | `order` |
| `OrdersProcessServiceImpl` | `api/services/OrdersProcessService.java` | `api/services/impl/OrdersProcessServiceImpl.java` | `org/trebol/api/services/impl` | `order` |
| `PaginationServiceImpl` | `api/services/PaginationService.java` | `api/services/impl/PaginationServiceImpl.java` | `org/trebol/api/services/impl` | `(shared kernel)` |
| `PaymentServiceException` | — | `payment/PaymentServiceException.java` | `org/trebol/payment` | `order` |
| `PeopleConverterServiceImpl` | `jpa/services/conversion/PeopleConverterService.java` | `jpa/services/conversion/impl/PeopleConverterServiceImpl.java` | `org/trebol/jpa/services/conversion/impl` | `person` |
| `PeopleCrudServiceImpl` | `jpa/services/crud/PeopleCrudService.java` | `jpa/services/crud/impl/PeopleCrudServiceImpl.java` | `org/trebol/jpa/services/crud/impl` | `person` |
| `PeoplePatchServiceImpl` | `jpa/services/patch/PeoplePatchService.java` | `jpa/services/patch/impl/PeoplePatchServiceImpl.java` | `org/trebol/jpa/services/patch/impl` | `person` |
| `PeoplePredicateServiceImpl` | `jpa/services/predicates/PeoplePredicateService.java` | `jpa/services/predicates/impl/PeoplePredicateServiceImpl.java` | `org/trebol/jpa/services/predicates/impl` | `person` |
| `ProductCategoriesConverterServiceImpl` | `jpa/services/conversion/ProductCategoriesConverterService.java` | `jpa/services/conversion/impl/ProductCategoriesConverterServiceImpl.java` | `org/trebol/jpa/services/conversion/impl` | `product-category` |
| `ProductCategoriesCrudServiceImpl` | `jpa/services/crud/ProductCategoriesCrudService.java` | `jpa/services/crud/impl/ProductCategoriesCrudServiceImpl.java` | `org/trebol/jpa/services/crud/impl` | `product-category` |
| `ProductCategoriesPatchServiceImpl` | `jpa/services/patch/ProductCategoriesPatchService.java` | `jpa/services/patch/impl/ProductCategoriesPatchServiceImpl.java` | `org/trebol/jpa/services/patch/impl` | `product-category` |
| `ProductCategoriesPredicateServiceImpl` | `jpa/services/predicates/ProductCategoriesPredicateService.java` | `jpa/services/predicates/impl/ProductCategoriesPredicateServiceImpl.java` | `org/trebol/jpa/services/predicates/impl` | `product-category` |
| `ProductCategoryTreeResolverServiceImpl` | `jpa/services/ProductCategoryTreeResolverService.java` | `jpa/services/impl/ProductCategoryTreeResolverServiceImpl.java` | `org/trebol/jpa/services/impl` | `product-category` |
| `ProductListConverterServiceImpl` | `jpa/services/conversion/ProductListsConverterService.java` | `jpa/services/conversion/impl/ProductListConverterServiceImpl.java` | `org/trebol/jpa/services/conversion/impl` | `product-list` |
| `ProductListItemsConverterServiceImpl` | `jpa/services/conversion/ProductListItemsConverterService.java` | `jpa/services/conversion/impl/ProductListItemsConverterServiceImpl.java` | `org/trebol/jpa/services/conversion/impl` | `product-list` |
| `ProductListItemsPredicateServiceImpl` | `jpa/services/predicates/ProductListItemsPredicateService.java` | `jpa/services/predicates/impl/ProductListItemsPredicateServiceImpl.java` | `org/trebol/jpa/services/predicates/impl` | `product-list` |
| `ProductListPatchServiceImpl` | `jpa/services/patch/ProductListsPatchService.java` | `jpa/services/patch/impl/ProductListPatchServiceImpl.java` | `org/trebol/jpa/services/patch/impl` | `product-list` |
| `ProductListsCrudServiceImpl` | `jpa/services/crud/ProductListCrudService.java` | `jpa/services/crud/impl/ProductListsCrudServiceImpl.java` | `org/trebol/jpa/services/crud/impl` | `product-list` |
| `ProductListsPredicateServiceImpl` | `jpa/services/predicates/ProductListsPredicateService.java` | `jpa/services/predicates/impl/ProductListsPredicateServiceImpl.java` | `org/trebol/jpa/services/predicates/impl` | `product-list` |
| `ProductsConverterServiceImpl` | `jpa/services/conversion/ProductsConverterService.java` | `jpa/services/conversion/impl/ProductsConverterServiceImpl.java` | `org/trebol/jpa/services/conversion/impl` | `product` |
| `ProductsCrudServiceImpl` | `jpa/services/crud/ProductsCrudService.java` | `jpa/services/crud/impl/ProductsCrudServiceImpl.java` | `org/trebol/jpa/services/crud/impl` | `product` |
| `ProductsPatchServiceImpl` | `jpa/services/patch/ProductsPatchService.java` | `jpa/services/patch/impl/ProductsPatchServiceImpl.java` | `org/trebol/jpa/services/patch/impl` | `product` |
| `ProductsPredicateServiceImpl` | `jpa/services/predicates/ProductsPredicateService.java` | `jpa/services/predicates/impl/ProductsPredicateServiceImpl.java` | `org/trebol/jpa/services/predicates/impl` | `product` |
| `ProfileServiceImpl` | `api/services/ProfileService.java` | `api/services/impl/ProfileServiceImpl.java` | `org/trebol/api/services/impl` | `user` |
| `PublicAboutController` | — | `api/controllers/PublicAboutController.java` | `org/trebol/api/controllers` | `(shared kernel)` |
| `PublicCheckoutController` | — | `api/controllers/PublicCheckoutController.java` | `org/trebol/api/controllers` | `order` |
| `PublicReceiptController` | — | `api/controllers/PublicReceiptController.java` | `org/trebol/api/controllers` | `order` |
| `PublicRegisterController` | — | `api/controllers/PublicRegisterController.java` | `org/trebol/api/controllers` | `(shared kernel)` |
| `ReceiptServiceImpl` | `api/services/ReceiptService.java` | `api/services/impl/ReceiptServiceImpl.java` | `org/trebol/api/services/impl` | `order` |
| `RegexMatcherAdapterServiceImpl` | `common/services/RegexMatcherAdapterService.java` | `common/services/impl/RegexMatcherAdapterServiceImpl.java` | `org/trebol/common/services/impl` | `(shared kernel)` |
| `RegistrationServiceImpl` | `api/services/RegistrationService.java` | `api/services/impl/RegistrationServiceImpl.java` | `org/trebol/api/services/impl` | `user` |
| `RootController` | — | `api/controllers/RootController.java` | `org/trebol/api/controllers` | `(shared kernel)` |
| `SalespeopleConverterServiceImpl` | `jpa/services/conversion/SalespeopleConverterService.java` | `jpa/services/conversion/impl/SalespeopleConverterServiceImpl.java` | `org/trebol/jpa/services/conversion/impl` | `salesperson` |
| `SalespeopleCrudServiceImpl` | `jpa/services/crud/SalespeopleCrudService.java` | `jpa/services/crud/impl/SalespeopleCrudServiceImpl.java` | `org/trebol/jpa/services/crud/impl` | `salesperson` |
| `SalespeoplePatchServiceImpl` | `jpa/services/patch/SalespeoplePatchService.java` | `jpa/services/patch/impl/SalespeoplePatchServiceImpl.java` | `org/trebol/jpa/services/patch/impl` | `salesperson` |
| `SalespeoplePredicateServiceImpl` | `jpa/services/predicates/SalespeoplePredicateService.java` | `jpa/services/predicates/impl/SalespeoplePredicateServiceImpl.java` | `org/trebol/jpa/services/predicates/impl` | `salesperson` |
| `ShippersConverterServiceImpl` | `jpa/services/conversion/ShippersConverterService.java` | `jpa/services/conversion/impl/ShippersConverterServiceImpl.java` | `org/trebol/jpa/services/conversion/impl` | `shipper` |
| `ShippersCrudServiceImpl` | `jpa/services/crud/ShippersCrudService.java` | `jpa/services/crud/impl/ShippersCrudServiceImpl.java` | `org/trebol/jpa/services/crud/impl` | `shipper` |
| `ShippersPatchServiceImpl` | `jpa/services/patch/ShippersPatchService.java` | `jpa/services/patch/impl/ShippersPatchServiceImpl.java` | `org/trebol/jpa/services/patch/impl` | `shipper` |
| `ShippersPredicateServiceImpl` | `jpa/services/predicates/ShippersPredicateService.java` | `jpa/services/predicates/impl/ShippersPredicateServiceImpl.java` | `org/trebol/jpa/services/predicates/impl` | `shipper` |
| `SortSpecParserServiceImpl` | `jpa/services/SortSpecParserService.java` | `jpa/services/impl/SortSpecParserServiceImpl.java` | `org/trebol/jpa/services/impl` | `(shared kernel)` |
| `UserDetailsServiceImpl` | — | `security/UserDetailsServiceImpl.java` | `org/trebol/security` | `user` |
| `UserPermissionsServiceImpl` | `security/services/UserPermissionsService.java` | `security/services/impl/UserPermissionsServiceImpl.java` | `org/trebol/security/services/impl` | `user` |
| `UserRolesConverterServiceImpl` | `jpa/services/conversion/UserRolesConverterService.java` | `jpa/services/conversion/impl/UserRolesConverterServiceImpl.java` | `org/trebol/jpa/services/conversion/impl` | `user` |
| `UserRolesCrudServiceImpl` | `jpa/services/crud/UserRolesCrudService.java` | `jpa/services/crud/impl/UserRolesCrudServiceImpl.java` | `org/trebol/jpa/services/crud/impl` | `user` |
| `UserRolesPatchServiceImpl` | `jpa/services/patch/UserRolesPatchService.java` | `jpa/services/patch/impl/UserRolesPatchServiceImpl.java` | `org/trebol/jpa/services/patch/impl` | `user` |
| `UserRolesPredicateServiceImpl` | `jpa/services/predicates/UserRolesPredicateService.java` | `jpa/services/predicates/impl/UserRolesPredicateServiceImpl.java` | `org/trebol/jpa/services/predicates/impl` | `user` |
| `UsersConverterServiceImpl` | `jpa/services/conversion/UsersConverterService.java` | `jpa/services/conversion/impl/UsersConverterServiceImpl.java` | `org/trebol/jpa/services/conversion/impl` | `user` |
| `UsersCrudServiceImpl` | `jpa/services/crud/UsersCrudService.java` | `jpa/services/crud/impl/UsersCrudServiceImpl.java` | `org/trebol/jpa/services/crud/impl` | `user` |
| `UsersPatchServiceImpl` | `jpa/services/patch/UsersPatchService.java` | `jpa/services/patch/impl/UsersPatchServiceImpl.java` | `org/trebol/jpa/services/patch/impl` | `user` |
| `UsersPredicateServiceImpl` | `jpa/services/predicates/UsersPredicateService.java` | `jpa/services/predicates/impl/UsersPredicateServiceImpl.java` | `org/trebol/jpa/services/predicates/impl` | `user` |
| `WebpayplusPaymentServiceImpl` | `payment/PaymentService.java` | `payment/impl/webpayplus/WebpayplusPaymentServiceImpl.java` | `org/trebol/payment/impl/webpayplus` | `order` |

## Interface-to-implementation coverage

| Layer | Interfaces | Implementations | Notes |
|---|---|---|---|
| crud services | 15 (`jpa/services/crud/`) | 14 (`jpa/services/crud/impl/`) + `CrudGenericService` base | one interface (`CrudGenericService`) is the abstract base, not a separate contract |
| converter services | 16 (`jpa/services/conversion/`) | 15 (`jpa/services/conversion/impl/`) | the implementation for product lists is named singular (`ProductListConverterServiceImpl`) against a plural interface |
| patch services | 14 (`jpa/services/patch/`) | 14 (`jpa/services/patch/impl/`) | five are **wired but unreachable** — no route exposes them |
| predicate services | 15 (`jpa/services/predicates/`) | 15 (`jpa/services/predicates/impl/`) | — |
| API services | 7 (`api/services/`) | 7 (`api/services/impl/`) | checkout, company, orders-process, pagination, profile, receipt, registration |
| security services | 3 (`security/services/`) | 3 (`security/services/impl/`) | authorization-header parser, authorized-API, user-permissions |
| common services | 1 (`common/services/`) | 1 (`common/services/impl/`) | the regular-expression adapter |
| integration adapters | 2 (`mailing/`, `payment/`) | 2 (`mailing/impl/mailgun/`, `payment/impl/webpayplus/`) | both profile- or configuration-gated |
| tree resolver | 1 (`jpa/services/`) | 1 (`jpa/services/impl/`) | the category descendant resolver |
| sort-spec parser | 1 (`jpa/services/`) | 1 (`jpa/services/impl/`) | — |

Every service in the codebase is interface-backed except the controllers and the
filters. That is a material asset for a rewrite: each contract is already
declared separately from its implementation, so a domain can be re-implemented
behind its existing interface.

## Components with no interface

| Component | Reason |
|---|---|
| all 22 controllers | framework entry points |
| `GenericJwtAuthenticationFilter`, `JwtLoginAuthenticationFilter`, `JwtGuestAuthenticationFilter`, `JwtTokenVerifierFilter` | framework filters; the first is an abstract base |
| `UserDetailsServiceImpl` | implements a framework-provided contract rather than an application one |
| `PhoneNumberValidator` | implements a framework validation contract; **applied to zero fields** |
| `CorsConfigurationSourceBuilder` | a plain builder; **never wired into the filter chain** |

## Unreachable components (wired beans with no caller)

| Component | Why |
|---|---|
| `CustomersPatchServiceImpl` | no `PATCH /data/customers` route |
| `SalespeoplePatchServiceImpl` | no `PATCH /data/salespeople` route |
| `UsersPatchServiceImpl` | no `PATCH /data/users` route |
| `UserRolesPatchServiceImpl` | no `PATCH /data/user_roles` route |
| `BillingCompaniesCrudServiceImpl`, `BillingCompaniesConverterServiceImpl`, `BillingCompaniesPatchServiceImpl`, `BillingCompaniesPredicateServiceImpl` | no controller references any billing-company bean |
| `BillingTypesPatchServiceImpl` | the billing-type controller exposes a read method only |
| `OrderStatusesPatchServiceImpl` | the order-status controller exposes a read method only |
| `PeoplePatchServiceImpl` (direct use) | reachable only indirectly, through the profile service and the customer/salesperson patch services |
| `CorsConfigurationSourceBuilder` | referenced by no bean at all |
| `PhoneNumberValidator` | its annotation is applied nowhere |

See `dead-code-decomposition-notes.md` for the boundary-relevant subset.

## Test counterparts

108 test classes mirror this inventory, named by appending `Test` to the
implementation name — for example `OrdersProcessServiceImplTest` for the order
state machine. Tests are predominantly plain unit tests with mocks; one
integration-style test exists, and there are no web-layer or persistence-layer
slice tests and no test resource directory.

Nine non-test files live alongside them: a security test configuration and eight
fixture helpers in `src/test/java/org/trebol/testing/`.

## Related documentation

- per-domain component tables: each `../detailed-domain-bre/domain-*/overview.md`
- shared-kernel components: `../domain-decomposition-overview/shared-kernel.md`
- the proposed service boundaries: `../domain-decomposition-overview/service-*.md`

