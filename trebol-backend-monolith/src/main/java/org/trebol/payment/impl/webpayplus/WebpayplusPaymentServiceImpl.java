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

package org.trebol.payment.impl.webpayplus;

import cl.transbank.webpay.exception.TransactionCommitException;
import cl.transbank.webpay.exception.TransactionCreateException;
import cl.transbank.webpay.webpayplus.WebpayPlus;
import cl.transbank.webpay.webpayplus.responses.WebpayPlusTransactionCreateResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.trebol.api.models.PaymentRedirectionDetailsPojo;
import org.trebol.api.models.OrderPojo;
import org.trebol.payment.PaymentService;
import org.trebol.payment.PaymentServiceException;

import java.io.IOException;
import java.util.UUID;

import static org.trebol.config.Constants.CHECKOUT_CALLBACK_NONCE_PARAM_NAME;

/**
 * Implements Webpay Plus as a payment gateway, using their official SDK.<br/>
 * Usage of Webpay Plus requires an affiliation to Transbank.
 * You can <a href="https://transbank.cl/">know more about Transbank here</a>.<br/><br/>
 * Documentation for the SDK and general guidelines for using Webpay Plus
 * <a href="https://transbankdevelopers.cl/referencia/webpay">can be found here</a>.
 */
@Service
public class WebpayplusPaymentServiceImpl
    implements PaymentService {
    private final Logger logger = LoggerFactory.getLogger(WebpayplusPaymentServiceImpl.class);
    private final WebpayplusPaymentProperties properties;
    private final WebpayTransactionFactory transactionFactory;

    @Autowired
    public WebpayplusPaymentServiceImpl(
        WebpayplusPaymentProperties properties,
        WebpayTransactionFactory transactionFactory
    ) {
        this.properties = properties;
        this.transactionFactory = transactionFactory;
    }

    /**
     * Asks Webpay for a payment page and the token that identifies the attempt.
     * <p>
     * <b>Register item D4 added the {@code callbackNonce} parameter.</b> It is appended to the
     * configured callback URL, so the URL handed to the provider is unguessable and single-purpose.
     * The provider sends no callback signature, so this is the only origin control available: only
     * the party we handed this URL to can produce a callback that passes verification.
     * <p>
     * The nonce is appended with {@code ?} or {@code &} depending on whether the configured URL
     * already carries a query string, so an operator's existing configuration keeps working either
     * way.
     *
     * @param callbackNonce the per-order nonce that the callback must present
     */
    @Override
    public PaymentRedirectionDetailsPojo requestNewPaymentPageDetails(OrderPojo transaction, String callbackNonce) throws PaymentServiceException {
        String buyOrder = transaction.getBuyOrder().toString();
        String sessionId = newSessionId();
        double amount = transaction.getTotalValue();
        String returnUrl = appendCallbackNonce(properties.getCallbackUrl(), callbackNonce);

        WebpayPlus.Transaction webpayTransaction = transactionFactory.newTransaction();

        try {
            WebpayPlusTransactionCreateResponse webpayResponse = webpayTransaction.create(
                buyOrder, sessionId, amount, returnUrl
            );
            return PaymentRedirectionDetailsPojo.builder()
                .url(webpayResponse.getUrl())
                .token(webpayResponse.getToken())
                .build();
        } catch (TransactionCreateException | IOException exc) {
            logger.error("Exception raised while creating transaction: ", exc);
            throw new PaymentServiceException("Webpay could not create a new transaction");
        }
    }

    /**
     * Requests the result of a payment from Webpay.
     * <p>
     * <b>Register item D3 changed this method's behaviour, deliberately.</b> A
     * {@link TransactionCommitException} used to be swallowed and reported as the magic value
     * {@code 1}, with no log and no cause. {@code CheckoutServiceImpl} treats any non-zero code as a
     * decline, so <b>a Transbank outage was indistinguishable from a customer declining the
     * payment</b>, and the customer's order was moved to {@code Payment Failed} on the strength of a
     * fabricated code.
     * <p>
     * It is now treated as what it is - an infrastructure failure - and raised as
     * {@link PaymentServiceException}, exactly as the {@link IOException} branch beside it already
     * did. The order consequently keeps its {@code Payment Started} state, which is the truthful
     * one and leaves it eligible for a later legitimate callback.
     * <p>
     * The literal message is reused rather than invented, so no new client-visible string appears.
     *
     * @return the provider's response code - {@code 0} for success, any other value being a genuine
     *     decline reported by the provider itself
     * @throws PaymentServiceException if the provider could not be reached or could not commit
     */
    @Override
    public int requestPaymentResult(String transactionToken) throws PaymentServiceException {
        WebpayPlus.Transaction webpayTransaction = transactionFactory.newTransaction();
        try {
            return webpayTransaction.commit(transactionToken).getResponseCode();
        } catch (TransactionCommitException | IOException exc) {
            logger.error("Exception raised while requesting transaction result: ", exc);
            throw new PaymentServiceException("Webpay failed to confirm the transaction");
        }
    }

    @Override
    public String getPaymentResultPageUrl() {
        return properties.getBrowserRedirectionUrl();
    }

    /**
     * Register item D9: the session identifier used to be {@code transaction.hashCode()}, which is
     * neither unique nor stable across processes - two instances of the application can produce the
     * same value for different orders, and the same order yields different values after a restart.
     * A UUID is process-independent and collision-free. The value is not persisted anywhere, so no
     * schema change is involved. Webpay allows up to 61 characters; this is 32.
     *
     * @return a fresh payment session identifier
     */
    static String newSessionId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Register item D4: appends the callback nonce to the configured return URL.
     * <p>
     * A blank nonce leaves the URL untouched, so a caller that has none cannot silently produce a
     * URL carrying an empty parameter - the callback would then be rejected by the verification
     * step, which is the correct outcome.
     *
     * @param callbackUrl the configured callback URL, with or without an existing query string
     * @param nonce       the per-order nonce
     * @return the URL the provider should return the customer to
     */
    static String appendCallbackNonce(String callbackUrl, String nonce) {
        if (callbackUrl==null || nonce==null || nonce.isBlank()) {
            return callbackUrl;
        }
        String separator = callbackUrl.contains("?") ? "&":"?";
        return callbackUrl + separator + CHECKOUT_CALLBACK_NONCE_PARAM_NAME + "=" + nonce;
    }

}
