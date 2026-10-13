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

package org.trebol.contracts.identity;

/**
 * A reference to a customer resolved across a service boundary.
 * <p>
 * Orders-service holds only this reference, not the customer record itself. The
 * {@code customers} table is owned exclusively by identity-service, so an order stores
 * {@link #customerId()} and resolves the rest on demand.
 * <p>
 * Note the asymmetry with the monolith: there, an order held a JPA association to the
 * customer entity and could navigate to its person transitively. Across a service boundary
 * that navigation no longer exists - which is why {@link #person()} is carried on this
 * reference rather than fetched separately. The order receipt and the notification
 * templates both need the person's name and e-mail, and neither should trigger a second
 * remote call.
 *
 * @param customerId the customer's surrogate key within identity-service. Opaque to
 *                   orders-service; used only to store the association.
 * @param person     the person behind the customer, as resolved or created. Carried inline
 *                   to avoid a second round trip on the receipt and notification paths.
 */
public record CustomerRef(
    Long customerId,
    PersonRef person
) {
}
