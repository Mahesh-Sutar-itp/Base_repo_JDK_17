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

import com.querydsl.core.types.OrderSpecifier;
import lombok.NoArgsConstructor;
import org.trebol.jpa.entities.QOrder;

import java.util.Map;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public class OrdersSortSpec {
    /*
     * Phase 3 cut C3. This map had NINE keys. The ninth, "customer", resolved through
     *
     *     private static final QPerson CUSTOMER_PATH = BASE_PATH.customer.person;
     *     "customer", CUSTOMER_PATH.lastName.asc()
     *
     * which is a QueryDSL path from `orders` into the Identity service's `people` table, by way of
     * `customers`. It generated a two-level join across what is now a service boundary, so it cannot
     * survive the extraction.
     *
     * The key is DROPPED rather than reimplemented. Three alternatives were considered and rejected:
     * denormalising the surname onto `orders` needs a column, a write path to keep it in step and a
     * migration; fetching identifiers from Identity and sorting in memory breaks pagination, because
     * sorting must happen before the page is cut; and a database view reintroduces the shared schema
     * that Phase 4 exists to retire.
     *
     * This is the ONE client-visible capability the conversion removes. It is recorded in
     * PHASE-3-EXIT.md, and OrdersSortSpecTest asserts the remaining eight keys so a reintroduction
     * fails the build rather than silently reaching across the boundary again.
     */
    private static final QOrder BASE_PATH = QOrder.order;
    public static final Map<String, OrderSpecifier<?>> ORDER_SPEC_MAP = Map.of(
        "buyOrder", BASE_PATH.id.asc(),
        "date", BASE_PATH.date.asc(),
        "status", BASE_PATH.status.code.asc(),
        "shipper", BASE_PATH.shipper.name.asc(),
        "totalValue", BASE_PATH.totalValue.asc(),
        "netValue", BASE_PATH.netValue.asc(),
        "totalItems", BASE_PATH.totalItems.asc(),
        "transportValue", BASE_PATH.transportValue.asc()
    );
}
