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

package org.trebol.jpa.repositories;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.trebol.jpa.Repository;
import org.trebol.jpa.entities.OrderMailOutboxEntry;

import java.util.List;

/**
 * Register item D11: access to the order-status notification outbox.
 */
@org.springframework.stereotype.Repository
public interface OrderMailOutboxRepository
    extends Repository<OrderMailOutboxEntry> {

    /**
     * The dispatcher's query: entries not yet sent, that have not exhausted their retry budget,
     * oldest first.
     * <p>
     * Paged rather than unbounded, so a long backlog cannot turn one scheduled run into an
     * arbitrarily long transaction.
     *
     * @param maxAttempts the retry bound - entries at or above this are abandoned, not retried
     * @param pageable    bounds the batch size
     * @return the entries to attempt, oldest first
     */
    @Query("SELECT e FROM OrderMailOutboxEntry e "
        + "WHERE e.dispatchedAt IS NULL "
        + "AND e.attempts < :maxAttempts "
        + "ORDER BY e.createdAt ASC")
    List<OrderMailOutboxEntry> findPending(@Param("maxAttempts") int maxAttempts, Pageable pageable);
}
