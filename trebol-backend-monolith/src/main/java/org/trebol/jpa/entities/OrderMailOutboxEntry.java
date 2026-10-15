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

package org.trebol.jpa.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.trebol.jpa.DBEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One pending or dispatched order-status notification.
 * <p>
 * Register item <b>D11</b>. Mail used to be sent <b>after</b> the status transition had committed,
 * synchronously, from a non-transactional controller method, at four call sites. A
 * {@code MailingServiceException} at that point surfaced to the caller as an error <b>while the
 * status change was already committed</b> - so the client saw a failure, the order had moved, and
 * the notification was lost with nothing recording that it should have been sent.
 * <p>
 * A row of this table is written <b>in the same transaction as the status transition</b>, which is
 * precisely what makes this an outbox rather than a queue: either the order moved and the
 * notification is recorded, or neither happened. A separate dispatcher drains it with bounded
 * retry, so a mail failure can no longer fail - or be lost by - a request whose transition
 * succeeded.
 * <p>
 * <b>No broker is involved.</b> ADR-003 accepted that no event infrastructure exists anywhere in
 * this codebase and that an outbox would have to be built from nothing; this is the minimum that
 * actually fixes the defect - one table, one scheduled drain, and the existing
 * {@code MailingService} contract untouched.
 * <p>
 * <b>The status name is stored, not re-read.</b> The mail must describe the status the order was in
 * when the transition happened, not whatever status it holds when the dispatcher runs - those can
 * differ, and the second is the wrong answer.
 */
@Entity
@Table(
    name = "order_mail_outbox",
    indexes = {
        @Index(columnList = "outbox_dispatched_at")
    })
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class OrderMailOutboxEntry
    implements DBEntity {
    private static final long serialVersionUID = 22L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbox_id", nullable = false)
    private Long id;
    /**
     * The order this notification is about. A plain identifier rather than an association: this
     * table is infrastructure, and a foreign key here would make the outbox part of the order
     * aggregate's delete semantics for no benefit.
     */
    @Column(name = "order_id", nullable = false)
    private Long orderId;
    /** {@code CUSTOMER} or {@code OWNERS} - which of the two mailing methods to call. */
    @Column(name = "outbox_recipient_kind", nullable = false, length = 20)
    private String recipientKind;
    /**
     * The order's status <b>at the moment of the transition</b>. Stored so the dispatched mail
     * describes what happened, not what has happened since.
     */
    @Column(name = "outbox_order_status_name", nullable = false, length = 50)
    private String orderStatusName;
    /** Incremented on every dispatch attempt, successful or not. Bounds the retry. */
    @Column(name = "outbox_attempts", nullable = false)
    private int attempts;
    @Column(name = "outbox_created_at", nullable = false)
    private Instant createdAt;
    /** Null until the mail has been sent successfully. The only marker of completion. */
    @Column(name = "outbox_dispatched_at")
    private Instant dispatchedAt;
    /** The last failure's message, truncated. Diagnostic only; never shown to a client. */
    @Column(name = "outbox_last_error", length = 500)
    private String lastError;
}
