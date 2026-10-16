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

package org.trebol.mailing.outbox.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.trebol.api.models.OrderPojo;
import org.trebol.jpa.entities.OrderMailOutboxEntry;
import org.trebol.jpa.repositories.OrderMailOutboxRepository;
import org.trebol.mailing.MailingService;
import org.trebol.mailing.outbox.MailOutboxService;
import org.trebol.mailing.outbox.MailRecipientKind;

import java.time.Instant;

/**
 * Register item D11: writes outbox entries, inside the caller's transaction.
 * <p>
 * {@code @Transactional(propagation = MANDATORY)} is deliberate and load-bearing. It makes the
 * contract's requirement into a compile-time-ish guarantee: if a caller ever invokes this outside a
 * transaction, Spring raises {@code IllegalTransactionStateException} immediately rather than
 * quietly starting a second transaction. A second transaction would reintroduce the exact defect
 * D11 exists to fix - a notification that can be lost independently of the transition.
 * <p>
 * The {@code MailingService} is injected only to answer "is mailing active at all?". This service
 * never sends anything.
 */
@Service
public class MailOutboxServiceImpl
    implements MailOutboxService {
    private final Logger logger = LoggerFactory.getLogger(MailOutboxServiceImpl.class);
    private final OrderMailOutboxRepository outboxRepository;
    @Nullable
    private final MailingService mailingService;

    @Autowired
    public MailOutboxServiceImpl(
        OrderMailOutboxRepository outboxRepository,
        @Autowired(required = false) MailingService mailingService
    ) {
        this.outboxRepository = outboxRepository;
        this.mailingService = mailingService;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(OrderPojo order, MailRecipientKind recipient) {
        if (mailingService==null) {
            // Preserves the baseline: with no mailing profile active there was no mail and no
            // error. Writing entries nothing will ever drain would be a slow-growing leak.
            return;
        }
        if (order==null || order.getBuyOrder()==null) {
            // Not an exception: a notification is not worth failing a committed transition for.
            logger.warn("Not recording a {} notification - the order has no identifier", recipient);
            return;
        }
        outboxRepository.save(OrderMailOutboxEntry.builder()
            .orderId(order.getBuyOrder())
            .recipientKind(recipient.name())
            .orderStatusName(order.getStatus())
            .attempts(0)
            .createdAt(Instant.now())
            .build());
    }
}
