/*
 * Copyright (c) 2020-2024 The Trebol eCommerce Project
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished
 * to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package org.trebol.jpa.services.conversion.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.trebol.api.models.AddressPojo;
import org.trebol.api.models.BillingCompanyPojo;
import org.trebol.api.models.OrderDetailPojo;
import org.trebol.api.models.OrderPojo;
import org.trebol.api.models.PersonPojo;
import org.trebol.api.models.ProductPojo;
import org.trebol.common.exceptions.BadInputException;
import org.trebol.jpa.entities.Address;
import org.trebol.jpa.entities.BillingCompany;
import org.trebol.jpa.entities.BillingType;
import org.trebol.jpa.entities.Order;
import org.trebol.jpa.entities.OrderStatus;
import org.trebol.jpa.entities.PaymentType;
import org.trebol.jpa.entities.Shipper;
import org.trebol.jpa.repositories.AddressesRepository;
import org.trebol.jpa.repositories.OrderStatusesRepository;
import org.trebol.jpa.services.contracts.BillingContract;
import org.trebol.jpa.services.contracts.ProductLookupContract;
import org.trebol.jpa.services.contracts.ProductSnapshot;
import org.trebol.jpa.services.contracts.CustomerProvisioningContract;
import org.trebol.jpa.services.contracts.ShipperLookupContract;
import org.trebol.jpa.services.conversion.AddressesConverterService;
import org.trebol.jpa.services.conversion.BillingCompaniesConverterService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.trebol.config.Constants.BILLING_TYPE_ENTERPRISE;
import static org.trebol.jpa.services.conversion.impl.OrdersConverterServiceImpl.TAX_PERCENT;
import static org.trebol.jpa.services.conversion.impl.OrdersConverterServiceImpl.UNEXISTING_BILLING_TYPE;
import static org.trebol.testing.TestConstants.ANY;
import static org.trebol.testing.TestConstants.NOT_ANY;

@ExtendWith(MockitoExtension.class)
class OrdersConverterServiceImplTest {
    @InjectMocks
    OrdersConverterServiceImpl instance;
    @Mock CustomerProvisioningContract customerProvisioningContractMock;
    @Mock BillingContract billingContractMock;
    @Mock BillingCompaniesConverterService billingCompaniesConverterServiceMock;
    @Mock ProductLookupContract productLookupContractMock;
    @Mock ShipperLookupContract shipperLookupContractMock;
    @Mock AddressesRepository addressesRepositoryMock;
    @Mock
    OrderStatusesRepository orderStatusesRepository;
    @Mock AddressesConverterService addressesConverterServiceMock;
    private static final Instant SOME_INSTANT = Instant.now();
    private static final PersonPojo SOME_CUSTOMER = PersonPojo.builder().build();
    private static final BillingCompanyPojo SOME_BILLING_COMPANY = BillingCompanyPojo.builder().build();
    private static final AddressPojo SOME_ADDRESS;
    /** Phase 3: the order holds a customer id number, not a Customer entity. */
    private static final String SOME_CUSTOMER_ID_NUMBER = "1";
    private static final BillingType SOME_BILLING_TYPE_ENTITY = BillingType.builder().build();
    private static final BillingCompany SOME_BILLING_COMPANY_ENTITY = BillingCompany.builder().build();
    private static final Address SOME_ADDRESS_ENTITY = Address.builder().build();
    /** Phase 3: the order line holds a product barcode, not a Product entity. */
    private static final String SOME_PRODUCT_BARCODE = "a-product-barcode";
    private static final Shipper SOME_SHIPPER_ENTITY = Shipper.builder().build();
    private static final PaymentType SOME_PAYMENT_TYPE_ENTITY = PaymentType.builder().build();
    private static final OrderStatus SOME_ORDER_STATUS_ENTITY = OrderStatus.builder().build();

    static {
        SOME_ADDRESS = AddressPojo.builder()
            .firstLine(ANY)
            .secondLine(ANY)
            .city(ANY)
            .municipality(ANY)
            .postalCode(ANY)
            .notes(ANY)
            .build();
    }

    @Test
    void converts_to_new_entity_with_existing_data() throws BadInputException {
        OrderPojo input = OrderPojo.builder()
            .date(SOME_INSTANT)
            .customer(SOME_CUSTOMER)
            .billingType(BILLING_TYPE_ENTERPRISE)
            .billingCompany(SOME_BILLING_COMPANY)
            .billingAddress(SOME_ADDRESS)
            .paymentType(ANY)
            .details(List.of(
                OrderDetailPojo.builder()
                    .units(1)
                    .product(ProductPojo.builder()
                        .barcode(ANY)
                        .build())
                    .build()
            ))
            .shipper(ANY)
            .shippingAddress(SOME_ADDRESS)
            .build();
        when(billingContractMock.findPaymentTypeByName(anyString())).thenReturn(Optional.of(SOME_PAYMENT_TYPE_ENTITY));
        when(customerProvisioningContractMock.resolveOrCreateCustomerIdNumber(any(PersonPojo.class))).thenReturn(SOME_CUSTOMER_ID_NUMBER);
        when(billingContractMock.findBillingTypeByName(anyString())).thenReturn(Optional.of(SOME_BILLING_TYPE_ENTITY));
        when(billingContractMock.resolveOrCreateBillingCompany(any(BillingCompanyPojo.class))).thenReturn(SOME_BILLING_COMPANY_ENTITY);
        when(addressesRepositoryMock.findByFields(anyString(), anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(Optional.of(SOME_ADDRESS_ENTITY));
        // Phase 3 cut C1: the contract returns a three-field snapshot, not a Product entity.
        when(productLookupContractMock.findProductByBarcode(anyString()))
            .thenReturn(Optional.of(new ProductSnapshot(SOME_PRODUCT_BARCODE, ANY, 100)));
        when(shipperLookupContractMock.findShipperByName(anyString())).thenReturn(Optional.of(SOME_SHIPPER_ENTITY));
        Order result = instance.convertToNewEntity(input);
        assertEquals(SOME_CUSTOMER_ID_NUMBER, result.getCustomerIdNumber());
        verify(customerProvisioningContractMock).resolveOrCreateCustomerIdNumber(SOME_CUSTOMER);
        assertEquals(SOME_BILLING_TYPE_ENTITY, result.getBillingType());
        verify(billingContractMock).findBillingTypeByName(BILLING_TYPE_ENTERPRISE);
        assertEquals(SOME_BILLING_COMPANY_ENTITY, result.getBillingCompany());
        verify(billingContractMock).resolveOrCreateBillingCompany(SOME_BILLING_COMPANY);
        assertEquals(SOME_ADDRESS_ENTITY, result.getBillingAddress());
        assertFalse(result.getDetails().isEmpty());
        assertEquals(1, result.getDetails().size());
        assertEquals(SOME_PRODUCT_ENTITY, result.getDetails().iterator().next().getProduct());
        verify(productLookupContractMock, times(1)).findProductByBarcode(ANY);
        assertEquals(SOME_SHIPPER_ENTITY, result.getShipper());
        assertEquals(SOME_ADDRESS_ENTITY, result.getShippingAddress());
        verify(shipperLookupContractMock).findShipperByName(ANY);
        verify(addressesRepositoryMock, times(2)).findByFields(ANY, ANY, ANY, ANY, ANY, ANY); // billing & shipping addresses
    }

    @Test
    void converts_to_new_entity_and_calculates_values_and_totals() throws BadInputException {
        int productUnits = 2;
        int productValue = 10000;
        int transportValue = 0;
        int taxPerProduct = (int) (productValue * TAX_PERCENT);
        int netProductValue = (productValue - taxPerProduct);
        int totalTaxValue = (taxPerProduct * productUnits);
        int totalNetValue = (netProductValue * productUnits);
        int totalValue = (totalNetValue + totalTaxValue + transportValue);
        Product productEntityWithPrice = Product.builder()
            .price(productValue)
            .build();
        OrderPojo input = OrderPojo.builder()
            .date(SOME_INSTANT)
            .customer(SOME_CUSTOMER)
            .billingType(BILLING_TYPE_ENTERPRISE)
            .billingCompany(SOME_BILLING_COMPANY)
            .billingAddress(SOME_ADDRESS)
            .paymentType(ANY)
            .details(List.of(
                OrderDetailPojo.builder()
                    .units(productUnits)
                    .product(ProductPojo.builder()
                        .barcode(ANY)
                        .build())
                    .build()
            ))
            .shipper(ANY)
            .shippingAddress(SOME_ADDRESS)
            .build();
        when(billingContractMock.findPaymentTypeByName(anyString())).thenReturn(Optional.of(SOME_PAYMENT_TYPE_ENTITY));
        when(billingContractMock.findBillingTypeByName(anyString())).thenReturn(Optional.of(SOME_BILLING_TYPE_ENTITY));
        when(billingContractMock.resolveOrCreateBillingCompany(any(BillingCompanyPojo.class))).thenReturn(SOME_BILLING_COMPANY_ENTITY);
        when(addressesRepositoryMock.findByFields(anyString(), anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(Optional.of(SOME_ADDRESS_ENTITY));
        when(productLookupContractMock.findProductByBarcode(anyString())).thenReturn(Optional.of(productEntityWithPrice));
        when(shipperLookupContractMock.findShipperByName(anyString())).thenReturn(Optional.of(SOME_SHIPPER_ENTITY));
        Order result = instance.convertToNewEntity(input);
        assertEquals(productUnits, result.getTotalItems());
        assertEquals(totalTaxValue, result.getTaxesValue());
        assertEquals(totalNetValue, result.getNetValue());
        assertEquals(transportValue, result.getTransportValue());
        assertEquals(totalValue, result.getTotalValue());
    }

    @Test
    void converts_to_pojo() {
        Order.OrderBuilder inputBuilder = Order.builder()
            .id(null)
            .date(null)
            .netValue(0)
            .taxesValue(0)
            .totalValue(0)
            .totalItems(0)
            .transportValue(0)
            .transactionToken(null)
            .customer(null)
            .status(OrderStatus.builder()
                .name(ANY)
                .build())
            .paymentType(PaymentType.builder()
                .name(ANY)
                .build())
            .billingType(BillingType.builder()
                .name(ANY)
                .build())
            .billingCompany(null)
            .billingAddress(null)
            .shipper(null)
            .shippingAddress(null)
            .salesperson(null);
        when(customerProvisioningContractMock.describeCustomer(nullable(Customer.class))).thenReturn(SOME_CUSTOMER);
        List.of(
            inputBuilder
                .build(),
            inputBuilder
                .id(1000L)
                .build(),
            inputBuilder
                .date(SOME_INSTANT)
                .build(),
            inputBuilder
                .netValue(1000)
                .taxesValue(10000)
                .totalValue(100)
                .totalItems(100000)
                .transportValue(1)
                .transactionToken(ANY)
                .build(),
            inputBuilder
                .customerIdNumber(SOME_CUSTOMER_ID_NUMBER)
                .build(),
            inputBuilder
                .status(OrderStatus.builder()
                    .name(NOT_ANY)
                    .build())
                .build(),
            inputBuilder
                .paymentType(PaymentType.builder()
                    .name(NOT_ANY)
                    .build())
                .build(),
            inputBuilder
                .billingType(BillingType.builder()
                    .name(NOT_ANY)
                    .build())
                .build(),
            inputBuilder
                .billingCompany(SOME_BILLING_COMPANY_ENTITY)
                .build(),
            inputBuilder
                .billingAddress(SOME_ADDRESS_ENTITY)
                .shippingAddress(SOME_ADDRESS_ENTITY)
                .build()
        ).forEach(input -> {
            OrderPojo result = instance.convertToPojo(input);
            assertEquals(input.getId(), result.getBuyOrder());
            assertEquals(input.getDate(), result.getDate());
            assertEquals(input.getNetValue(), result.getNetValue());
            assertEquals(input.getTaxesValue(), result.getTaxValue());
            assertEquals(input.getTotalValue(), result.getTotalValue());
            assertEquals(input.getTotalItems(), result.getTotalItems());
            assertEquals(input.getTransportValue(), result.getTransportValue());
            assertEquals(input.getTransactionToken(), result.getToken());
            assertEquals(SOME_CUSTOMER, result.getCustomer());
            assertEquals(input.getStatus().getName(), result.getStatus());
            assertEquals(input.getPaymentType().getName(), result.getPaymentType());
            assertEquals(input.getBillingType().getName(), result.getBillingType());
            assertNull(result.getBillingCompany());
            assertNull(result.getShipper());
            assertNull(result.getBillingAddress());
            assertNull(result.getShippingAddress());
            assertNull(result.getDetails());
        });
    }

    @Test
    void converts_to_pojo_with_billing_company() {
        Order input = Order.builder()
            .status(OrderStatus.builder()
                .name(ANY)
                .build())
            .paymentType(PaymentType.builder()
                .name(ANY)
                .build())
            .billingType(BillingType.builder()
                .name(BILLING_TYPE_ENTERPRISE)
                .build())
            .billingCompany(SOME_BILLING_COMPANY_ENTITY)
            .build();
        when(billingCompaniesConverterServiceMock.convertToPojo(nullable(BillingCompany.class))).thenReturn(SOME_BILLING_COMPANY);
        OrderPojo result = instance.convertToPojo(input);
        assertEquals(BILLING_TYPE_ENTERPRISE, result.getBillingType());
        assertEquals(SOME_BILLING_COMPANY, result.getBillingCompany());
        verify(billingCompaniesConverterServiceMock).convertToPojo(SOME_BILLING_COMPANY_ENTITY);
    }

    @Test
    void converts_to_pojo_with_shipper() {
        Order input = Order.builder()
            .status(OrderStatus.builder()
                .name(ANY)
                .build())
            .paymentType(PaymentType.builder()
                .name(ANY)
                .build())
            .billingType(BillingType.builder()
                .name(ANY)
                .build())
            .shipper(Shipper.builder()
                .name(ANY)
                .build())
            .build();
        OrderPojo result = instance.convertToPojo(input);
        assertEquals(ANY, result.getShipper());
    }

    @Test
    void converts_to_pojo_with_salesperson() {
        Order input = Order.builder()
            .status(OrderStatus.builder()
                .name(ANY)
                .build())
            .paymentType(PaymentType.builder()
                .name(ANY)
                .build())
            .billingType(BillingType.builder()
                .name(ANY)
                .build())
            .build();
        OrderPojo result = instance.convertToPojo(input);
    }

    @Nested
    class NewEntityCascadingCases {
        @BeforeEach
        void beforeEach() {
            when(orderStatusesRepository.findByName(anyString())).thenReturn(Optional.of(SOME_ORDER_STATUS_ENTITY));
        }

        @Test
        void converts_to_new_entity_with_new_billing_company() throws BadInputException {
            OrderPojo input = OrderPojo.builder()
                .date(SOME_INSTANT)
                .paymentType(ANY)
                .customer(SOME_CUSTOMER)
                .billingType(BILLING_TYPE_ENTERPRISE)
                .billingCompany(SOME_BILLING_COMPANY)
                .billingAddress(SOME_ADDRESS)
                .details(List.of())
                .build();
            when(billingContractMock.findPaymentTypeByName(anyString())).thenReturn(Optional.of(SOME_PAYMENT_TYPE_ENTITY));
            when(billingContractMock.findBillingTypeByName(anyString())).thenReturn(Optional.of(SOME_BILLING_TYPE_ENTITY));
            when(billingContractMock.resolveOrCreateBillingCompany(any(BillingCompanyPojo.class))).thenReturn(SOME_BILLING_COMPANY_ENTITY);
            when(addressesRepositoryMock.findByFields(anyString(), anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(Optional.of(SOME_ADDRESS_ENTITY));
            Order result = instance.convertToNewEntity(input);
            assertEquals(SOME_BILLING_COMPANY_ENTITY, result.getBillingCompany());
            // resolve-or-create is now the billing contract's responsibility; both of its branches
            // are covered by BillingContractImplTest
            verify(billingContractMock).resolveOrCreateBillingCompany(SOME_BILLING_COMPANY);
        }

        @Test
        void converts_to_new_entity_with_new_shipping_address() throws BadInputException {
            OrderPojo input = OrderPojo.builder()
                .date(SOME_INSTANT)
                .paymentType(ANY)
                .customer(SOME_CUSTOMER)
                .billingType(ANY)
                .details(List.of())
                .shipper(ANY)
                .shippingAddress(SOME_ADDRESS)
                .build();
            when(billingContractMock.findPaymentTypeByName(anyString())).thenReturn(Optional.of(SOME_PAYMENT_TYPE_ENTITY));
            when(billingContractMock.findBillingTypeByName(anyString())).thenReturn(Optional.of(SOME_BILLING_TYPE_ENTITY));
            when(addressesConverterServiceMock.convertToNewEntity(any(AddressPojo.class))).thenReturn(SOME_ADDRESS_ENTITY);
            when(shipperLookupContractMock.findShipperByName(anyString())).thenReturn(Optional.of(SOME_SHIPPER_ENTITY));
            Order result = instance.convertToNewEntity(input);
            verify(addressesRepositoryMock).findByFields(ANY, ANY, ANY, ANY, ANY, ANY); // not mocked, is Optional.empty()
            verify(addressesConverterServiceMock).convertToNewEntity(SOME_ADDRESS);
            assertEquals(SOME_ADDRESS_ENTITY, result.getShippingAddress());
        }
    }

    @Nested
    class NewEntityValidationFailureCases {
        private static final String NO_CUSTOMER_PROVIDED = "No customer provided";
        private static final String NO_BILLING_COMPANY_PROVIDED = "No billing company provided";

        @BeforeEach
        void beforeEach() {
            when(orderStatusesRepository.findByName(anyString())).thenReturn(Optional.of(SOME_ORDER_STATUS_ENTITY));
        }

        @Test
        void no_payment_type() {
            List.of(
                OrderPojo.builder()
                    .date(null)
                    .build(),
                OrderPojo.builder()
                    .date(SOME_INSTANT)
                    .paymentType(null)
                    .build()
            ).forEach(input -> {
                BadInputException result = assertThrows(BadInputException.class, () -> instance.convertToNewEntity(input));
                assertEquals("A payment type has to be included", result.getMessage());
            });
            verifyNoInteractions(customerProvisioningContractMock);
        }

        @Test
        void no_customer_information() throws BadInputException {
            OrderPojo input = OrderPojo.builder()
                .date(SOME_INSTANT)
                .paymentType(ANY)
                .customer(null)
                .build();
            when(billingContractMock.findPaymentTypeByName(anyString())).thenReturn(Optional.of(SOME_PAYMENT_TYPE_ENTITY));
            when(customerProvisioningContractMock.resolveOrCreateCustomer(isNull())).thenThrow(new BadInputException(NO_CUSTOMER_PROVIDED));
            BadInputException result = assertThrows(BadInputException.class, () -> instance.convertToNewEntity(input));
            assertEquals(NO_CUSTOMER_PROVIDED, result.getMessage());
            verify(customerProvisioningContractMock).resolveOrCreateCustomer(isNull());
            verifyNoMoreInteractions(customerProvisioningContractMock);
        }

        @Test
        void no_billing_information() {
            OrderPojo input = OrderPojo.builder()
                .date(SOME_INSTANT)
                .paymentType(ANY)
                .customer(SOME_CUSTOMER)
                .billingType(null)
                .build();
            when(billingContractMock.findPaymentTypeByName(anyString())).thenReturn(Optional.of(SOME_PAYMENT_TYPE_ENTITY));
            BadInputException result = assertThrows(BadInputException.class, () -> instance.convertToNewEntity(input));
            assertEquals(UNEXISTING_BILLING_TYPE, result.getMessage());
            verify(billingContractMock).findBillingTypeByName(isNull());
        }

        @Test
        void invalid_billing_type() {
            OrderPojo input = OrderPojo.builder()
                .date(SOME_INSTANT)
                .paymentType(ANY)
                .customer(SOME_CUSTOMER)
                .billingType(NOT_ANY)
                .build();
            when(billingContractMock.findPaymentTypeByName(anyString())).thenReturn(Optional.of(SOME_PAYMENT_TYPE_ENTITY));
            when(billingContractMock.findBillingTypeByName(NOT_ANY)).thenReturn(Optional.empty());
            BadInputException result = assertThrows(BadInputException.class, () -> instance.convertToNewEntity(input));
            assertEquals(UNEXISTING_BILLING_TYPE, result.getMessage());
            verify(billingContractMock).findBillingTypeByName(NOT_ANY);
        }

        @Test
        void billing_type_for_enterprise_without_billing_company() throws BadInputException {
            OrderPojo input = OrderPojo.builder()
                .date(SOME_INSTANT)
                .paymentType(ANY)
                .customer(SOME_CUSTOMER)
                .billingType(BILLING_TYPE_ENTERPRISE)
                .billingAddress(SOME_ADDRESS)
                .billingCompany(null)
                .build();
            when(billingContractMock.findPaymentTypeByName(anyString())).thenReturn(Optional.of(SOME_PAYMENT_TYPE_ENTITY));
            when(billingContractMock.findBillingTypeByName(anyString())).thenReturn(Optional.of(SOME_BILLING_TYPE_ENTITY));
            when(billingContractMock.resolveOrCreateBillingCompany(isNull())).thenThrow(new BadInputException(NO_BILLING_COMPANY_PROVIDED));
            BadInputException result = assertThrows(BadInputException.class, () -> instance.convertToNewEntity(input));
            assertEquals(NO_BILLING_COMPANY_PROVIDED, result.getMessage());
        }

        @Test
        void no_details() {
            OrderPojo inputWithoutDetails = OrderPojo.builder()
                .date(SOME_INSTANT)
                .paymentType(ANY)
                .customer(SOME_CUSTOMER)
                .billingType(ANY)
                .details(null)
                .build();
            when(billingContractMock.findPaymentTypeByName(anyString())).thenReturn(Optional.of(SOME_PAYMENT_TYPE_ENTITY));
            when(billingContractMock.findBillingTypeByName(anyString())).thenReturn(Optional.of(SOME_BILLING_TYPE_ENTITY));
            assertThrows(NullPointerException.class, () -> instance.convertToNewEntity(inputWithoutDetails));
            verifyNoInteractions(productLookupContractMock);
        }

        @Test
        void invalid_details() {
            OrderPojo.OrderPojoBuilder inputBuilder = OrderPojo.builder()
                .date(SOME_INSTANT)
                .paymentType(ANY)
                .customer(SOME_CUSTOMER)
                .billingType(ANY);
            when(billingContractMock.findPaymentTypeByName(anyString())).thenReturn(Optional.of(SOME_PAYMENT_TYPE_ENTITY));
            when(billingContractMock.findBillingTypeByName(anyString())).thenReturn(Optional.of(SOME_BILLING_TYPE_ENTITY));

            // products cannot be null
            List.of(
                List.of(
                    OrderDetailPojo.builder().build()
                ),
                List.of(
                    OrderDetailPojo.builder()
                        .product(null)
                        .build()
                )
            ).forEach(detailsList -> {
                inputBuilder.details(detailsList);
                assertThrows(NullPointerException.class, () -> instance.convertToNewEntity(inputBuilder.build()));
            });

            // nor can product barcodes be absent, null nor empty
            List.of(
                List.of(
                    OrderDetailPojo.builder()
                        .product(ProductPojo.builder().build())
                        .build()
                ),
                List.of(
                    OrderDetailPojo.builder()
                        .product(ProductPojo.builder()
                            .barcode(null)
                            .build())
                        .build()
                ),
                List.of(
                    OrderDetailPojo.builder()
                        .product(ProductPojo.builder()
                            .barcode("")
                            .build())
                        .build()
                )
            ).forEach(detailsList -> {
                inputBuilder.details(detailsList);
                RuntimeException result = assertThrows(RuntimeException.class, () -> instance.convertToNewEntity(inputBuilder.build()));
                assertEquals("Product barcode must be valid", result.getMessage());
            });
            verifyNoInteractions(productLookupContractMock);
        }

        @Test
        void invalid_products() {
            OrderPojo input = OrderPojo.builder()
                .date(SOME_INSTANT)
                .paymentType(ANY)
                .customer(SOME_CUSTOMER)
                .billingType(ANY)
                .details(List.of(
                    OrderDetailPojo.builder()
                        .product(ProductPojo.builder()
                            .barcode(ANY)
                            .build())
                        .build()
                ))
                .build();
            when(billingContractMock.findPaymentTypeByName(anyString())).thenReturn(Optional.of(SOME_PAYMENT_TYPE_ENTITY));
            when(billingContractMock.findBillingTypeByName(anyString())).thenReturn(Optional.of(SOME_BILLING_TYPE_ENTITY));
            when(productLookupContractMock.findProductByBarcode(anyString())).thenReturn(Optional.empty());
            RuntimeException result = assertThrows(RuntimeException.class, () -> instance.convertToNewEntity(input));
            BadInputException originalResult = (BadInputException) result.getCause();
            assertEquals("Unexisting product in sell details", originalResult.getMessage());
            verify(productLookupContractMock).findProductByBarcode(ANY);
        }

        @Test
        void invalid_shipper() {
            OrderPojo input = OrderPojo.builder()
                .date(SOME_INSTANT)
                .paymentType(ANY)
                .customer(SOME_CUSTOMER)
                .billingType(ANY)
                .details(List.of())
                .shipper(ANY)
                .build();
            when(billingContractMock.findPaymentTypeByName(anyString())).thenReturn(Optional.of(SOME_PAYMENT_TYPE_ENTITY));
            when(billingContractMock.findBillingTypeByName(anyString())).thenReturn(Optional.of(SOME_BILLING_TYPE_ENTITY));
            BadInputException result = assertThrows(BadInputException.class, () -> instance.convertToNewEntity(input));
            assertEquals("Specified shipper does not exist", result.getMessage());
            verify(shipperLookupContractMock).findShipperByName(ANY);
        }

        @Test
        void shipper_without_shipping_address() {
            OrderPojo input = OrderPojo.builder()
                .date(SOME_INSTANT)
                .paymentType(ANY)
                .customer(SOME_CUSTOMER)
                .billingType(ANY)
                .details(List.of())
                .shipper(ANY)
                .shippingAddress(null)
                .build();
            when(billingContractMock.findPaymentTypeByName(anyString())).thenReturn(Optional.of(SOME_PAYMENT_TYPE_ENTITY));
            when(billingContractMock.findBillingTypeByName(anyString())).thenReturn(Optional.of(SOME_BILLING_TYPE_ENTITY));
            when(shipperLookupContractMock.findShipperByName(anyString())).thenReturn(Optional.of(SOME_SHIPPER_ENTITY));
            assertThrows(NullPointerException.class, () -> instance.convertToNewEntity(input));
            verify(shipperLookupContractMock).findShipperByName(ANY);
        }
    }
}
