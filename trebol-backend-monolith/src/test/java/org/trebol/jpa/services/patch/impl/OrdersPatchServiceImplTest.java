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

package org.trebol.jpa.services.patch.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.trebol.api.models.OrderPojo;
import org.trebol.common.exceptions.BadInputException;
import org.trebol.jpa.entities.*;
import org.trebol.jpa.entities.OrderStatus;
import org.trebol.jpa.repositories.OrderStatusesRepository;
import org.trebol.jpa.services.contracts.BillingContract;
import org.trebol.jpa.services.contracts.ShipperLookupContract;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.trebol.config.Constants.ORDER_STATUS_PAYMENT_STARTED;
import static org.trebol.config.Constants.ORDER_STATUS_PENDING;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.trebol.testing.TestConstants.ANY;
import static org.trebol.testing.TestConstants.NOT_ANY;

@ExtendWith(MockitoExtension.class)
class OrdersPatchServiceImplTest {
    @InjectMocks
    OrdersPatchServiceImpl instance;
    @Mock
    OrderStatusesRepository statusesRepositoryMock;
    @Mock BillingContract billingContractMock;
    @Mock ShipperLookupContract shipperLookupContractMock;
    private static ObjectMapper MAPPER;
    private static Order existingOrder;

    @BeforeAll
    static void beforeAll() {
        MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .setSerializationInclusion(NON_NULL)
            .configure(WRITE_DATES_AS_TIMESTAMPS, false);
        existingOrder = Order.builder()
            .id(1L)
            .date(Instant.now())
            .totalItems(1)
            .netValue(9700)
            .transportValue(0)
            .taxesValue(300)
            .totalValue(10000)
            .transactionToken(ANY)
            .paymentType(PaymentType.builder()
                .id(1L)
                .name(ANY)
                .build())
            .status(OrderStatus.builder()
                .id(1L)
                .name(ANY)
                .build())
            .billingType(BillingType.builder()
                .id(1L)
                .name(ANY)
                .build())
            .billingAddress(null)
            .customer(null)
            .details(null)
            .billingCompany(null)
            .shipper(null)
            .shippingAddress(null)
            .salesperson(null)
            .build();
    }

    @Test
    void performs_empty_patch() throws BadInputException {
        Map<String, Object> input = this.mapFrom(OrderPojo.builder().build());
        Order result = instance.patchExistingEntity(input, existingOrder);
        assertEquals(existingOrder, result);
    }

    @Test
    void patches_date() throws BadInputException {
        Instant whenTheMethodExecutes = Instant.now(Clock.systemUTC());
        Map<String, Object> input = this.mapFrom(OrderPojo.builder()
            .date(whenTheMethodExecutes)
            .build());
        Order result = instance.patchExistingEntity(input, existingOrder);
        assertNotEquals(existingOrder, result);
        assertEquals(whenTheMethodExecutes, result.getDate());
    }

    /**
     * <b>ASSERTION INVERTED by register items D5 and D10.</b>
     * <p>
     * This test was named {@code patches_status} and asserted that the patched status was set on the
     * returned entity - which was true of the in-memory copy, and was why the register recorded D5
     * as a state-machine bypass. The empirical probe written before the fix
     * ({@code OrderStatusPatchPersistenceProbeTest}) established that {@code Order.status} is mapped
     * {@code updatable = false}, so that copy's status was <b>silently discarded</b> on save and the
     * request reported success regardless.
     * <p>
     * Silently accepting an instruction that cannot be carried out is the defect. A status change
     * through this route is now rejected, naming the routes that do work.
     */
    @Test
    void rejects_a_status_change_because_this_route_cannot_apply_one() {
        Map<String, Object> input = this.mapFrom(OrderPojo.builder()
            .status(NOT_ANY)
            .build());

        BadInputException result = assertThrows(BadInputException.class,
            () -> instance.patchExistingEntity(input, existingOrder));

        assertEquals(OrdersPatchServiceImpl.ILLEGAL_STATUS_TRANSITION, result.getMessage());
    }

    /**
     * The same rejection, reached through the legal-transition branch rather than the illegal one.
     * {@code existingOrder}'s status is {@code Pending}, whose only legal successor is
     * {@code Payment Started}; that target is legal, and is still refused here because this route
     * cannot apply any status at all.
     */
    @Test
    void rejects_even_a_legal_status_transition_through_this_route() {
        // The shared fixture's status is ANY, which the transition table does not know, so it would
        // reach the ILLEGAL branch. Put the order in Pending - whose only legal successor is
        // Payment Started - so the LEGAL branch is exercised and still refuses.
        Order pendingOrder = new Order(existingOrder);
        pendingOrder.setStatus(OrderStatus.builder().id(7L).name(ORDER_STATUS_PENDING).build());
        Map<String, Object> input = new HashMap<>();
        input.put("status", ORDER_STATUS_PAYMENT_STARTED);

        BadInputException result = assertThrows(BadInputException.class,
            () -> instance.patchExistingEntity(input, pendingOrder));

        assertEquals(OrdersPatchServiceImpl.STATUS_IS_NOT_PATCHABLE, result.getMessage());
    }

    @Test
    void patches_paymentType() throws BadInputException {
        PaymentType existingPaymentType = PaymentType.builder().id(2L).build();
        Map<String, Object> input = this.mapFrom(OrderPojo.builder()
            .paymentType(NOT_ANY)
            .build());
        when(billingContractMock.findPaymentTypeByName(anyString())).thenReturn(Optional.of(existingPaymentType));
        Order result = instance.patchExistingEntity(input, existingOrder);
        assertNotEquals(existingOrder, result);
        assertEquals(existingPaymentType, result.getPaymentType());
    }

    @Test
    void patches_shipper() throws BadInputException {
        Shipper existingShipper = Shipper.builder().id(2L).build();
        Map<String, Object> input = this.mapFrom(OrderPojo.builder()
            .shipper(NOT_ANY)
            .build());
        when(shipperLookupContractMock.findShipperByName(anyString())).thenReturn(Optional.of(existingShipper));
        Order result = instance.patchExistingEntity(input, existingOrder);
        assertNotEquals(existingOrder, result);
        assertEquals(existingShipper, result.getShipper());
    }

    /**
     * <b>Narrowed by register item D5.</b> This case used to patch all four keys at once, including
     * {@code status}. A status change is now rejected outright, so including it here would assert a
     * rejection rather than a patch - that is covered by the two tests above. The other three keys
     * are unchanged.
     */
    @Test
    void patches_every_other_field_at_once() throws BadInputException {
        Instant whenTheMethodExecutes = Instant.now();
        PaymentType existingPaymentType = PaymentType.builder().build();
        Shipper existingShipper = Shipper.builder().build();
        Map<String, Object> input = this.mapFrom(OrderPojo.builder()
            .date(whenTheMethodExecutes)
            .paymentType(NOT_ANY)
            .shipper(NOT_ANY)
            .build());
        when(billingContractMock.findPaymentTypeByName(anyString())).thenReturn(Optional.of(existingPaymentType));
        when(shipperLookupContractMock.findShipperByName(anyString())).thenReturn(Optional.of(existingShipper));
        Order result = instance.patchExistingEntity(input, existingOrder);
        assertNotEquals(existingOrder, result);
        assertEquals(whenTheMethodExecutes, result.getDate());
        assertEquals(existingPaymentType, result.getPaymentType());
        assertEquals(existingShipper, result.getShipper());
    }

    @Test
    void does_not_support_old_method_signature() {
        OrderPojo input = OrderPojo.builder().build();
        assertThrows(UnsupportedOperationException.class,
            () -> instance.patchExistingEntity(input, existingOrder));
    }

    private Map<String, Object> mapFrom(OrderPojo data) {
        return MAPPER.convertValue(data, new TypeReference<HashMap<String, Object>>() {
        });
    }
}
