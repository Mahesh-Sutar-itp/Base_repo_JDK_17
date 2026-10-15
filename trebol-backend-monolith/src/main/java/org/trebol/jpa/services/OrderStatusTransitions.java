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

package org.trebol.jpa.services;

import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Set;

import static lombok.AccessLevel.PRIVATE;
import static org.trebol.config.Constants.ORDER_STATUS_COMPLETED;
import static org.trebol.config.Constants.ORDER_STATUS_PAID_CONFIRMED;
import static org.trebol.config.Constants.ORDER_STATUS_PAID_UNCONFIRMED;
import static org.trebol.config.Constants.ORDER_STATUS_PAYMENT_CANCELLED;
import static org.trebol.config.Constants.ORDER_STATUS_PAYMENT_FAILED;
import static org.trebol.config.Constants.ORDER_STATUS_PAYMENT_STARTED;
import static org.trebol.config.Constants.ORDER_STATUS_PENDING;
import static org.trebol.config.Constants.ORDER_STATUS_REJECTED;

/**
 * The order status machine, as one explicit table.
 * <p>
 * Register items <b>D5</b> and <b>D10</b>. Before this class the rules existed only as seven
 * separate {@code if} statements inside {@code OrdersProcessServiceImpl}, each checking one
 * expected source status - which made the machine impossible to read in one place, impossible to
 * test as a whole, and impossible to enforce anywhere else.
 * <p>
 * <b>Twelve statuses are seeded, not eleven.</b> The transformation definition and the defect
 * register both say eleven; {@code data.sql} seeds twelve and restarts the sequence at 13. The
 * correction is recorded in {@code defect-register-rulings.md}.
 * <p>
 * <b>The legal transitions</b>, derived from the {@code markAs*} preconditions that were already in
 * the code - this table changes no rule, it only states them in one place:
 *
 * <pre>
 *   Pending            -> Payment Started
 *   Payment Started    -> Payment Cancelled | Payment Failed | Paid, Unconfirmed
 *   Paid, Unconfirmed  -> Paid, Confirmed   | Rejected
 *   Paid, Confirmed    -> Delivery Complete
 * </pre>
 *
 * Terminal: {@code Payment Cancelled}, {@code Payment Failed}, {@code Rejected},
 * {@code Delivery Complete}.
 * <p>
 * <b>Register item D10 - the four unreachable statuses.</b> {@code Returned} (-6),
 * {@code Delivery Failed} (-5), {@code Delivery Cancelled} (-4) and {@code Delivery On Route} (05)
 * are seeded but reached by no code path, and {@code Constants} does not even name them. This table
 * records them explicitly and <b>rejects every transition into them</b>. Per the D10 ruling the
 * <b>seed rows are preserved exactly</b> - the data stays, the transitions do not. They are named
 * here as string literals rather than constants precisely because no constant exists for them, and
 * inventing one would imply they are usable.
 */
@NoArgsConstructor(access = PRIVATE)
public final class OrderStatusTransitions {
    /** Seeded, reachable by no code path, and rejected as a transition target. Register item D10. */
    public static final String ORDER_STATUS_RETURNED = "Returned";
    /** Seeded, reachable by no code path, and rejected as a transition target. Register item D10. */
    public static final String ORDER_STATUS_DELIVERY_FAILED = "Delivery Failed";
    /** Seeded, reachable by no code path, and rejected as a transition target. Register item D10. */
    public static final String ORDER_STATUS_DELIVERY_CANCELLED = "Delivery Cancelled";
    /** Seeded, reachable by no code path, and rejected as a transition target. Register item D10. */
    public static final String ORDER_STATUS_DELIVERY_ON_ROUTE = "Delivery On Route";

    /**
     * The four statuses register item D10 records as seeded but unreachable.
     * <p>
     * Kept as a named set so the D10 ruling is verifiable in code, not only in a document.
     */
    public static final Set<String> UNREACHABLE_STATUSES = Set.of(
        ORDER_STATUS_RETURNED,
        ORDER_STATUS_DELIVERY_FAILED,
        ORDER_STATUS_DELIVERY_CANCELLED,
        ORDER_STATUS_DELIVERY_ON_ROUTE);

    /** Every status the application can actually hold, in lifecycle order. */
    public static final Set<String> REACHABLE_STATUSES = Set.of(
        ORDER_STATUS_PENDING,
        ORDER_STATUS_PAYMENT_STARTED,
        ORDER_STATUS_PAYMENT_CANCELLED,
        ORDER_STATUS_PAYMENT_FAILED,
        ORDER_STATUS_PAID_UNCONFIRMED,
        ORDER_STATUS_PAID_CONFIRMED,
        ORDER_STATUS_REJECTED,
        ORDER_STATUS_COMPLETED);

    /** source status -&gt; the set of statuses it may legally move to. */
    private static final Map<String, Set<String>> LEGAL_TRANSITIONS = Map.of(
        ORDER_STATUS_PENDING, Set.of(ORDER_STATUS_PAYMENT_STARTED),
        ORDER_STATUS_PAYMENT_STARTED, Set.of(
            ORDER_STATUS_PAYMENT_CANCELLED,
            ORDER_STATUS_PAYMENT_FAILED,
            ORDER_STATUS_PAID_UNCONFIRMED),
        ORDER_STATUS_PAID_UNCONFIRMED, Set.of(
            ORDER_STATUS_PAID_CONFIRMED,
            ORDER_STATUS_REJECTED),
        ORDER_STATUS_PAID_CONFIRMED, Set.of(ORDER_STATUS_COMPLETED),
        // Terminal states - no transition out of any of them.
        ORDER_STATUS_PAYMENT_CANCELLED, Set.of(),
        ORDER_STATUS_PAYMENT_FAILED, Set.of(),
        ORDER_STATUS_REJECTED, Set.of(),
        ORDER_STATUS_COMPLETED, Set.of());

    /**
     * @param status a status name
     * @return whether the status is terminal - reachable, but with no legal transition out
     */
    public static boolean isTerminal(String status) {
        Set<String> targets = LEGAL_TRANSITIONS.get(status);
        return targets!=null && targets.isEmpty();
    }

    /**
     * @param source the order's current status name
     * @param target the status name it would move to
     * @return whether that transition is legal
     */
    public static boolean isLegal(String source, String target) {
        if (source==null || target==null) {
            return false;
        }
        // Register item D10: never a legal target, from any source.
        if (UNREACHABLE_STATUSES.contains(target)) {
            return false;
        }
        return LEGAL_TRANSITIONS.getOrDefault(source, Set.of()).contains(target);
    }

    /**
     * @param source the order's current status name
     * @return the legal target statuses, or an empty set for a terminal or unknown source
     */
    public static Set<String> legalTargetsFrom(String source) {
        return LEGAL_TRANSITIONS.getOrDefault(source, Set.of());
    }
}
