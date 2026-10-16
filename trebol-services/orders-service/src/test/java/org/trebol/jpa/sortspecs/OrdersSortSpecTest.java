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

package org.trebol.jpa.sortspecs;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins cut C3, which is the one client-visible capability this extraction removes.
 * <p>
 * In the monolith {@code OrdersSortSpec} declared nine keys. The ninth,
 * {@code "customer"}, mapped to {@code QOrder.order.customer.person.lastName.asc()} - a QueryDSL
 * join that walked from the {@code orders} table into the Identity service's {@code people}
 * table. Once Identity owns that table the join cannot be expressed, so the key was dropped,
 * leaving eight. The three rejected alternatives are recorded in {@code OrdersSortSpec} itself.
 * <p>
 * This test exists because nothing else fails if the key comes back. Re-adding it would compile
 * only if the entity association were also restored, but a future edit that does both would
 * silently reintroduce the cross-service join; and more likely, someone re-adding a key by
 * copying the monolith's file would get a {@code NullPointerException} at sort time rather than
 * a clear failure here. The exact key set is asserted rather than just the count, so that
 * swapping one key for another is also caught.
 * <p>
 * This is also the D21 mitigation surface: CVE-2024-49203 in QueryDSL 5.0.0 has no fixed
 * release, and the accepted mitigation is that {@code orderBy} only ever receives an
 * {@code OrderSpecifier} drawn from this closed map. An assertion on the exact key set is what
 * keeps that argument true.
 */
class OrdersSortSpecTest {
    @Test
    void declares_exactly_the_eight_locally_resolvable_sort_keys() {
        assertThat(OrdersSortSpec.ORDER_SPEC_MAP)
            .as("every sort key must resolve inside this service's own schema")
            .containsOnlyKeys(
                "buyOrder",
                "date",
                "status",
                "shipper",
                "totalValue",
                "netValue",
                "totalItems",
                "transportValue");
    }

    @Test
    void does_not_declare_the_customer_sort_key_that_cut_C3_removed() {
        assertThat(OrdersSortSpec.ORDER_SPEC_MAP)
            .as("sorting by customer requires joining orders to the Identity service's people "
                + "table, which this service cannot do - see cut C3 in OrdersSortSpec")
            .doesNotContainKey("customer");
    }

    @Test
    void every_declared_key_maps_to_a_non_null_order_specifier() {
        assertThat(OrdersSortSpec.ORDER_SPEC_MAP.values())
            .as("a null specifier would fail only at query time, on a live sort request")
            .doesNotContainNull();
    }
}
