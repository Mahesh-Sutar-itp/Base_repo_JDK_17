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
import org.trebol.jpa.entities.Customer;

/**
 * Published contract owned by the <code>customer</code> domain (Identity).
 * <p>
 * Phase 2 of the microservices conversion closes coupling hotspot <b>HS-02</b>. The checkout path
 * ({@code OrdersConverterServiceImpl}) previously reached {@code CustomersCrudService} and
 * {@code CustomersConverterService} directly - a <i>service</i>-level leak, which is why Gate A's
 * repository-import grep did not catch it.
 * <p>
 * <b>{@link #resolveOrCreateCustomer} is a read <i>or a write</i>, not a read.</b> It is the
 * identity kernel's outward write that HS-02 names: today it is an in-process call returning an
 * unsaved entity that is cascaded on order save. Across a service boundary it becomes a remote
 * write inside checkout. The behaviour here is identical to the baseline, deliberately (G1); the
 * consistency implication is recorded in {@code ADR-001}.
 */
public interface CustomerProvisioningContract {
    /**
     * Resolves the customer for an order, converting the submitted data into a new -
     * <b>not yet persisted</b> - entity when no record matches, exactly as the baseline
     * checkout did.
     *
     * @param customer the submitted customer data
     * @return the matching registered customer, or a new entity ready to be cascaded on save
     * @throws BadInputException if the submitted data is incomplete or invalid
     */
    Customer resolveOrCreateCustomer(PersonPojo customer) throws BadInputException;

    /**
     * Read side of the same boundary: renders a customer entity as the person data an order
     * exposes to clients.
     *
     * @param customer the customer held by an order
     * @return the person data for that customer
     */
    PersonPojo describeCustomer(Customer customer);
}
