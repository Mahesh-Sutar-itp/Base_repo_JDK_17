# Traceability Matrix

Maps each domain's rule and feature identifiers to the source files that
implement them. Use this to find the code behind a rule, or the rules affected by
a file.

Paths are relative to `src/main/java/org/trebol/`.

## Domain → rule range → primary source files

| Domain | Rule IDs | Primary source files |
|---|---|---|
| `person` | `BR-PERSON-001` … `013` | `jpa/entities/Person.java`, `jpa/services/crud/impl/PeopleCrudServiceImpl.java`, `jpa/services/patch/impl/PeoplePatchServiceImpl.java`, `jpa/services/predicates/impl/PeoplePredicateServiceImpl.java`, `jpa/sortspecs/PeopleSortSpec.java`, `jpa/repositories/PeopleRepository.java`, `api/controllers/DataPeopleController.java`, `api/models/PersonPojo.java` |
| `image` | `BR-IMAGE-001` … `015` | `jpa/entities/Image.java`, `jpa/services/crud/impl/ImagesCrudServiceImpl.java`, `jpa/services/patch/impl/ImagesPatchServiceImpl.java`, `jpa/services/predicates/impl/ImagesPredicateServiceImpl.java`, `jpa/sortspecs/ImagesSortSpec.java`, `jpa/repositories/ImagesRepository.java`, `api/controllers/DataImagesController.java`, `api/models/ImagePojo.java` |
| `product-category` | `BR-PRODCAT-001` … `020` | `jpa/entities/ProductCategory.java`, `jpa/services/crud/impl/ProductCategoriesCrudServiceImpl.java`, `jpa/services/impl/ProductCategoryTreeResolverServiceImpl.java`, `jpa/services/patch/impl/ProductCategoriesPatchServiceImpl.java`, `jpa/services/predicates/impl/ProductCategoriesPredicateServiceImpl.java`, `jpa/repositories/ProductsCategoriesRepository.java`, `api/controllers/DataProductCategoriesController.java`, `config/ApiProperties.java` |
| `billing` | `BR-BILLING-001` … `019` | `jpa/entities/BillingType.java`, `jpa/entities/BillingCompany.java`, `jpa/services/crud/impl/BillingTypesCrudServiceImpl.java`, `jpa/services/crud/impl/BillingCompaniesCrudServiceImpl.java`, `jpa/repositories/BillingTypesRepository.java`, `jpa/repositories/BillingCompaniesRepository.java`, `api/controllers/DataBillingTypesController.java`, `config/Constants.java`, `jpa/services/conversion/impl/OrdersConverterServiceImpl.java` |
| `shipper` | `BR-SHIPPER-001` … `015` | `jpa/entities/Shipper.java`, `jpa/services/crud/impl/ShippersCrudServiceImpl.java`, `jpa/services/patch/impl/ShippersPatchServiceImpl.java`, `jpa/repositories/ShippersRepository.java`, `api/controllers/DataShippersController.java`, `jpa/services/conversion/impl/OrdersConverterServiceImpl.java`, `jpa/services/patch/impl/OrdersPatchServiceImpl.java` |
| `company-profile` | `BR-COMPANY-001` … `013` | `jpa/entities/Param.java`, `api/services/impl/CompanyServiceImpl.java`, `jpa/repositories/ParamsRepository.java`, `api/controllers/PublicAboutController.java`, `api/models/CompanyDetailsPojo.java`, `src/main/resources/application.properties` |
| `user` | `BR-USER-001` … `055` | `jpa/entities/User.java`, `UserRole.java`, `Permission.java`, `UserRolePermission.java`, `security/*` (all 9 files), `security/services/impl/*` (3 files), `api/services/impl/RegistrationServiceImpl.java`, `api/services/impl/ProfileServiceImpl.java`, `jpa/services/crud/impl/UsersCrudServiceImpl.java`, `jpa/services/crud/impl/UserRolesCrudServiceImpl.java`, `api/controllers/{AccessController,AccountProfileController,PublicRegisterController,DataUsersController,DataUserRolesController}.java`, `config/{SecurityConfig,SecurityProperties,JwtKeyConfig}.java` |
| `customer` | `BR-CUSTOMER-001` … `022` | `jpa/entities/Customer.java`, `jpa/services/crud/impl/CustomersCrudServiceImpl.java`, `jpa/services/patch/impl/CustomersPatchServiceImpl.java`, `jpa/services/predicates/impl/CustomersPredicateServiceImpl.java`, `jpa/repositories/CustomersRepository.java`, `api/controllers/DataCustomersController.java`, `common/Utils.java`, `security/JwtGuestAuthenticationFilter.java` |
| `salesperson` | `BR-SALESPERSON-001` … `023` | `jpa/entities/Salesperson.java`, `jpa/services/crud/impl/SalespeopleCrudServiceImpl.java`, `jpa/services/patch/impl/SalespeoplePatchServiceImpl.java`, `jpa/services/predicates/impl/SalespeoplePredicateServiceImpl.java`, `jpa/repositories/SalespeopleRepository.java`, `api/controllers/DataSalespeopleController.java`, `jpa/entities/Order.java` |
| `product` | `BR-PRODUCT-001` … `032` | `jpa/entities/Product.java`, `jpa/entities/ProductImage.java`, `jpa/services/crud/impl/ProductsCrudServiceImpl.java`, `jpa/services/patch/impl/ProductsPatchServiceImpl.java`, `jpa/services/predicates/impl/ProductsPredicateServiceImpl.java`, `jpa/repositories/ProductsRepository.java`, `jpa/repositories/ProductImagesRepository.java`, `api/controllers/DataProductsController.java`, `api/models/ProductPojo.java` |
| `product-list` | `BR-PRODLIST-001` … `030` | `jpa/entities/ProductList.java`, `jpa/entities/ProductListItem.java`, `jpa/services/crud/impl/ProductListsCrudServiceImpl.java`, `jpa/services/patch/impl/ProductListPatchServiceImpl.java`, `jpa/services/predicates/impl/ProductListsPredicateServiceImpl.java`, `jpa/services/predicates/impl/ProductListItemsPredicateServiceImpl.java`, `jpa/repositories/ProductListsRepository.java`, `jpa/repositories/ProductListItemsRepository.java`, `api/controllers/DataProductListsController.java`, `api/controllers/DataProductListContentsController.java` |
| `order` | `BR-ORDER-001` … `070` | `jpa/entities/{Order,OrderDetail,OrderStatus,Address,PaymentType}.java`, `api/services/impl/{CheckoutServiceImpl,OrdersProcessServiceImpl,ReceiptServiceImpl}.java`, `jpa/services/crud/impl/OrdersCrudServiceImpl.java`, `jpa/services/conversion/impl/OrdersConverterServiceImpl.java`, `jpa/services/patch/impl/OrdersPatchServiceImpl.java`, `jpa/repositories/{OrdersRepository,OrderDetailsRepository,OrderStatusesRepository,AddressesRepository,PaymentTypesRepository}.java`, `api/controllers/{DataOrdersController,DataOrderStatusesController,PublicCheckoutController,PublicReceiptController}.java`, `payment/impl/webpayplus/*`, `mailing/impl/mailgun/*`, `common/converters/*`, `config/{ApiProperties,Constants}.java` |

## Source file → rules it governs (the load-bearing files)

| Source file | Rules |
|---|---|
| `api/services/impl/OrdersProcessServiceImpl.java` | `BR-ORDER-020` … `026` |
| `jpa/services/conversion/impl/OrdersConverterServiceImpl.java` | `BR-ORDER-001`, `004` … `018`; `BR-BILLING-002` … `008`; `BR-SHIPPER-015`; `BR-PRODUCT-009` … `011`; `BR-CUSTOMER-019` |
| `jpa/services/crud/impl/OrdersCrudServiceImpl.java` | `BR-ORDER-023`, `029` … `031`, `069` |
| `jpa/services/patch/impl/OrdersPatchServiceImpl.java` | `BR-ORDER-028`, `032`, `033`; `BR-SHIPPER-009`, `015` |
| `payment/impl/webpayplus/WebpayplusPaymentServiceImpl.java` | `BR-ORDER-034` … `038` |
| `api/services/impl/CheckoutServiceImpl.java` | `BR-ORDER-038`, `039`, `041`, `042` |
| `api/controllers/PublicCheckoutController.java` | `BR-ORDER-040`, `042`, `043`, `052`, `062` |
| `mailing/impl/mailgun/MailgunMailingServiceImpl.java` | `BR-ORDER-049` … `054` |
| `security/JwtTokenVerifierFilter.java` | `BR-USER-019` … `022` |
| `security/GenericJwtAuthenticationFilter.java` | `BR-USER-016`, `017` |
| `security/UserDetailsServiceImpl.java` | `BR-USER-005`, `023`, `026`, `027`, `030` |
| `security/services/impl/AuthorizedApiServiceImpl.java` | `BR-USER-031` … `033` |
| `api/services/impl/RegistrationServiceImpl.java` | `BR-USER-009` … `013`; `BR-PERSON-001`; `BR-CUSTOMER-003` |
| `api/services/impl/ProfileServiceImpl.java` | `BR-USER-004`, `050` … `052`; `BR-PERSON-007`, `008` |
| `jpa/services/crud/impl/UsersCrudServiceImpl.java` | `BR-USER-039`, `041` … `043` |
| `config/SecurityConfig.java` | `BR-USER-014`, `024`, `025` |
| `config/JwtKeyConfig.java` | `BR-USER-018` |
| `jpa/services/crud/CrudGenericService.java` | the already-exists, single-row, no-op-write and not-found rules in **every** domain |
| `api/DataCrudGenericController.java` | the missing-params, no-match and empty-delete rules in every CRUD domain |
| `api/ExceptionsControllerAdvice.java` | the four mapped error conditions referenced by every domain's validations file |
| `jpa/services/impl/ProductCategoryTreeResolverServiceImpl.java` | `BR-PRODCAT-010`, `011`; `BR-PRODUCT-019` |
| `jpa/services/crud/impl/ProductsCrudServiceImpl.java` | `BR-PRODUCT-002`, `003`, `011` … `016`; `BR-IMAGE-002`, `003` |
| `api/controllers/DataProductListContentsController.java` | `BR-PRODLIST-011` … `018`, `030` |
| `jpa/services/crud/impl/ProductListsCrudServiceImpl.java` | `BR-PRODLIST-003`, `004`, `021`, `022` |
| `common/Utils.java` | `BR-CUSTOMER-012`; `BR-PERSON-007` (prefix convention) |
| `config/Constants.java` | `BR-ORDER-020`, `043`, `062`; `BR-BILLING-002`; `BR-USER-021` |
| `config/ApiProperties.java` | `BR-ORDER-029`, `030`; `BR-PRODCAT-010` |
| `config/SecurityProperties.java` | `BR-USER-013`, `017`, `026`, `027`, `030`, `041` |
| `src/main/resources/data.sql` | `BR-USER-036`, `037`, `055`; `BR-ORDER-025`; `BR-BILLING-002`, `016`; `BR-COMPANY-003`, `012` |
| `src/main/resources/schema-mariadb.sql` | every uniqueness, nullability and referential rule across all domains |

## Feature → endpoint traceability

| Domain | Features | Endpoints |
|---|---|---|
| `person` | `F-001` … `007` | `GET /data/people` |
| `image` | `F-001` … `008` | `GET POST PUT PATCH DELETE /data/images` |
| `product-category` | `F-001` … `014` | `GET POST PUT PATCH DELETE /data/product_categories` |
| `billing` | `F-001` … `010` | `GET /data/billing_types` |
| `shipper` | `F-001` … `009` | `GET POST PUT PATCH DELETE /data/shippers` |
| `company-profile` | `F-001` … `005` | `GET /public/about` |
| `user` | `F-001` … `015` | `POST /public/login`, `POST /public/guest`, `POST /public/register`, `GET PUT /account/profile`, `GET /access`, `GET /access/{apiRoute}`, `GET POST PUT DELETE /data/users`, `GET POST PUT DELETE /data/user_roles` |
| `customer` | `F-001` … `011` | `GET POST PUT DELETE /data/customers` |
| `salesperson` | `F-001` … `008` | `GET POST PUT DELETE /data/salespeople` |
| `product` | `F-001` … `013` | `GET POST PUT PATCH DELETE /data/products` |
| `product-list` | `F-001` … `016` | `GET POST PUT PATCH DELETE /data/product_lists`, `GET POST PUT DELETE /data/product_list_contents` |
| `order` | `F-001` … `023` | `GET POST PUT PATCH DELETE /data/orders`, `POST /data/orders/{confirmation,rejection,completion}`, `GET /data/order_statuses`, `POST /public/checkout`, `GET POST /public/checkout/validate`, `GET /public/receipt/{token}` |

Note that `POST /public/login` and `POST /public/guest` are implemented by
authentication filters rather than controllers, so they do not appear in any
controller file. See `../reference-documentation/api-endpoint-catalog.md`.

## Cross-domain feature traceability

| Feature | Orchestrating file | Rules spanning domains |
|---|---|---|
| CDF-01 Checkout | `jpa/services/conversion/impl/OrdersConverterServiceImpl.java` | `BR-ORDER-004` … `012`, `BR-BILLING-003`, `BR-SHIPPER-015`, `BR-PRODUCT-009`, `BR-CUSTOMER-019` |
| CDF-02 Self-registration | `api/services/impl/RegistrationServiceImpl.java` | `BR-USER-009` … `012`, `BR-PERSON-001`, `BR-CUSTOMER-003` |
| CDF-03 Guest session | `security/JwtGuestAuthenticationFilter.java` | `BR-USER-026` … `030`, `BR-CUSTOMER-017`, `018` |
| CDF-04 Account profile | `api/services/impl/ProfileServiceImpl.java` | `BR-USER-004`, `051`, `BR-PERSON-007`, `008` |
| CDF-05 Branch-aware search | `jpa/services/predicates/impl/ProductsPredicateServiceImpl.java` | `BR-PRODUCT-019`, `BR-PRODCAT-010`, `011`, `018` |
| CDF-06 List contents | `api/controllers/DataProductListContentsController.java` | `BR-PRODLIST-011` … `018`, `BR-PRODUCT-030` |
| CDF-07 Image linking | `jpa/services/crud/impl/ProductsCrudServiceImpl.java` | `BR-PRODUCT-011` … `016`, `BR-IMAGE-002`, `003`, `014` |
| CDF-08 Status notification | `api/controllers/DataOrdersController.java` | `BR-ORDER-049` … `055` |
| CDF-09 Receipt lookup | `api/services/impl/ReceiptServiceImpl.java` | `BR-ORDER-044` … `048`, `BR-PRODUCT-010` |
| CDF-10 Authority enforcement | every controller + `security/services/impl/UserPermissionsServiceImpl.java` | `BR-USER-005`, `022`, `031`, `036` … `038` |

## Test coverage pointers

108 test classes mirror the main package structure exactly, so the tests for any
rule are found by prefixing the implementing class with its test name — for
example `OrdersProcessServiceImplTest` for the status machine and
`WebpayplusPaymentServiceImplTest` for the payment adapter. Tests are
predominantly plain unit tests with mocks; only one integration-style test exists
and there are no web-layer or persistence-layer slice tests. See
`../reference-documentation/service-component-inventory.md`.
