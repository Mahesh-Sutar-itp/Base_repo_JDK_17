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

import com.querydsl.core.types.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.trebol.api.models.PaymentRedirectionDetailsPojo;
import org.trebol.api.models.OrderPojo;
import org.trebol.api.services.CheckoutService;
import org.trebol.api.services.OrdersProcessService;
import org.trebol.common.exceptions.BadInputException;
import org.trebol.jpa.services.crud.OrdersCrudService;
import org.trebol.jpa.services.predicates.OrdersPredicateService;
import org.trebol.payment.PaymentService;
import org.trebol.payment.PaymentServiceException;

import jakarta.persistence.EntityNotFoundException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import java.util.Set;
import java.util.UUID;

import static org.trebol.config.Constants.ORDER_STATUS_PAID_UNCONFIRMED;
import static org.trebol.config.Constants.ORDER_STATUS_PAYMENT_CANCELLED;
import static org.trebol.config.Constants.ORDER_STATUS_PAYMENT_FAILED;
import static org.trebol.config.Constants.ORDER_STATUS_PAYMENT_STARTED;

@Service
public class CheckoutServiceImpl
    implements CheckoutService {
    private final Logger logger = LoggerFactory.getLogger(CheckoutServiceImpl.class);
    private final OrdersCrudService ordersCrudService;
    private final OrdersProcessService ordersProcessService;
    private final OrdersPredicateService ordersPredicateService;
    private final PaymentService paymentIntegrationService;
    /**
     * Register item D4(b): the statuses a payment callback can itself produce.
     * <p>
     * An order already in one of these has had its callback processed, so a further callback is a
     * replay and is answered identically rather than raised as an error.
     */
    static final Set<String> ALREADY_RESOLVED_STATUSES = Set.of(
        ORDER_STATUS_PAYMENT_CANCELLED,
        ORDER_STATUS_PAYMENT_FAILED,
        ORDER_STATUS_PAID_UNCONFIRMED);
    static final String CALLBACK_NONCE_REJECTED = "The transaction callback could not be authenticated";

    @Autowired
    public CheckoutServiceImpl(
        OrdersCrudService ordersCrudService,
        OrdersProcessService ordersProcessService,
        OrdersPredicateService ordersPredicateService,
        PaymentService paymentIntegrationService
    ) {
        this.ordersCrudService = ordersCrudService;
        this.ordersProcessService = ordersProcessService;
        this.ordersPredicateService = ordersPredicateService;
        this.paymentIntegrationService = paymentIntegrationService;
    }

    /**
     * Begins a checkout.
     * <p>
     * <b>Register item D4:</b> a per-order callback nonce is generated here, before the payment
     * service is asked for a redirection URL, because the nonce has to be embedded in that URL. It
     * is then persisted by {@code markAsStarted} in the same transaction as the status transition,
     * so an order can never sit in {@code Payment Started} without the nonce that authenticates its
     * callback.
     * <p>
     * The nonce is <b>not</b> returned to the caller. It travels only to the payment provider, which
     * is what makes a callback presenting it evidence of origin.
     */
    @Override
    public PaymentRedirectionDetailsPojo requestTransactionStart(OrderPojo transaction) throws PaymentServiceException, BadInputException {
        String callbackNonce = newCallbackNonce();
        PaymentRedirectionDetailsPojo response = paymentIntegrationService.requestNewPaymentPageDetails(transaction, callbackNonce);
        try {
            transaction.setToken(response.getToken());
            ordersProcessService.markAsStarted(transaction, callbackNonce);
            return response;
        } catch (EntityNotFoundException exc) {
            throw new IllegalStateException("The server had a problem requesting the transaction", exc);
        }
    }

    /**
     * Confirms or aborts a payment, from a provider callback.
     * <p>
     * <b>Register item D4 made three changes here, all of them behavioural.</b>
     * <ol>
     *   <li><b>Origin authentication.</b> The callback must present the per-order nonce that was
     *       embedded in the {@code returnUrl} handed to the provider. Transbank sends no signature,
     *       so this is the only origin control available: a caller who did not receive that URL
     *       cannot produce the nonce. A missing or mismatched nonce is rejected before any
     *       transition is attempted.</li>
     *   <li><b>Idempotency.</b> Payment providers do re-send callbacks. Previously a duplicate
     *       produced a {@code BadInputException} from the {@code markAs*} precondition, which this
     *       method converted into an {@code IllegalStateException} and the client saw <b>HTTP
     *       500</b>. A replay is now a <b>no-op returning the order unchanged</b>, so the caller
     *       receives the same redirect as the first time.</li>
     *   <li><b>The lookup.</b> It used to pass a map key {@code "statusCode"} that
     *       {@code OrdersPredicateServiceImpl} does not recognise, so <b>the status filter was
     *       silently dropped</b> and the lookup was by token alone. The status is now checked
     *       explicitly, in code - see {@link #getSellRequestedWithMatchingToken}.</li>
     * </ol>
     */
    @Override
    public OrderPojo confirmTransaction(String transactionToken, boolean wasAborted, String callbackNonce)
        throws EntityNotFoundException, PaymentServiceException, BadInputException {
        OrderPojo sellByToken = this.getSellRequestedWithMatchingToken(transactionToken);
        this.assertCallbackNonceMatches(transactionToken, callbackNonce);

        if (ALREADY_RESOLVED_STATUSES.contains(sellByToken.getStatus())) {
            logger.info("Ignoring a repeated payment callback for an order already in '{}'",
                sellByToken.getStatus());
            return sellByToken;
        }

        try {
            if (wasAborted) {
                return ordersProcessService.markAsAborted(sellByToken);
            } else {
                return this.processSellPaymentStatus(sellByToken);
            }
        } catch (BadInputException e) {
            logger.error("Incorrect state of sell, was: {}", sellByToken.getStatus());
            throw new IllegalStateException("Transaction could not be confirmed");
        }
    }

    @Override
    public URI generateResultPageUrl(String transactionToken) {
        // TODO add a switch to either use path params or query params
        try {
            String url = (paymentIntegrationService.getPaymentResultPageUrl() + "?token=" + transactionToken);
            return new URL(url).toURI();
        } catch (MalformedURLException | URISyntaxException ex) {
            logger.error("Malformed redirection URL; make sure the 'final URL for payment method' property is correctly configured.", ex);
            throw new IllegalStateException("Transaction was confirmed, but server had an unexpected malfunction");
        }
    }

    /**
     * Fetches the result of a transaction from the payment payment service and updates it in the database.
     *
     * @throws EntityNotFoundException If no transaction has a matching token.
     * @throws PaymentServiceException As raised at payment level.
     */
    private OrderPojo processSellPaymentStatus(OrderPojo sellByToken)
        throws EntityNotFoundException, PaymentServiceException {
        int statusCode = paymentIntegrationService.requestPaymentResult(sellByToken.getToken());
        try {
            if (statusCode!=0) {
                return ordersProcessService.markAsFailed(sellByToken);
            } else {
                return ordersProcessService.markAsPaid(sellByToken);
            }
        } catch (BadInputException e) {
            logger.error("Incorrect state of sell, was: {}", sellByToken.getStatus());
            throw new IllegalStateException("Transaction could not be confirmed");
        }
    }

    /**
     * Resolves the order a callback refers to, <b>by token alone</b>.
     * <p>
     * Register item D4(c). This method used to build its predicate with a map key
     * {@code "statusCode"}, which {@code OrdersPredicateServiceImpl} does not recognise - its
     * switch accepts only {@code id}, {@code buyOrder}, {@code date}, {@code statusName} and
     * {@code token}. The status filter was therefore <b>silently dropped</b>, and the replay
     * protection the defect register credited to it never existed; the only thing rejecting a
     * repeated callback was the {@code markAs*} precondition, surfacing as HTTP 500.
     * <p>
     * The fix does <b>not</b> simply correct the key to {@code statusName}. Filtering by status in
     * the query makes an already-processed order <b>indistinguishable from a non-existent one</b> -
     * both come back as {@code EntityNotFoundException} - which makes idempotency impossible to
     * implement. The order is resolved by token in whatever state it is in, and the caller then
     * decides: a status in {@link #ALREADY_RESOLVED_STATUSES} is a replay, anything other than
     * {@code Payment Started} is rejected by the {@code markAs*} precondition exactly as before.
     */
    private OrderPojo getSellRequestedWithMatchingToken(String transactionToken) throws EntityNotFoundException {
        Map<String, String> tokenMatcher = new HashMap<>(Map.of(
            "token", transactionToken));
        Predicate transactionWithMatchingToken = ordersPredicateService.parseMap(tokenMatcher);
        return ordersCrudService.readOne(transactionWithMatchingToken);
    }

    /**
     * Register item D4(a): rejects a callback that does not present the order's nonce.
     * <p>
     * Compared with {@link java.security.MessageDigest#isEqual} semantics in mind, but a plain
     * equality check is used: the nonce is a 32-character random value, not a MAC, and there is no
     * oracle here that a timing attack could exploit - an attacker who can guess the nonce has
     * already won.
     *
     * @throws BadInputException if the order has no nonce recorded, or the presented value differs
     */
    private void assertCallbackNonceMatches(String transactionToken, String presentedNonce) throws BadInputException {
        String expectedNonce = ordersProcessService.findCallbackNonce(transactionToken);
        if (expectedNonce==null || expectedNonce.isBlank()
            || presentedNonce==null || !expectedNonce.equals(presentedNonce)) {
            // Deliberately does not say WHICH check failed, and does not echo either value.
            logger.warn("Rejected a payment callback presenting an invalid nonce");
            throw new BadInputException(CALLBACK_NONCE_REJECTED);
        }
    }

    /**
     * Register item D4(a): generates a per-order callback nonce.
     * <p>
     * A UUID without separators - 32 hexadecimal characters, 122 bits of randomness from
     * {@link UUID#randomUUID()}'s cryptographically strong source. It is not a MAC and does not
     * need to be: it only has to be unguessable and single-use, and it fits the column's 64
     * characters with room to spare.
     *
     * @return a fresh nonce
     */
    static String newCallbackNonce() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
