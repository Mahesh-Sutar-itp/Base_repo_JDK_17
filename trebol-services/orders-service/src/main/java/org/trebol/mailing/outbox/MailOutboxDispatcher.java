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

package org.trebol.mailing.outbox;

import com.querydsl.core.types.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.trebol.api.models.OrderPojo;
import org.trebol.jpa.entities.OrderMailOutboxEntry;
import org.trebol.jpa.repositories.OrderMailOutboxRepository;
import org.trebol.jpa.services.crud.OrdersCrudService;
import org.trebol.jpa.services.predicates.OrdersPredicateService;
import org.trebol.mailing.MailingService;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.abbreviate;

/**
 * Register item <b>D11</b>: drains the order-status notification outbox.
 * <p>
 * This is the half of the outbox that sends. It runs on a fixed delay, takes a bounded batch of
 * undispatched entries, and calls the <b>existing, unchanged</b> {@code MailingService} contract -
 * so Mailgun behaviour, templates, subjects and both verbatim exception messages are untouched by
 * D11.
 * <p>
 * Properties that govern it, all with defaults so an operator who configures nothing still gets
 * working behaviour:
 * <ul>
 *   <li>{@code trebol.mailing.outbox.fixed-delay-ms} - how often to drain (default 60s)</li>
 *   <li>{@code trebol.mailing.outbox.batch-size} - entries per run (default 50)</li>
 *   <li>{@code trebol.mailing.outbox.max-attempts} - retry bound (default 5)</li>
 * </ul>
 * <p>
 * <b>Each entry is dispatched in its own transaction.</b> One unreachable recipient must not roll
 * back the successful sends beside it, and an entry that keeps failing must accumulate attempts
 * rather than retrying forever inside a single long transaction.
 * <p>
 * <b>Semantics deliberately preserved from the baseline.</b> Only four customer statuses and three
 * owner statuses have templates; any other status produces no mail and no error. That rule lives in
 * the mailing adapter and is not re-implemented here - this dispatcher calls the adapter and treats
 * "nothing to send" as success, exactly as the synchronous call sites did.
 */
@Component
public class MailOutboxDispatcher {
    private final Logger logger = LoggerFactory.getLogger(MailOutboxDispatcher.class);
    private final OrderMailOutboxRepository outboxRepository;
    private final OrdersCrudService ordersCrudService;
    private final OrdersPredicateService ordersPredicateService;
    @Nullable
    private final MailingService mailingService;
    private final int batchSize;
    private final int maxAttempts;

    @Autowired
    public MailOutboxDispatcher(
        OrderMailOutboxRepository outboxRepository,
        OrdersCrudService ordersCrudService,
        OrdersPredicateService ordersPredicateService,
        @Autowired(required = false) MailingService mailingService,
        @Value("${trebol.mailing.outbox.batch-size:50}") int batchSize,
        @Value("${trebol.mailing.outbox.max-attempts:5}") int maxAttempts
    ) {
        this.outboxRepository = outboxRepository;
        this.ordersCrudService = ordersCrudService;
        this.ordersPredicateService = ordersPredicateService;
        this.mailingService = mailingService;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    /**
     * Drains one batch. Never throws: a scheduled method that throws is silently disabled by some
     * schedulers and logs a stack trace per tick in others, and neither helps.
     */
    @Scheduled(
        fixedDelayString = "${trebol.mailing.outbox.fixed-delay-ms:60000}",
        initialDelayString = "${trebol.mailing.outbox.initial-delay-ms:60000}")
    public void dispatchPending() {
        if (mailingService==null) {
            return;
        }
        try {
            List<OrderMailOutboxEntry> pending =
                outboxRepository.findPending(maxAttempts, PageRequest.of(0, batchSize));
            for (OrderMailOutboxEntry entry : pending) {
                this.dispatchOne(entry);
            }
        } catch (RuntimeException exc) {
            logger.error("The mail outbox dispatcher failed to drain a batch", exc);
        }
    }

    /**
     * Attempts one entry, in its own transaction.
     * <p>
     * The attempt counter is incremented <b>before</b> the send, and the row is saved whether the
     * send succeeds or fails. An entry that fails {@code maxAttempts} times stops being selected by
     * {@link OrderMailOutboxRepository#findPending} and retains its last error for diagnosis -
     * abandoned, but not silently: the row is the record that a notification was owed and never
     * sent, which is exactly what the baseline had no way of knowing.
     * <p>
     * <b>Not annotated {@code @Transactional}, deliberately.</b> This method is invoked from
     * {@link #dispatchPending} on {@code this}, so a transactional annotation here would be
     * bypassed entirely - Spring's proxy only intercepts calls arriving from outside the bean - and
     * would read as a guarantee that does not exist. None is needed: {@code save} commits on its
     * own, and per-entry isolation is exactly what is wanted, so that one unreachable recipient
     * cannot roll back the successful sends beside it.
     */
    void dispatchOne(OrderMailOutboxEntry entry) {
        entry.setAttempts(entry.getAttempts() + 1);
        try {
            OrderPojo order = this.resolveOrder(entry);
            // The status the order was in AT THE TRANSITION, not whatever it holds now.
            order.setStatus(entry.getOrderStatusName());

            if (MailRecipientKind.OWNERS.name().equals(entry.getRecipientKind())) {
                mailingService.notifyOrderStatusToOwners(order);
            } else {
                mailingService.notifyOrderStatusToClient(order);
            }

            entry.setDispatchedAt(Instant.now());
            entry.setLastError(null);
        } catch (Exception exc) {
            // Checked MailingServiceException, EntityNotFoundException from a deleted order, and
            // anything the adapter throws - all treated alike: record and retry within the bound.
            entry.setLastError(abbreviate(exc.getClass().getSimpleName() + ": " + exc.getMessage(), 500));
            logger.warn("Could not dispatch outbox entry {} for order {} (attempt {} of {}): {}",
                entry.getId(), entry.getOrderId(), entry.getAttempts(), maxAttempts, exc.getMessage());
        }
        outboxRepository.save(entry);
    }

    /**
     * Re-reads the order the notification is about.
     * <p>
     * Through {@code OrdersCrudService} rather than a repository, mirroring how the checkout path
     * resolves orders - the mailing adapter needs a fully converted {@code OrderPojo}, including
     * details, which is what the crud service produces.
     */
    private OrderPojo resolveOrder(OrderMailOutboxEntry entry) {
        Map<String, String> byId = new HashMap<>(Map.of("id", String.valueOf(entry.getOrderId())));
        Predicate orderWithMatchingId = ordersPredicateService.parseMap(byId);
        return ordersCrudService.readOne(orderWithMatchingId);
    }
}
