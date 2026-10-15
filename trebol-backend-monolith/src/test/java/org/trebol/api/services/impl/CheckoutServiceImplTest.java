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

package org.trebol.api.services.impl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.trebol.api.models.PaymentRedirectionDetailsPojo;
import org.trebol.api.models.OrderPojo;
import org.trebol.api.services.OrdersProcessService;
import org.trebol.common.exceptions.BadInputException;
import org.trebol.jpa.services.crud.OrdersCrudService;
import org.trebol.jpa.services.predicates.OrdersPredicateService;
import org.trebol.payment.PaymentService;
import org.trebol.payment.PaymentServiceException;
import org.trebol.testing.OrdersTestHelper;

import jakarta.persistence.EntityNotFoundException;
import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.trebol.config.Constants.ORDER_STATUS_PAID_UNCONFIRMED;
import static org.trebol.testing.OrdersTestHelper.ORDER_TRANSACTION_TOKEN;
import static org.trebol.testing.TestConstants.ANY;
import static org.trebol.testing.TestConstants.ONE;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceImplTest {
    @InjectMocks CheckoutServiceImpl service;
    @Mock
    OrdersCrudService ordersCrudServiceMock;
    @Mock
    OrdersProcessService ordersProcessServiceMock;
    @Mock
    OrdersPredicateService ordersPredicateServiceMock;
    @Mock PaymentService paymentServiceMock;
    final OrdersTestHelper salesHelper = new OrdersTestHelper();
    static final String PAYMENT_URL = "https://example.com/pay";
    static final Predicate MATCHER_PREDICATE = new BooleanBuilder();
    /** Register item D4: the per-order callback nonce a valid callback must present. */
    static final String SOME_CALLBACK_NONCE = "0123456789abcdef0123456789abcdef";

    @BeforeEach
    void beforeEach() {
        salesHelper.resetOrders();
        // Register item D4: every callback is now nonce-checked, so the happy path needs the
        // recorded nonce to match. lenient() because several tests fail before reaching the check.
        lenient().when(ordersProcessServiceMock.findCallbackNonce(anyString()))
            .thenReturn(SOME_CALLBACK_NONCE);
    }

    @Test
    void requests_transaction_start()
        throws BadInputException, PaymentServiceException, EntityNotFoundException {
        PaymentRedirectionDetailsPojo payload = PaymentRedirectionDetailsPojo.builder()
            .url(PAYMENT_URL)
            .token(ORDER_TRANSACTION_TOKEN)
            .build();
        when(paymentServiceMock.requestNewPaymentPageDetails(eq(salesHelper.orderPojoAfterCreation()), anyString())).thenReturn(payload);

        PaymentRedirectionDetailsPojo result = service.requestTransactionStart(salesHelper.orderPojoAfterCreation());

        verify(paymentServiceMock).requestNewPaymentPageDetails(eq(salesHelper.orderPojoAfterCreation()), anyString());
        verify(ordersProcessServiceMock).markAsStarted(eq(salesHelper.orderPojoAfterCreation()), anyString());
        assertEquals(PAYMENT_URL, result.getUrl());
        assertEquals(ORDER_TRANSACTION_TOKEN, result.getToken());
    }

    @Test
    void acknowledges_successful_transaction()
        throws PaymentServiceException, EntityNotFoundException, BadInputException {
        // Register item D4(c): the lookup is by token alone now. It always WAS, in effect - the
        // "statusCode" key these tests used to pass is not recognised by OrdersPredicateServiceImpl,
        // so the status filter was silently dropped. The map now says what the code actually does.
        Map<String, String> matcherMap = Map.of(
            "token", ORDER_TRANSACTION_TOKEN);
        when(ordersPredicateServiceMock.parseMap(matcherMap)).thenReturn(MATCHER_PREDICATE);
        when(ordersCrudServiceMock.readOne(MATCHER_PREDICATE)).thenReturn(salesHelper.orderPojoAfterCreation());
        when(paymentServiceMock.requestPaymentResult(ORDER_TRANSACTION_TOKEN)).thenReturn(0);
        when(ordersProcessServiceMock.markAsPaid(salesHelper.orderPojoAfterCreation())).thenReturn(null);

        OrderPojo result = service.confirmTransaction(ORDER_TRANSACTION_TOKEN, false, SOME_CALLBACK_NONCE);

        verify(ordersPredicateServiceMock).parseMap(matcherMap);
        verify(ordersCrudServiceMock).readOne(MATCHER_PREDICATE);
        verify(paymentServiceMock).requestPaymentResult(ORDER_TRANSACTION_TOKEN);
        verify(ordersProcessServiceMock).markAsPaid(salesHelper.orderPojoAfterCreation());
        assertNull(result);
    }

    @DisplayName("Confirm transaction when status code is not equal to zero call mark as failed")
    @Test
    void acknowledges_successful_transaction_payment_integration_returns_1()
        throws PaymentServiceException, EntityNotFoundException, BadInputException {
        // Register item D4(c): the lookup is by token alone now. It always WAS, in effect - the
        // "statusCode" key these tests used to pass is not recognised by OrdersPredicateServiceImpl,
        // so the status filter was silently dropped. The map now says what the code actually does.
        Map<String, String> matcherMap = Map.of(
            "token", ORDER_TRANSACTION_TOKEN);
        when(ordersPredicateServiceMock.parseMap(matcherMap)).thenReturn(MATCHER_PREDICATE);
        when(ordersCrudServiceMock.readOne(MATCHER_PREDICATE)).thenReturn(salesHelper.orderPojoAfterCreation());
        when(paymentServiceMock.requestPaymentResult(ORDER_TRANSACTION_TOKEN)).thenReturn(1);
        when(ordersProcessServiceMock.markAsFailed(salesHelper.orderPojoAfterCreation())).thenReturn(null);

        OrderPojo result = service.confirmTransaction(ORDER_TRANSACTION_TOKEN, false, SOME_CALLBACK_NONCE);

        verify(ordersPredicateServiceMock).parseMap(matcherMap);
        verify(ordersCrudServiceMock).readOne(MATCHER_PREDICATE);
        verify(paymentServiceMock).requestPaymentResult(ORDER_TRANSACTION_TOKEN);
        verify(ordersProcessServiceMock).markAsFailed(salesHelper.orderPojoAfterCreation());
        assertNull(result);
    }

    /**
     * Register item <b>D3</b>: an infrastructure failure must not be recorded as a customer decline.
     * <p>
     * Before the fix, a {@code TransactionCommitException} inside the adapter was swallowed and
     * reported as the magic value {@code 1}. Because this service treats <em>any</em> non-zero code
     * as a decline, a Transbank outage moved the customer's order to {@code Payment Failed} - a
     * state the customer never caused and which cannot be distinguished afterwards from a real
     * decline.
     * <p>
     * The adapter now raises {@link PaymentServiceException}, so this service never sees a
     * fabricated code. The order is <b>not</b> transitioned: it keeps {@code Payment Started}, which
     * is the truthful state and leaves it eligible for a later legitimate callback.
     * <p>
     * Note what is asserted: not merely that the exception propagates, but that
     * <b>no transition of any kind</b> was attempted.
     */
    @DisplayName("D3: an infrastructure failure propagates and does NOT mark the order as failed")
    @Test
    void infrastructure_failure_does_not_mark_the_order_as_failed()
        throws PaymentServiceException, EntityNotFoundException, BadInputException {
        // Register item D4(c): the lookup is by token alone now. It always WAS, in effect - the
        // "statusCode" key these tests used to pass is not recognised by OrdersPredicateServiceImpl,
        // so the status filter was silently dropped. The map now says what the code actually does.
        Map<String, String> matcherMap = Map.of(
            "token", ORDER_TRANSACTION_TOKEN);
        when(ordersPredicateServiceMock.parseMap(matcherMap)).thenReturn(MATCHER_PREDICATE);
        when(ordersCrudServiceMock.readOne(MATCHER_PREDICATE)).thenReturn(salesHelper.orderPojoAfterCreation());
        when(paymentServiceMock.requestPaymentResult(ORDER_TRANSACTION_TOKEN))
            .thenThrow(new PaymentServiceException("Webpay failed to confirm the transaction"));

        PaymentServiceException result = assertThrows(PaymentServiceException.class,
            () -> service.confirmTransaction(ORDER_TRANSACTION_TOKEN, false, SOME_CALLBACK_NONCE));

        assertEquals("Webpay failed to confirm the transaction", result.getMessage());
        verify(paymentServiceMock, times(ONE)).requestPaymentResult(ORDER_TRANSACTION_TOKEN);
        // Not verifyNoInteractions: register item D4 added a findCallbackNonce lookup that runs
        // before any transition. What matters for D3 is that NO TRANSITION was attempted.
        verify(ordersProcessServiceMock, never()).markAsPaid(any());
        verify(ordersProcessServiceMock, never()).markAsFailed(any());
        verify(ordersProcessServiceMock, never()).markAsAborted(any());
    }

    @DisplayName("Confirm transaction when status code is not equal to zero call mark as failed throws BadInputException")
    @Test
    void acknowledges_successful_transaction_payment_integration_returns_1_mark_as_failed_throws_bad_input_exception()
        throws PaymentServiceException, EntityNotFoundException, BadInputException {
        // Register item D4(c): the lookup is by token alone now. It always WAS, in effect - the
        // "statusCode" key these tests used to pass is not recognised by OrdersPredicateServiceImpl,
        // so the status filter was silently dropped. The map now says what the code actually does.
        Map<String, String> matcherMap = Map.of(
            "token", ORDER_TRANSACTION_TOKEN);
        when(ordersPredicateServiceMock.parseMap(matcherMap)).thenReturn(MATCHER_PREDICATE);
        when(ordersCrudServiceMock.readOne(MATCHER_PREDICATE)).thenReturn(salesHelper.orderPojoAfterCreation());
        when(paymentServiceMock.requestPaymentResult(ORDER_TRANSACTION_TOKEN)).thenReturn(1);
        when(ordersProcessServiceMock.markAsFailed(salesHelper.orderPojoAfterCreation())).thenThrow(BadInputException.class);

        assertThrows(IllegalStateException.class, () -> service.confirmTransaction(ORDER_TRANSACTION_TOKEN, false, SOME_CALLBACK_NONCE));

        verify(ordersPredicateServiceMock, times(ONE)).parseMap(matcherMap);
        verify(ordersCrudServiceMock, times(ONE)).readOne(MATCHER_PREDICATE);
        verify(paymentServiceMock, times(ONE)).requestPaymentResult(ORDER_TRANSACTION_TOKEN);
        verify(ordersProcessServiceMock, times(ONE)).markAsFailed(salesHelper.orderPojoAfterCreation());
    }

    @Test
    void acknowledges_aborted_transaction()
        throws PaymentServiceException, EntityNotFoundException, BadInputException {
        // Register item D4(c): the lookup is by token alone now. It always WAS, in effect - the
        // "statusCode" key these tests used to pass is not recognised by OrdersPredicateServiceImpl,
        // so the status filter was silently dropped. The map now says what the code actually does.
        Map<String, String> matcherMap = Map.of(
            "token", ORDER_TRANSACTION_TOKEN);
        when(ordersPredicateServiceMock.parseMap(matcherMap)).thenReturn(MATCHER_PREDICATE);
        when(ordersCrudServiceMock.readOne(MATCHER_PREDICATE)).thenReturn(salesHelper.orderPojoAfterCreation());
        when(ordersProcessServiceMock.markAsAborted(salesHelper.orderPojoAfterCreation())).thenReturn(null);

        OrderPojo result = service.confirmTransaction(ORDER_TRANSACTION_TOKEN, true, SOME_CALLBACK_NONCE);

        verify(ordersPredicateServiceMock, times(ONE)).parseMap(matcherMap);
        verify(ordersCrudServiceMock, times(ONE)).readOne(MATCHER_PREDICATE);
        verify(ordersProcessServiceMock, times(ONE)).markAsAborted(salesHelper.orderPojoAfterCreation());
        assertNull(result);
    }

    @DisplayName("Confirm Transaction when mark as aborted catch BadInputException throw IllegalArgumentException")
    @Test
    void acknowledges_aborted_transaction_illegal_bad_input_exception() throws EntityNotFoundException, BadInputException {
        // Register item D4(c): the lookup is by token alone now. It always WAS, in effect - the
        // "statusCode" key these tests used to pass is not recognised by OrdersPredicateServiceImpl,
        // so the status filter was silently dropped. The map now says what the code actually does.
        Map<String, String> matcherMap = Map.of(
            "token", ORDER_TRANSACTION_TOKEN);
        when(ordersPredicateServiceMock.parseMap(matcherMap)).thenReturn(MATCHER_PREDICATE);
        when(ordersCrudServiceMock.readOne(MATCHER_PREDICATE)).thenReturn(salesHelper.orderPojoAfterCreation());
        when(ordersProcessServiceMock.markAsAborted(salesHelper.orderPojoAfterCreation())).thenThrow(BadInputException.class);

        assertThrows(IllegalStateException.class,
            () -> service.confirmTransaction(ORDER_TRANSACTION_TOKEN, true, SOME_CALLBACK_NONCE), "Transaction could not be confirmed");

        verify(ordersPredicateServiceMock, times(ONE)).parseMap(matcherMap);
        verify(ordersCrudServiceMock, times(ONE)).readOne(MATCHER_PREDICATE);
        verify(ordersProcessServiceMock, times(ONE)).markAsAborted(salesHelper.orderPojoAfterCreation());
    }

    @Test
    void throws_exceptions_at_unexisting_transactions_before_requesting_payments()
        throws PaymentServiceException, EntityNotFoundException, BadInputException {
        PaymentRedirectionDetailsPojo payload = PaymentRedirectionDetailsPojo.builder()
            .url(PAYMENT_URL)
            .token(ORDER_TRANSACTION_TOKEN)
            .build();
        String exceptionMessage = "No match";
        when(paymentServiceMock.requestNewPaymentPageDetails(eq(salesHelper.orderPojoAfterCreation()), anyString())).thenReturn(payload);
        doThrow(new EntityNotFoundException(exceptionMessage)).
            when(ordersProcessServiceMock).markAsStarted(eq(salesHelper.orderPojoAfterCreation()), anyString());

        PaymentRedirectionDetailsPojo result = null;
        try {
            result = service.requestTransactionStart(salesHelper.orderPojoAfterCreation());
        } catch (Exception ex) {
            verify(paymentServiceMock).requestNewPaymentPageDetails(eq(salesHelper.orderPojoAfterCreation()), anyString());
        }

        assertNull(result);
        verify(ordersProcessServiceMock).markAsStarted(eq(salesHelper.orderPojoAfterCreation()), anyString());
    }

    @Test
    void throws_exceptions_at_invalid_transactions_before_confirming()
        throws PaymentServiceException, EntityNotFoundException, BadInputException {
        // Register item D4(c): the lookup is by token alone now. It always WAS, in effect - the
        // "statusCode" key these tests used to pass is not recognised by OrdersPredicateServiceImpl,
        // so the status filter was silently dropped. The map now says what the code actually does.
        Map<String, String> matcherMap = Map.of(
            "token", ORDER_TRANSACTION_TOKEN);
        String exceptionMessage = "No match";
        when(ordersPredicateServiceMock.parseMap(matcherMap)).thenReturn(MATCHER_PREDICATE);
        when(ordersCrudServiceMock.readOne(MATCHER_PREDICATE)).thenThrow(new EntityNotFoundException(exceptionMessage));

        OrderPojo result = null;
        try {
            result = service.confirmTransaction(ORDER_TRANSACTION_TOKEN, true, SOME_CALLBACK_NONCE);
        } catch (EntityNotFoundException e) {
            assertEquals(exceptionMessage, e.getMessage());
        }

        assertNull(result);
    }

    /**
     * Register item <b>D4(a)</b>, and the evidence for exit criterion <b>X9</b>: the callback is
     * authenticated.
     * <p>
     * Transbank sends no signature, so there is nothing to verify cryptographically. The control is
     * that the callback must present the per-order nonce that was embedded in the {@code returnUrl}
     * handed to the provider - a value a third party never saw. Both these mappings are, and remain,
     * fully anonymous; the nonce <em>is</em> the access control.
     */
    @DisplayName("D4: a callback presenting no nonce is rejected before any transition")
    @Test
    void rejects_a_callback_with_no_nonce() throws Exception {
        Map<String, String> matcherMap = Map.of("token", ORDER_TRANSACTION_TOKEN);
        when(ordersPredicateServiceMock.parseMap(matcherMap)).thenReturn(MATCHER_PREDICATE);
        when(ordersCrudServiceMock.readOne(MATCHER_PREDICATE)).thenReturn(salesHelper.orderPojoAfterCreation());

        BadInputException result = assertThrows(BadInputException.class,
            () -> service.confirmTransaction(ORDER_TRANSACTION_TOKEN, false, null));

        assertEquals("The transaction callback could not be authenticated", result.getMessage());
        verify(ordersProcessServiceMock, times(ONE)).findCallbackNonce(ORDER_TRANSACTION_TOKEN);
        verify(ordersProcessServiceMock, never()).markAsPaid(any());
        verify(ordersProcessServiceMock, never()).markAsFailed(any());
        verify(ordersProcessServiceMock, never()).markAsAborted(any());
        verifyNoInteractions(paymentServiceMock);
    }

    /** Register item D4(a): a guessed or stale nonce is no better than none. */
    @DisplayName("D4: a callback presenting the wrong nonce is rejected before any transition")
    @Test
    void rejects_a_callback_with_the_wrong_nonce() throws Exception {
        Map<String, String> matcherMap = Map.of("token", ORDER_TRANSACTION_TOKEN);
        when(ordersPredicateServiceMock.parseMap(matcherMap)).thenReturn(MATCHER_PREDICATE);
        when(ordersCrudServiceMock.readOne(MATCHER_PREDICATE)).thenReturn(salesHelper.orderPojoAfterCreation());

        BadInputException result = assertThrows(BadInputException.class,
            () -> service.confirmTransaction(ORDER_TRANSACTION_TOKEN, false, "not-the-right-nonce"));

        assertEquals("The transaction callback could not be authenticated", result.getMessage());
        verify(ordersProcessServiceMock, never()).markAsPaid(any());
        verifyNoInteractions(paymentServiceMock);
    }

    /**
     * Register item <b>D4(a)</b>: an order with no nonce recorded cannot be confirmed by callback.
     * <p>
     * This covers orders that predate the column and orders that never began a checkout. Rejecting
     * them is the correct outcome - the alternative would be a bypass in which a null expected value
     * matches a null presented value.
     */
    @DisplayName("D4: an order with no recorded nonce cannot be confirmed by callback")
    @Test
    void rejects_a_callback_for_an_order_with_no_recorded_nonce() throws Exception {
        Map<String, String> matcherMap = Map.of("token", ORDER_TRANSACTION_TOKEN);
        when(ordersPredicateServiceMock.parseMap(matcherMap)).thenReturn(MATCHER_PREDICATE);
        when(ordersCrudServiceMock.readOne(MATCHER_PREDICATE)).thenReturn(salesHelper.orderPojoAfterCreation());
        when(ordersProcessServiceMock.findCallbackNonce(anyString())).thenReturn(null);

        assertThrows(BadInputException.class,
            () -> service.confirmTransaction(ORDER_TRANSACTION_TOKEN, false, null));

        verify(ordersProcessServiceMock, never()).markAsPaid(any());
    }

    /**
     * Register item <b>D4(b)</b>, the second half of the X9 evidence: the callback is idempotent.
     * <p>
     * Payment providers re-send callbacks. Previously a duplicate reached the {@code markAs*}
     * precondition, which raised {@code BadInputException}; this service converted that into
     * {@code IllegalStateException} and the caller saw <b>HTTP 500</b> for what is a normal event.
     * <p>
     * A replay is now a no-op: the order is returned unchanged, so the controller issues the same
     * 303 redirect as the first time, and <b>no second transition is attempted</b>.
     */
    @DisplayName("D4: a replayed callback performs no second transition and returns the order unchanged")
    @Test
    void a_replayed_callback_is_a_no_op() throws Exception {
        OrderPojo alreadyPaid = salesHelper.orderPojoAfterCreation();
        alreadyPaid.setStatus(ORDER_STATUS_PAID_UNCONFIRMED);
        Map<String, String> matcherMap = Map.of("token", ORDER_TRANSACTION_TOKEN);
        when(ordersPredicateServiceMock.parseMap(matcherMap)).thenReturn(MATCHER_PREDICATE);
        when(ordersCrudServiceMock.readOne(MATCHER_PREDICATE)).thenReturn(alreadyPaid);

        OrderPojo result = service.confirmTransaction(ORDER_TRANSACTION_TOKEN, false, SOME_CALLBACK_NONCE);

        assertEquals(ORDER_STATUS_PAID_UNCONFIRMED, result.getStatus());
        verify(ordersProcessServiceMock, never()).markAsPaid(any());
        verify(ordersProcessServiceMock, never()).markAsFailed(any());
        // The provider is not consulted a second time either - no double commit.
        verifyNoInteractions(paymentServiceMock);
    }

    @DisplayName("Generate result page proper url to URI")
    @Test
    void generate_result_page_url() {
        when(paymentServiceMock.getPaymentResultPageUrl()).thenReturn("http://www.any.com");

        URI actual = service.generateResultPageUrl(ANY);

        assertEquals("http://www.any.com?token=ANY", actual.toString());
        verify(paymentServiceMock, times(ONE)).getPaymentResultPageUrl();
    }

    @DisplayName("Generate result page proper url to URI when result page generates malformed url then catch " +
        "MalformedURLException and throw IllegalArgumentException")
    @Test
    void generate_result_page_url_throws_illegal_argument_exception() {
        when(paymentServiceMock.getPaymentResultPageUrl()).thenReturn(ANY);

        assertThrows(IllegalStateException.class, () -> service.generateResultPageUrl(ANY), "Transaction was confirmed, but server had an unexpected malfunction");

        verify(paymentServiceMock, times(ONE)).getPaymentResultPageUrl();
    }
}
