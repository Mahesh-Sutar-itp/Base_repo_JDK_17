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

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.trebol.api.models.OrderPojo;
import org.trebol.jpa.entities.OrderMailOutboxEntry;
import org.trebol.jpa.repositories.OrderMailOutboxRepository;
import org.trebol.jpa.services.crud.OrdersCrudService;
import org.trebol.jpa.services.predicates.OrdersPredicateService;
import org.trebol.mailing.MailingService;
import org.trebol.mailing.MailingServiceException;
import org.trebol.mailing.outbox.impl.MailOutboxServiceImpl;

import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Register item <b>D11</b>: the mail outbox, both halves.
 * <p>
 * The defect was that mail was sent <b>after</b> the status transition had committed, synchronously,
 * from a non-transactional controller method. A {@code MailingServiceException} at that point
 * surfaced as an error to the caller <b>with the status already changed</b>, and the notification
 * was lost with nothing recording that it had been owed.
 * <p>
 * These tests assert the four properties that constitute the fix:
 * <ol>
 *   <li>an entry is recorded, carrying the status <b>as at the transition</b>;</li>
 *   <li>a mail failure neither propagates nor loses the entry - it is retried within a bound and
 *       the error is recorded;</li>
 *   <li>an entry that exhausts its retries is abandoned but <b>remains as a record</b>;</li>
 *   <li>with no mailing service active, nothing is recorded and nothing fails - the baseline
 *       behaviour of an application running without a mailing profile.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class MailOutboxTest {
    @Mock OrderMailOutboxRepository outboxRepositoryMock;
    @Mock OrdersCrudService ordersCrudServiceMock;
    @Mock OrdersPredicateService ordersPredicateServiceMock;
    @Mock MailingService mailingServiceMock;
    static final long SOME_ORDER_ID = 1234L;
    static final String SOME_STATUS = "Paid, Unconfirmed";
    static final Predicate MATCHER_PREDICATE = new BooleanBuilder();

    private OrderPojo someOrder() {
        return OrderPojo.builder()
            .buyOrder(SOME_ORDER_ID)
            .status(SOME_STATUS)
            .build();
    }

    @Nested
    class Enqueueing {
        MailOutboxServiceImpl instance;

        @BeforeEach
        void beforeEach() {
            instance = new MailOutboxServiceImpl(outboxRepositoryMock, mailingServiceMock);
        }

        @Test
        void records_an_entry_carrying_the_status_as_at_the_transition() {
            instance.enqueue(someOrder(), MailRecipientKind.CUSTOMER);

            verify(outboxRepositoryMock).save(org.mockito.ArgumentMatchers.argThat(entry ->
                entry.getOrderId().equals(SOME_ORDER_ID)
                    && MailRecipientKind.CUSTOMER.name().equals(entry.getRecipientKind())
                    // The status is STORED, not re-read at dispatch time: the mail must describe
                    // what happened, not whatever the order's status has become since.
                    && SOME_STATUS.equals(entry.getOrderStatusName())
                    && entry.getAttempts()==0
                    && entry.getDispatchedAt()==null
                    && entry.getCreatedAt()!=null));
        }

        @Test
        void records_the_owners_notification_distinctly() {
            instance.enqueue(someOrder(), MailRecipientKind.OWNERS);

            verify(outboxRepositoryMock).save(org.mockito.ArgumentMatchers.argThat(entry ->
                MailRecipientKind.OWNERS.name().equals(entry.getRecipientKind())));
        }

        /**
         * A notification is not worth failing a committed transition for, so a malformed order is
         * logged and skipped rather than raised.
         */
        @Test
        void does_not_record_an_entry_for_an_order_with_no_identifier() {
            instance.enqueue(OrderPojo.builder().status(SOME_STATUS).build(), MailRecipientKind.CUSTOMER);

            verifyNoInteractions(outboxRepositoryMock);
        }

        /**
         * Preserves the baseline behaviour of an application with no mailing profile active: no
         * mail, no error - and now also no entries that nothing would ever drain.
         */
        @Test
        void records_nothing_when_no_mailing_service_is_active() {
            MailOutboxServiceImpl withoutMailing =
                new MailOutboxServiceImpl(outboxRepositoryMock, null);

            withoutMailing.enqueue(someOrder(), MailRecipientKind.CUSTOMER);

            verifyNoInteractions(outboxRepositoryMock);
        }
    }

    @Nested
    class Dispatching {
        MailOutboxDispatcher instance;

        @BeforeEach
        void beforeEach() {
            instance = new MailOutboxDispatcher(outboxRepositoryMock, ordersCrudServiceMock,
                ordersPredicateServiceMock, mailingServiceMock, 50, 5);
            lenient().when(ordersPredicateServiceMock.parseMap(anyMap())).thenReturn(MATCHER_PREDICATE);
            lenient().when(ordersCrudServiceMock.readOne(any())).thenReturn(someOrder());
        }

        private OrderMailOutboxEntry pendingEntry(MailRecipientKind kind, int attempts) {
            return OrderMailOutboxEntry.builder()
                .id(1L)
                .orderId(SOME_ORDER_ID)
                .recipientKind(kind.name())
                .orderStatusName(SOME_STATUS)
                .attempts(attempts)
                .createdAt(Instant.now())
                .build();
        }

        @Test
        void sends_a_customer_notification_and_marks_the_entry_dispatched() throws Exception {
            OrderMailOutboxEntry entry = pendingEntry(MailRecipientKind.CUSTOMER, 0);
            when(outboxRepositoryMock.findPending(anyInt(), any())).thenReturn(List.of(entry));

            instance.dispatchPending();

            verify(mailingServiceMock).notifyOrderStatusToClient(any(OrderPojo.class));
            verify(mailingServiceMock, never()).notifyOrderStatusToOwners(any());
            assertNotNull(entry.getDispatchedAt(), "a sent entry must be marked dispatched");
            assertNull(entry.getLastError());
            assertEquals(1, entry.getAttempts());
            verify(outboxRepositoryMock).save(entry);
        }

        @Test
        void sends_an_owners_notification_through_the_other_method() throws Exception {
            OrderMailOutboxEntry entry = pendingEntry(MailRecipientKind.OWNERS, 0);
            when(outboxRepositoryMock.findPending(anyInt(), any())).thenReturn(List.of(entry));

            instance.dispatchPending();

            verify(mailingServiceMock).notifyOrderStatusToOwners(any(OrderPojo.class));
            verify(mailingServiceMock, never()).notifyOrderStatusToClient(any());
        }

        /**
         * The dispatched mail must describe the status the order held <b>at the transition</b>, not
         * whatever it holds now. Here the order has since moved on, and the entry's stored status is
         * what must be sent.
         */
        @Test
        void sends_the_status_recorded_on_the_entry_not_the_order_s_current_status() throws Exception {
            OrderMailOutboxEntry entry = pendingEntry(MailRecipientKind.CUSTOMER, 0);
            when(outboxRepositoryMock.findPending(anyInt(), any())).thenReturn(List.of(entry));
            when(ordersCrudServiceMock.readOne(any()))
                .thenReturn(OrderPojo.builder().buyOrder(SOME_ORDER_ID).status("Delivery Complete").build());

            instance.dispatchPending();

            verify(mailingServiceMock).notifyOrderStatusToClient(
                org.mockito.ArgumentMatchers.argThat(order -> SOME_STATUS.equals(order.getStatus())));
        }

        /**
         * <b>The core of the D11 fix.</b> A mail failure must not propagate - the transition it
         * relates to has already committed - and must not lose the notification.
         */
        @Test
        void a_mail_failure_neither_propagates_nor_loses_the_entry() throws Exception {
            OrderMailOutboxEntry entry = pendingEntry(MailRecipientKind.CUSTOMER, 0);
            when(outboxRepositoryMock.findPending(anyInt(), any())).thenReturn(List.of(entry));
            doThrow(new MailingServiceException("the mail server is unreachable"))
                .when(mailingServiceMock).notifyOrderStatusToClient(any());

            // Does not throw.
            instance.dispatchPending();

            assertNull(entry.getDispatchedAt(), "a failed entry must remain undispatched");
            assertEquals(1, entry.getAttempts(), "the attempt must be counted so the retry is bounded");
            assertNotNull(entry.getLastError());
            assertTrue(entry.getLastError().contains("unreachable"),
                "the failure must be recorded for diagnosis");
            verify(outboxRepositoryMock).save(entry);
        }

        /**
         * A deleted or unresolvable order is treated the same way as a mail failure: recorded and
         * retried within the bound, never thrown.
         */
        @Test
        void an_unresolvable_order_is_recorded_rather_than_thrown() {
            OrderMailOutboxEntry entry = pendingEntry(MailRecipientKind.CUSTOMER, 0);
            when(outboxRepositoryMock.findPending(anyInt(), any())).thenReturn(List.of(entry));
            when(ordersCrudServiceMock.readOne(any()))
                .thenThrow(new EntityNotFoundException("Requested item(s) not found"));

            instance.dispatchPending();

            assertNull(entry.getDispatchedAt());
            assertNotNull(entry.getLastError());
            verify(outboxRepositoryMock).save(entry);
        }

        /**
         * The retry bound is enforced by the query, so an exhausted entry is never selected again -
         * but it is <b>kept</b>, because the row is the only record that a notification was owed and
         * never sent. That is precisely what the baseline had no way of knowing.
         */
        @Test
        void the_retry_bound_is_applied_by_the_query_and_exhausted_entries_are_kept() {
            when(outboxRepositoryMock.findPending(anyInt(), any())).thenReturn(List.of());

            instance.dispatchPending();

            verify(outboxRepositoryMock).findPending(org.mockito.ArgumentMatchers.eq(5), any());
            verifyNoInteractions(mailingServiceMock);
        }

        /**
         * One unreachable recipient must not prevent the entries beside it from being sent, which is
         * why each entry is attempted and saved independently.
         */
        @Test
        void one_failing_entry_does_not_stop_the_rest_of_the_batch() throws Exception {
            OrderMailOutboxEntry failing = pendingEntry(MailRecipientKind.CUSTOMER, 0);
            failing.setId(1L);
            OrderMailOutboxEntry succeeding = pendingEntry(MailRecipientKind.OWNERS, 0);
            succeeding.setId(2L);
            when(outboxRepositoryMock.findPending(anyInt(), any()))
                .thenReturn(List.of(failing, succeeding));
            doThrow(new MailingServiceException("boom"))
                .when(mailingServiceMock).notifyOrderStatusToClient(any());

            instance.dispatchPending();

            assertNull(failing.getDispatchedAt());
            assertNotNull(succeeding.getDispatchedAt(), "the second entry must still be sent");
        }

        @Test
        void does_nothing_at_all_when_no_mailing_service_is_active() {
            MailOutboxDispatcher withoutMailing = new MailOutboxDispatcher(outboxRepositoryMock,
                ordersCrudServiceMock, ordersPredicateServiceMock, null, 50, 5);

            withoutMailing.dispatchPending();

            verifyNoInteractions(outboxRepositoryMock);
        }
    }
}
