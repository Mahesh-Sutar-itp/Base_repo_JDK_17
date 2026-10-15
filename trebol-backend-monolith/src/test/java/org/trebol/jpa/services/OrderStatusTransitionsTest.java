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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.trebol.config.Constants.ORDER_STATUS_COMPLETED;
import static org.trebol.config.Constants.ORDER_STATUS_PAID_CONFIRMED;
import static org.trebol.config.Constants.ORDER_STATUS_PAID_UNCONFIRMED;
import static org.trebol.config.Constants.ORDER_STATUS_PAYMENT_CANCELLED;
import static org.trebol.config.Constants.ORDER_STATUS_PAYMENT_FAILED;
import static org.trebol.config.Constants.ORDER_STATUS_PAYMENT_STARTED;
import static org.trebol.config.Constants.ORDER_STATUS_PENDING;
import static org.trebol.config.Constants.ORDER_STATUS_REJECTED;
import static org.trebol.jpa.services.OrderStatusTransitions.UNREACHABLE_STATUSES;

/**
 * Exhaustive coverage of the order status machine - register items <b>D5</b> and <b>D10</b>.
 * <p>
 * "Exhaustive" is meant literally: the test below enumerates the full cross-product of the twelve
 * seeded statuses and asserts every pair, so a transition cannot be added, removed or mistyped in
 * the table without a test failing. That matters because before this table the same rules lived as
 * seven separate {@code if} statements and could not be tested as a machine at all.
 */
class OrderStatusTransitionsTest {
    /** All twelve seeded statuses - the eight reachable plus D10's four unreachable ones. */
    static final Set<String> ALL_SEEDED_STATUSES = Set.of(
        ORDER_STATUS_PENDING,
        ORDER_STATUS_PAYMENT_STARTED,
        ORDER_STATUS_PAYMENT_CANCELLED,
        ORDER_STATUS_PAYMENT_FAILED,
        ORDER_STATUS_PAID_UNCONFIRMED,
        ORDER_STATUS_PAID_CONFIRMED,
        ORDER_STATUS_REJECTED,
        ORDER_STATUS_COMPLETED,
        OrderStatusTransitions.ORDER_STATUS_RETURNED,
        OrderStatusTransitions.ORDER_STATUS_DELIVERY_FAILED,
        OrderStatusTransitions.ORDER_STATUS_DELIVERY_CANCELLED,
        OrderStatusTransitions.ORDER_STATUS_DELIVERY_ON_ROUTE);

    /** The seven transitions the {@code markAs*} methods implement, and the only legal ones. */
    static final Set<String> LEGAL_PAIRS = Set.of(
        ORDER_STATUS_PENDING + " -> " + ORDER_STATUS_PAYMENT_STARTED,
        ORDER_STATUS_PAYMENT_STARTED + " -> " + ORDER_STATUS_PAYMENT_CANCELLED,
        ORDER_STATUS_PAYMENT_STARTED + " -> " + ORDER_STATUS_PAYMENT_FAILED,
        ORDER_STATUS_PAYMENT_STARTED + " -> " + ORDER_STATUS_PAID_UNCONFIRMED,
        ORDER_STATUS_PAID_UNCONFIRMED + " -> " + ORDER_STATUS_PAID_CONFIRMED,
        ORDER_STATUS_PAID_UNCONFIRMED + " -> " + ORDER_STATUS_REJECTED,
        ORDER_STATUS_PAID_CONFIRMED + " -> " + ORDER_STATUS_COMPLETED);

    @Test
    void there_are_twelve_seeded_statuses_not_eleven() {
        assertEquals(12, ALL_SEEDED_STATUSES.size(),
            "data.sql seeds twelve order statuses; the definition's 'eleven' is corrected in the "
                + "defect register");
        assertEquals(8, OrderStatusTransitions.REACHABLE_STATUSES.size());
        assertEquals(4, UNREACHABLE_STATUSES.size());
    }

    /**
     * The whole machine, as one assertion over all 144 ordered pairs. Every pair in
     * {@link #LEGAL_PAIRS} must be accepted and every other pair rejected.
     */
    @Test
    void every_pair_of_statuses_is_either_a_named_legal_transition_or_rejected() {
        Set<String> acceptedPairs = new HashSet<>();
        for (String source : ALL_SEEDED_STATUSES) {
            for (String target : ALL_SEEDED_STATUSES) {
                if (OrderStatusTransitions.isLegal(source, target)) {
                    acceptedPairs.add(source + " -> " + target);
                }
            }
        }
        assertEquals(LEGAL_PAIRS, acceptedPairs,
            "the set of accepted transitions must be exactly the seven the markAs* methods implement");
    }

    @Nested
    class LegalTransitions {
        @Test
        void pending_may_only_start_payment() {
            assertEquals(Set.of(ORDER_STATUS_PAYMENT_STARTED),
                OrderStatusTransitions.legalTargetsFrom(ORDER_STATUS_PENDING));
        }

        @Test
        void payment_started_has_three_outcomes() {
            assertEquals(
                Set.of(ORDER_STATUS_PAYMENT_CANCELLED, ORDER_STATUS_PAYMENT_FAILED, ORDER_STATUS_PAID_UNCONFIRMED),
                OrderStatusTransitions.legalTargetsFrom(ORDER_STATUS_PAYMENT_STARTED));
        }

        @Test
        void paid_unconfirmed_may_be_confirmed_or_rejected() {
            assertEquals(Set.of(ORDER_STATUS_PAID_CONFIRMED, ORDER_STATUS_REJECTED),
                OrderStatusTransitions.legalTargetsFrom(ORDER_STATUS_PAID_UNCONFIRMED));
        }

        @Test
        void paid_confirmed_may_only_complete() {
            assertEquals(Set.of(ORDER_STATUS_COMPLETED),
                OrderStatusTransitions.legalTargetsFrom(ORDER_STATUS_PAID_CONFIRMED));
        }
    }

    @Nested
    class TerminalStates {
        @Test
        void the_four_terminal_states_have_no_way_out() {
            for (String terminal : Set.of(ORDER_STATUS_PAYMENT_CANCELLED, ORDER_STATUS_PAYMENT_FAILED,
                ORDER_STATUS_REJECTED, ORDER_STATUS_COMPLETED)) {
                assertTrue(OrderStatusTransitions.isTerminal(terminal), terminal + " must be terminal");
                assertTrue(OrderStatusTransitions.legalTargetsFrom(terminal).isEmpty());
            }
        }

        @Test
        void the_in_flight_states_are_not_terminal() {
            for (String inFlight : Set.of(ORDER_STATUS_PENDING, ORDER_STATUS_PAYMENT_STARTED,
                ORDER_STATUS_PAID_UNCONFIRMED, ORDER_STATUS_PAID_CONFIRMED)) {
                assertFalse(OrderStatusTransitions.isTerminal(inFlight));
            }
        }
    }

    /**
     * Register item <b>D10</b>: four statuses are seeded but reached by no code path. The seed rows
     * are preserved exactly, as the ruling requires - the data stays, the transitions do not.
     */
    @Nested
    class UnreachableStatuses {
        @Test
        void the_four_d10_statuses_are_rejected_as_targets_from_every_source() {
            for (String unreachable : UNREACHABLE_STATUSES) {
                for (String source : ALL_SEEDED_STATUSES) {
                    assertFalse(OrderStatusTransitions.isLegal(source, unreachable),
                        source + " must not be able to reach " + unreachable + " (register item D10)");
                }
            }
        }

        @Test
        void the_four_d10_statuses_have_no_outgoing_transitions_either() {
            for (String unreachable : UNREACHABLE_STATUSES) {
                assertTrue(OrderStatusTransitions.legalTargetsFrom(unreachable).isEmpty());
                // Not "terminal" either - a terminal status is one an order can legitimately reach
                // and then stay in. These cannot be reached at all, which is a different thing.
                assertFalse(OrderStatusTransitions.isTerminal(unreachable));
            }
        }

        @Test
        void they_are_named_exactly_as_the_seed_data_names_them() {
            assertEquals(Set.of("Returned", "Delivery Failed", "Delivery Cancelled", "Delivery On Route"),
                UNREACHABLE_STATUSES);
        }
    }

    @Nested
    class DefensiveCases {
        @Test
        void a_null_source_or_target_is_never_legal() {
            assertFalse(OrderStatusTransitions.isLegal(null, ORDER_STATUS_PAYMENT_STARTED));
            assertFalse(OrderStatusTransitions.isLegal(ORDER_STATUS_PENDING, null));
            assertFalse(OrderStatusTransitions.isLegal(null, null));
        }

        @Test
        void an_unknown_status_name_is_never_legal_in_either_position() {
            assertFalse(OrderStatusTransitions.isLegal("Not A Status", ORDER_STATUS_PAYMENT_STARTED));
            assertFalse(OrderStatusTransitions.isLegal(ORDER_STATUS_PENDING, "Not A Status"));
        }

        @Test
        void no_status_may_transition_to_itself() {
            for (String status : ALL_SEEDED_STATUSES) {
                assertFalse(OrderStatusTransitions.isLegal(status, status),
                    status + " must not be a legal transition to itself");
            }
        }

        /**
         * The two status names containing a comma are client-observable literals. Pinned because a
         * well-meaning rename would silently break every transition through them.
         */
        @Test
        void the_two_comma_bearing_status_names_are_preserved_verbatim() {
            assertEquals("Paid, Unconfirmed", ORDER_STATUS_PAID_UNCONFIRMED);
            assertEquals("Paid, Confirmed", ORDER_STATUS_PAID_CONFIRMED);
        }
    }
}
