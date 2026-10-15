package org.trebol.payment.impl.webpayplus;

import cl.transbank.webpay.exception.TransactionCommitException;
import cl.transbank.webpay.exception.TransactionCreateException;
import cl.transbank.webpay.webpayplus.WebpayPlus;
import cl.transbank.webpay.webpayplus.responses.WebpayPlusTransactionCommitResponse;
import cl.transbank.webpay.webpayplus.responses.WebpayPlusTransactionCreateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.trebol.api.models.OrderPojo;
import org.trebol.api.models.PaymentRedirectionDetailsPojo;
import org.trebol.payment.PaymentServiceException;
import org.trebol.testing.OrdersTestHelper;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.trebol.testing.TestConstants.ANY;

/**
 * Register item D18: the payment adapter's unit tests.
 * <p>
 * <b>This class used to be an integration test presented as a unit test.</b> It mocked only
 * {@link WebpayplusPaymentProperties}, while the adapter constructed
 * {@code new WebpayPlus.Transaction(...)} inline - so every test made a <b>real HTTP call to the
 * Transbank sandbox</b> and failed with HTTP 403 off-network. Three of its tests were in the
 * project's "known failures" set from the first baseline onward, and one of them took ~270 seconds
 * to fail because it was waiting on a network timeout.
 * <p>
 * The consequence was the serious part: <b>the payment adapter had no genuine unit-test coverage</b>
 * - the most critical and least safely changeable component in the system, and the one Phase 3 must
 * port with the D3 and D4 fixes applied. Four catch branches were simply untestable.
 * <p>
 * It is rewritten here against the {@link WebpayTransactionFactory} seam, which lets the SDK be
 * substituted. <b>No network access, and no test takes longer than milliseconds.</b> Every branch of
 * both SDK-facing methods is now covered, including all four exception paths.
 * <p>
 * G3 permits modifying tests for compatibility only, so this improvement carries an explicit ruling
 * in {@code defect-register-rulings.md} and is landed as its own reviewed change, ahead of the D3
 * and D4 fixes that depend on it.
 */
@ExtendWith(MockitoExtension.class)
class WebpayplusPaymentServiceImplTest {
    @InjectMocks WebpayplusPaymentServiceImpl instance;
    @Mock WebpayplusPaymentProperties propertiesMock;
    @Mock WebpayTransactionFactory transactionFactoryMock;
    @Mock WebpayPlus.Transaction webpayTransactionMock;
    final OrdersTestHelper ordersHelper = new OrdersTestHelper();
    static final String RETURN_URL = "http://localhost";
    static final String SOME_TOKEN = "some-transaction-token";
    static final String SOME_URL = "https://webpay.example/redirect";
    static final String CREATE_FAILURE_MESSAGE = "Webpay could not create a new transaction";
    static final String COMMIT_FAILURE_MESSAGE = "Webpay failed to confirm the transaction";

    @BeforeEach
    void beforeEach() {
        ordersHelper.resetOrders();
    }

    @Nested
    class RequestNewPaymentPageDetails {
        @Test
        void fetches_payment_page_for_started_transactions() throws Exception {
            OrderPojo input = ordersHelper.orderPojoAfterCreation();
            WebpayPlusTransactionCreateResponse response = new WebpayPlusTransactionCreateResponse();
            response.setToken(SOME_TOKEN);
            response.setUrl(SOME_URL);
            when(propertiesMock.getCallbackUrl()).thenReturn(RETURN_URL);
            when(transactionFactoryMock.newTransaction()).thenReturn(webpayTransactionMock);
            when(webpayTransactionMock.create(anyString(), anyString(), anyDouble(), anyString()))
                .thenReturn(response);

            PaymentRedirectionDetailsPojo result = instance.requestNewPaymentPageDetails(input);

            assertNotNull(result);
            assertEquals(SOME_TOKEN, result.getToken());
            assertEquals(SOME_URL, result.getUrl());
        }

        /**
         * Pins the four arguments the SDK is handed, in order. The buy order is the order id as a
         * string, the session id is a fresh UUID (register item D9), the amount is the order's total
         * value, and the return url comes from configuration.
         */
        @Test
        void passes_the_order_details_through_to_the_sdk() throws Exception {
            OrderPojo input = ordersHelper.orderPojoAfterCreation();
            WebpayPlusTransactionCreateResponse response = new WebpayPlusTransactionCreateResponse();
            response.setToken(SOME_TOKEN);
            response.setUrl(SOME_URL);
            when(propertiesMock.getCallbackUrl()).thenReturn(RETURN_URL);
            when(transactionFactoryMock.newTransaction()).thenReturn(webpayTransactionMock);
            when(webpayTransactionMock.create(anyString(), anyString(), anyDouble(), anyString()))
                .thenReturn(response);

            instance.requestNewPaymentPageDetails(input);

            // Every argument must be a matcher once any of them is - the session id is a fresh
            // UUID per call (register item D9), so it is matched by shape rather than by value.
            verify(webpayTransactionMock).create(
                eq(input.getBuyOrder().toString()),
                argThat(sessionId ->
                    sessionId != null && sessionId.length() == 32 && !sessionId.contains("-")),
                eq((double) input.getTotalValue()),
                eq(RETURN_URL));
        }

        /**
         * Previously untestable: reaching this branch required the sandbox to reject the request.
         */
        @Test
        void wraps_a_transaction_create_exception() throws Exception {
            OrderPojo input = ordersHelper.orderPojoAfterCreation();
            when(propertiesMock.getCallbackUrl()).thenReturn(RETURN_URL);
            when(transactionFactoryMock.newTransaction()).thenReturn(webpayTransactionMock);
            when(webpayTransactionMock.create(anyString(), anyString(), anyDouble(), anyString()))
                .thenThrow(new TransactionCreateException(ANY));

            PaymentServiceException result = assertThrows(PaymentServiceException.class,
                () -> instance.requestNewPaymentPageDetails(input));

            assertEquals(CREATE_FAILURE_MESSAGE, result.getMessage());
        }

        /** Previously untestable: reaching this branch required a network-level failure. */
        @Test
        void wraps_an_io_exception_on_create() throws Exception {
            OrderPojo input = ordersHelper.orderPojoAfterCreation();
            when(propertiesMock.getCallbackUrl()).thenReturn(RETURN_URL);
            when(transactionFactoryMock.newTransaction()).thenReturn(webpayTransactionMock);
            when(webpayTransactionMock.create(anyString(), anyString(), anyDouble(), anyString()))
                .thenThrow(new IOException(ANY));

            PaymentServiceException result = assertThrows(PaymentServiceException.class,
                () -> instance.requestNewPaymentPageDetails(input));

            assertEquals(CREATE_FAILURE_MESSAGE, result.getMessage());
        }
    }

    @Nested
    class RequestPaymentResult {
        @Test
        void returns_the_response_code_from_the_sdk() throws Exception {
            WebpayPlusTransactionCommitResponse response = new WebpayPlusTransactionCommitResponse();
            response.setResponseCode((byte) 0);
            when(transactionFactoryMock.newTransaction()).thenReturn(webpayTransactionMock);
            when(webpayTransactionMock.commit(anyString())).thenReturn(response);

            assertEquals(0, instance.requestPaymentResult(SOME_TOKEN));
            verify(webpayTransactionMock).commit(SOME_TOKEN);
        }

        /**
         * A genuine customer decline. The adapter passes the provider's non-zero code straight
         * through, and {@code CheckoutServiceImpl} is what turns it into a Payment Failed
         * transition. Distinguishing this case from an infrastructure failure is exactly what
         * register item D3 is about, and it is only testable at all because of the D18 seam.
         */
        @Test
        void passes_a_non_zero_decline_code_through_unchanged() throws Exception {
            WebpayPlusTransactionCommitResponse response = new WebpayPlusTransactionCommitResponse();
            response.setResponseCode((byte) -1);
            when(transactionFactoryMock.newTransaction()).thenReturn(webpayTransactionMock);
            when(webpayTransactionMock.commit(anyString())).thenReturn(response);

            assertEquals(-1, instance.requestPaymentResult(SOME_TOKEN));
        }

        /** Previously untestable: reaching this branch required a network-level failure. */
        @Test
        void wraps_an_io_exception_on_commit() throws Exception {
            when(transactionFactoryMock.newTransaction()).thenReturn(webpayTransactionMock);
            when(webpayTransactionMock.commit(anyString())).thenThrow(new IOException(ANY));

            PaymentServiceException result = assertThrows(PaymentServiceException.class,
                () -> instance.requestPaymentResult(SOME_TOKEN));

            assertEquals(COMMIT_FAILURE_MESSAGE, result.getMessage());
        }

        /**
         * The {@link TransactionCommitException} branch - register item <b>D3</b>.
         * <p>
         * At the time this test was written the adapter still swallowed this exception and returned
         * the magic value {@code 1}, which {@code CheckoutServiceImpl} cannot distinguish from a
         * genuine decline. This test pins <b>the current, defective behaviour</b> so that the D3 fix
         * in the next step has something to invert, and so the inversion is visible in a diff rather
         * than silent. See {@code defect-register-rulings.md}, register item D3.
         */
        @Test
        void currently_swallows_a_commit_exception_and_returns_one() throws Exception {
            when(transactionFactoryMock.newTransaction()).thenReturn(webpayTransactionMock);
            when(webpayTransactionMock.commit(anyString())).thenThrow(new TransactionCommitException(ANY));

            assertEquals(1, instance.requestPaymentResult(SOME_TOKEN));
        }
    }

    @Nested
    class ResultPageUrl {
        @Test
        void fetches_payment_result_page_url() {
            when(propertiesMock.getBrowserRedirectionUrl()).thenReturn(ANY);
            String result = instance.getPaymentResultPageUrl();
            assertNotNull(result);
            assertEquals(ANY, result);
        }
    }
}
