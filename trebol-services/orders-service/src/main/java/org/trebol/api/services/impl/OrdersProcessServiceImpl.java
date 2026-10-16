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

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.trebol.api.models.OrderDetailPojo;
import org.trebol.api.models.OrderPojo;
import org.trebol.api.services.OrdersProcessService;
import org.trebol.common.exceptions.BadInputException;
import org.trebol.jpa.entities.Order;
import org.trebol.jpa.entities.OrderDetail;
import org.trebol.jpa.entities.OrderStatus;
import org.trebol.jpa.repositories.OrdersRepository;
import org.trebol.jpa.repositories.OrderDetailsRepository;
import org.trebol.jpa.repositories.OrderStatusesRepository;
import org.trebol.jpa.services.OrderStatusTransitions;
import org.trebol.mailing.outbox.MailOutboxService;
import org.trebol.mailing.outbox.MailRecipientKind;
import org.trebol.jpa.services.conversion.OrdersConverterService;
import org.trebol.jpa.services.crud.OrdersCrudService;

import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.trebol.config.Constants.ORDER_STATUS_COMPLETED;
import static org.trebol.config.Constants.ORDER_STATUS_PAID_CONFIRMED;
import static org.trebol.config.Constants.ORDER_STATUS_PAID_UNCONFIRMED;
import static org.trebol.config.Constants.ORDER_STATUS_PAYMENT_CANCELLED;
import static org.trebol.config.Constants.ORDER_STATUS_PAYMENT_FAILED;
import static org.trebol.config.Constants.ORDER_STATUS_PAYMENT_STARTED;
import static org.trebol.config.Constants.ORDER_STATUS_PENDING;
import static org.trebol.config.Constants.ORDER_STATUS_REJECTED;

@Transactional
@Service
public class OrdersProcessServiceImpl
    implements OrdersProcessService {
    private static final String THE_TRANSACTION_IS_NOT_IN_A_VALID_STATE_FOR_THIS_OPERATION = "The transaction is not in a valid state for this api";
    private static final String NO_STATUS_MATCHES_THE = "No status matches the";
    private static final String NAME_IS_THE_DATABASE_EMPTY_OR_CORRUPT = "name - Is the database empty or corrupt?";
    private final OrdersCrudService crudService;
    private final OrdersRepository ordersRepository;
    private final OrderDetailsRepository orderDetailsRepository;
    private final OrderStatusesRepository orderStatusesRepository;
    private final OrdersConverterService converterService;
    private final MailOutboxService mailOutboxService;

    public OrdersProcessServiceImpl(
        OrdersCrudService crudService,
        OrdersRepository ordersRepository,
        OrderDetailsRepository orderDetailsRepository,
        OrderStatusesRepository orderStatusesRepository,
        OrdersConverterService converterService,
        MailOutboxService mailOutboxService
    ) {
        this.crudService = crudService;
        this.ordersRepository = ordersRepository;
        this.orderDetailsRepository = orderDetailsRepository;
        this.orderStatusesRepository = orderStatusesRepository;
        this.converterService = converterService;
        this.mailOutboxService = mailOutboxService;
    }

    // TODO figure out how to shorten below methods
    // TODO to compare statuses use numbers, not strings
    /**
     * Register item D4: reads back the payment-callback nonce recorded for an order.
     *
     * @param transactionToken the token the payment provider issued for the order
     * @return the recorded nonce, or {@code null} if the order has none or does not exist
     */
    @Override
    @Transactional(readOnly = true)
    public String findCallbackNonce(String transactionToken) {
        return ordersRepository.findByTransactionToken(transactionToken)
            .map(Order::getCallbackNonce)
            .orElse(null);
    }

    /**
     * Moves an order to {@code Payment Started} and records the payment-callback nonce.
     * <p>
     * <b>Register item D4 added the {@code callbackNonce} parameter.</b> The nonce is generated by
     * the caller - {@code CheckoutServiceImpl}, which must have it before it asks the payment
     * service for a redirection URL, because the nonce is embedded in that URL. This method
     * persists it in the same transaction as the status transition, so an order is never in
     * {@code Payment Started} without the nonce that authenticates its callback.
     *
     * @param sell          the order to transition
     * @param callbackNonce the nonce embedded in the {@code returnUrl} handed to the provider
     */
    @Override
    public OrderPojo markAsStarted(OrderPojo sell, String callbackNonce) throws BadInputException, EntityNotFoundException {
        Order existingOrder = this.fetchExistingOrThrowException(sell);

        if (!OrderStatusTransitions.isLegal(existingOrder.getStatus().getName(), ORDER_STATUS_PAYMENT_STARTED)) {
            throw new BadInputException(THE_TRANSACTION_IS_NOT_IN_A_VALID_STATE_FOR_THIS_OPERATION);
        }

        Optional<OrderStatus> startedStatus = orderStatusesRepository.findByName(ORDER_STATUS_PAYMENT_STARTED);
        if (startedStatus.isEmpty()) {
            throw new IllegalStateException(NO_STATUS_MATCHES_THE + " '" + ORDER_STATUS_PAYMENT_STARTED + "' " + NAME_IS_THE_DATABASE_EMPTY_OR_CORRUPT);
        }
        ordersRepository.setStatus(existingOrder.getId(), startedStatus.get());
        ordersRepository.setTransactionToken(existingOrder.getId(), sell.getToken());
        ordersRepository.setCallbackNonce(existingOrder.getId(), callbackNonce);

        OrderPojo target = this.convertOrThrowException(existingOrder);
        target.setStatus(ORDER_STATUS_PAYMENT_STARTED);
        return target;
    }

    @Override
    public OrderPojo markAsAborted(OrderPojo sell) throws BadInputException, EntityNotFoundException {
        Order existingOrder = this.fetchExistingOrThrowException(sell);

        if (!OrderStatusTransitions.isLegal(existingOrder.getStatus().getName(), ORDER_STATUS_PAYMENT_CANCELLED)) {
            throw new BadInputException(THE_TRANSACTION_IS_NOT_IN_A_VALID_STATE_FOR_THIS_OPERATION);
        }

        Optional<OrderStatus> abortedStatus = orderStatusesRepository.findByName(ORDER_STATUS_PAYMENT_CANCELLED);
        if (abortedStatus.isEmpty()) {
            throw new IllegalStateException(NO_STATUS_MATCHES_THE + " '" + ORDER_STATUS_PAYMENT_CANCELLED + "' " + NAME_IS_THE_DATABASE_EMPTY_OR_CORRUPT);
        }
        ordersRepository.setStatus(existingOrder.getId(), abortedStatus.get());

        OrderPojo target = this.convertOrThrowException(existingOrder);
        target.setStatus(ORDER_STATUS_PAYMENT_CANCELLED);
        return target;
    }

    @Override
    public OrderPojo markAsFailed(OrderPojo sell) throws BadInputException, EntityNotFoundException {
        Order existingOrder = this.fetchExistingOrThrowException(sell);

        if (!OrderStatusTransitions.isLegal(existingOrder.getStatus().getName(), ORDER_STATUS_PAYMENT_FAILED)) {
            throw new BadInputException(THE_TRANSACTION_IS_NOT_IN_A_VALID_STATE_FOR_THIS_OPERATION);
        }

        Optional<OrderStatus> failedStatus = orderStatusesRepository.findByName(ORDER_STATUS_PAYMENT_FAILED);
        if (failedStatus.isEmpty()) {
            throw new IllegalStateException(NO_STATUS_MATCHES_THE + " '" + ORDER_STATUS_PAYMENT_FAILED + "' " + NAME_IS_THE_DATABASE_EMPTY_OR_CORRUPT);
        }
        ordersRepository.setStatus(existingOrder.getId(), failedStatus.get());

        OrderPojo target = this.convertOrThrowException(existingOrder);
        target.setStatus(ORDER_STATUS_PAYMENT_FAILED);
        // Register item D11: recorded in THIS transaction, alongside the transition the
        // notification is about. The controllers used to send mail after this method had
        // already committed, so a mail failure surfaced as an error with the status
        // changed and the notification lost.
        mailOutboxService.enqueue(target, MailRecipientKind.CUSTOMER);
        return target;
    }

    @Override
    public OrderPojo markAsPaid(OrderPojo sell) throws BadInputException, EntityNotFoundException {
        Order existingOrder = this.fetchExistingOrThrowException(sell);

        if (!OrderStatusTransitions.isLegal(existingOrder.getStatus().getName(), ORDER_STATUS_PAID_UNCONFIRMED)) {
            throw new BadInputException(THE_TRANSACTION_IS_NOT_IN_A_VALID_STATE_FOR_THIS_OPERATION);
        }

        Optional<OrderStatus> paidStatus = orderStatusesRepository.findByName(ORDER_STATUS_PAID_UNCONFIRMED);
        if (paidStatus.isEmpty()) {
            throw new IllegalStateException(NO_STATUS_MATCHES_THE + " '" + ORDER_STATUS_PAID_UNCONFIRMED + "' " + NAME_IS_THE_DATABASE_EMPTY_OR_CORRUPT);
        }
        ordersRepository.setStatus(existingOrder.getId(), paidStatus.get());

        OrderPojo target = this.convertOrThrowException(existingOrder);

        List<OrderDetailPojo> pojoDetails = new ArrayList<>();
        for (OrderDetail detail : orderDetailsRepository.findBySellId(existingOrder.getId())) {
            // Phase 3 cut C1: the product is no longer an association on the line, so resolution
            // goes through the converter - which owns the single remote-lookup policy. These four
            // sites used to build the pojo inline, which meant four places to keep in step.
            pojoDetails.add(converterService.convertDetailToPojo(detail));
        }
        target.setStatus(ORDER_STATUS_PAID_UNCONFIRMED);
        target.setDetails(pojoDetails);

        // Register item D11: recorded in THIS transaction, alongside the transition the
        // notification is about. The controllers used to send mail after this method had
        // already committed, so a mail failure surfaced as an error with the status
        // changed and the notification lost.
        mailOutboxService.enqueue(target, MailRecipientKind.CUSTOMER);
        return target;
    }

    @Override
    public OrderPojo markAsConfirmed(OrderPojo sell)
        throws BadInputException, EntityNotFoundException {
        Order existingOrder = this.fetchExistingOrThrowException(sell);

        if (!OrderStatusTransitions.isLegal(existingOrder.getStatus().getName(), ORDER_STATUS_PAID_CONFIRMED)) {
            throw new BadInputException(THE_TRANSACTION_IS_NOT_IN_A_VALID_STATE_FOR_THIS_OPERATION);
        }

        Optional<OrderStatus> confirmedStatus = orderStatusesRepository.findByName(ORDER_STATUS_PAID_CONFIRMED);
        if (confirmedStatus.isEmpty()) {
            throw new IllegalStateException(NO_STATUS_MATCHES_THE + " '" + ORDER_STATUS_PAID_CONFIRMED + "' " + NAME_IS_THE_DATABASE_EMPTY_OR_CORRUPT);
        }
        ordersRepository.setStatus(existingOrder.getId(), confirmedStatus.get());

        OrderPojo target = this.convertOrThrowException(existingOrder);

        List<OrderDetailPojo> pojoDetails = new ArrayList<>();
        for (OrderDetail detail : orderDetailsRepository.findBySellId(existingOrder.getId())) {
            // Phase 3 cut C1: the product is no longer an association on the line, so resolution
            // goes through the converter - which owns the single remote-lookup policy. These four
            // sites used to build the pojo inline, which meant four places to keep in step.
            pojoDetails.add(converterService.convertDetailToPojo(detail));
        }
        target.setDetails(pojoDetails);
        target.setStatus(ORDER_STATUS_PAID_CONFIRMED);


        // Register item D11: recorded in THIS transaction, alongside the transition the
        // notification is about. The controllers used to send mail after this method had
        // already committed, so a mail failure surfaced as an error with the status
        // changed and the notification lost.
        mailOutboxService.enqueue(target, MailRecipientKind.CUSTOMER);
        mailOutboxService.enqueue(target, MailRecipientKind.OWNERS);
        return target;
    }

    @Override
    public OrderPojo markAsRejected(OrderPojo sell)
        throws BadInputException, EntityNotFoundException {
        Order existingOrder = this.fetchExistingOrThrowException(sell);

        if (!OrderStatusTransitions.isLegal(existingOrder.getStatus().getName(), ORDER_STATUS_REJECTED)) {
            throw new BadInputException(THE_TRANSACTION_IS_NOT_IN_A_VALID_STATE_FOR_THIS_OPERATION);
        }

        Optional<OrderStatus> rejectedStatus = orderStatusesRepository.findByName(ORDER_STATUS_REJECTED);
        if (rejectedStatus.isEmpty()) {
            throw new IllegalStateException(NO_STATUS_MATCHES_THE + " '" + ORDER_STATUS_REJECTED + "' " + NAME_IS_THE_DATABASE_EMPTY_OR_CORRUPT);
        }
        ordersRepository.setStatus(existingOrder.getId(), rejectedStatus.get());

        OrderPojo target = this.convertOrThrowException(existingOrder);

        List<OrderDetailPojo> pojoDetails = new ArrayList<>();
        for (OrderDetail detail : orderDetailsRepository.findBySellId(existingOrder.getId())) {
            // Phase 3 cut C1: the product is no longer an association on the line, so resolution
            // goes through the converter - which owns the single remote-lookup policy. These four
            // sites used to build the pojo inline, which meant four places to keep in step.
            pojoDetails.add(converterService.convertDetailToPojo(detail));
        }
        target.setDetails(pojoDetails);
        target.setStatus(ORDER_STATUS_REJECTED);

        // Register item D11: recorded in THIS transaction, alongside the transition the
        // notification is about. The controllers used to send mail after this method had
        // already committed, so a mail failure surfaced as an error with the status
        // changed and the notification lost.
        mailOutboxService.enqueue(target, MailRecipientKind.CUSTOMER);
        return target;
    }

    @Override
    public OrderPojo markAsCompleted(OrderPojo sell)
        throws BadInputException, EntityNotFoundException {
        Order existingOrder = this.fetchExistingOrThrowException(sell);

        if (!OrderStatusTransitions.isLegal(existingOrder.getStatus().getName(), ORDER_STATUS_COMPLETED)) {
            throw new BadInputException(THE_TRANSACTION_IS_NOT_IN_A_VALID_STATE_FOR_THIS_OPERATION);
        }

        Optional<OrderStatus> completedStatus = orderStatusesRepository.findByName(ORDER_STATUS_COMPLETED);
        if (completedStatus.isEmpty()) {
            throw new IllegalStateException(NO_STATUS_MATCHES_THE + " '" + ORDER_STATUS_COMPLETED + "' " + NAME_IS_THE_DATABASE_EMPTY_OR_CORRUPT);
        }
        ordersRepository.setStatus(existingOrder.getId(), completedStatus.get());

        OrderPojo target = this.convertOrThrowException(existingOrder);

        List<OrderDetailPojo> pojoDetails = new ArrayList<>();
        for (OrderDetail detail : orderDetailsRepository.findBySellId(existingOrder.getId())) {
            // Phase 3 cut C1: the product is no longer an association on the line, so resolution
            // goes through the converter - which owns the single remote-lookup policy. These four
            // sites used to build the pojo inline, which meant four places to keep in step.
            pojoDetails.add(converterService.convertDetailToPojo(detail));
        }
        target.setDetails(pojoDetails);
        target.setStatus(ORDER_STATUS_COMPLETED);

        // Register item D11: recorded in THIS transaction, alongside the transition the
        // notification is about. The controllers used to send mail after this method had
        // already committed, so a mail failure surfaced as an error with the status
        // changed and the notification lost.
        mailOutboxService.enqueue(target, MailRecipientKind.CUSTOMER);
        return target;
    }

    private Order fetchExistingOrThrowException(OrderPojo sell) throws BadInputException {
        Optional<Order> match = crudService.getExisting(sell);
        if (match.isEmpty()) {
            throw new EntityNotFoundException("No transaction matches given input");
        }
        return match.get();
    }

    private OrderPojo convertOrThrowException(Order existingOrder) {
        Order freshInstance = ordersRepository.getById(existingOrder.getId());
        OrderPojo target = converterService.convertToPojo(freshInstance);
        if (target==null) {
            throw new IllegalStateException("Converter could not turn Sell into its Pojo equivalent");
        }
        return target;
    }
}
