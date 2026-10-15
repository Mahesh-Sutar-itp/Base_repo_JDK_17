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

import org.trebol.api.models.OrderPojo;

/**
 * Records that an order-status notification should be sent.
 * <p>
 * Register item <b>D11</b>. Callers must invoke this <b>from inside the transaction that performs
 * the status transition</b> - that is the whole point. Writing the entry in the same transaction
 * means either the order moved and the notification is recorded, or neither happened; a
 * notification can no longer be lost because the mail server was unreachable at the moment the
 * transition committed.
 * <p>
 * Nothing here sends anything. {@link MailOutboxDispatcher} drains the outbox separately.
 */
public interface MailOutboxService {
    /**
     * Records a notification to be sent for an order's current status.
     * <p>
     * Must be called within the transition's transaction. Does nothing when no
     * {@code MailingService} is active, which preserves the baseline behaviour of an application
     * running with no mailing profile: no mail, no entry, no error.
     *
     * @param order     the order as it stands after the transition - its status is stored, not
     *                  re-read later, so the notification describes what actually happened
     * @param recipient which of the two notifications to send
     */
    void enqueue(OrderPojo order, MailRecipientKind recipient);
}
