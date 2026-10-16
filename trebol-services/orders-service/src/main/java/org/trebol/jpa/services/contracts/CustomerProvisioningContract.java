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

package org.trebol.jpa.services.contracts;

import org.trebol.api.models.PersonPojo;
import org.trebol.common.exceptions.BadInputException;

/**
 * Resolves - or creates - a customer in the Identity service.
 * <p>
 * <b>Phase 3 cut.</b> In the monolith this returned the {@code Customer} <b>entity</b>, and
 * {@code Order} held a {@code @ManyToOne} to it cascading {@code PERSIST}. That is how one checkout
 * call created a {@code customers} row and a {@code people} row as a side effect of saving an
 * order. Both tables belong to the Identity service now, so the entity is gone and an identifier
 * takes its place.
 * <p>
 * <b>{@link #resolveOrCreateCustomerIdNumber} is a read <i>or a write</i>, and it is now a REMOTE
 * write inside the checkout transaction.</b> That is the open consequence {@code ADR-001} recorded
 * when this grouping was approved, and extraction is what makes it real rather than theoretical:
 * <ul>
 *   <li>the local transaction can roll back; <b>the remote customer creation cannot</b>, so a
 *       failed checkout can leave an orphaned customer in Identity;</li>
 *   <li>the call is synchronous and on the critical path, so Identity being unavailable makes
 *       checkout unavailable.</li>
 * </ul>
 * Neither is solved here. Solving the first needs a compensating action or a saga, and solving the
 * second needs the customer to be provisioned before checkout begins - both are design changes
 * beyond a conversion (G1). <b>The orphaned-customer window is recorded in {@code PHASE-3-EXIT.md}
 * as a known limitation of the approved target.</b>
 * <p>
 * It is idempotent on the Identity side - a customer is matched by id number before being created -
 * so a retry does not duplicate.
 */
public interface CustomerProvisioningContract {
    /**
     * Finds the customer matching this person, creating one if none exists.
     *
     * @param customer the customer details submitted with the order
     * @return the customer's id number, for {@code Order.customerIdNumber} - the business key,
     *         because Identity's {@code PersonPojo} exposes no surrogate id
     * @throws BadInputException if the submitted details are not sufficient to identify or create a
     *                           customer - the message is raised by Identity and relayed verbatim
     */
    String resolveOrCreateCustomerIdNumber(PersonPojo customer) throws BadInputException;

    /**
     * Reads back a customer's person details, for rendering an order.
     *
     * @param customerIdNumber the id number stored on the order
     * @return the person details, or {@code null} if the identifier does not resolve
     */
    PersonPojo describeCustomer(String customerIdNumber);
}
