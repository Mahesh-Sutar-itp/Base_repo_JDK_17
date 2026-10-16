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

import org.trebol.api.models.BillingCompanyPojo;
import org.trebol.common.exceptions.BadInputException;
import org.trebol.jpa.entities.BillingCompany;
import org.trebol.jpa.entities.BillingType;
import org.trebol.jpa.entities.PaymentType;

import java.util.Optional;

/**
 * Published contract owned by the <code>billing</code> domain.
 * <p>
 * Gate A of the microservices conversion: the checkout path previously reached
 * {@code BillingTypesRepository}, {@code BillingCompaniesCrudService} and
 * {@code BillingCompaniesConverterService} directly, and {@code OrdersPatchServiceImpl}
 * reached {@code PaymentTypesRepository}.
 * <p>
 * <b>{@link #resolveOrCreateBillingCompany} is a read <i>or a write</i>, not a read.</b> Today it
 * is an in-process call that returns an unsaved entity, cascaded on order save. Across a service
 * boundary it would become a remote write inside checkout. That is recorded as an open decision in
 * {@code ADR-001} - the behaviour here is identical to the baseline, deliberately.
 */
public interface BillingContract {
    /**
     * @param name the billing type name, as submitted on an order
     * @return the matching billing type, or empty if no such type is registered
     */
    Optional<BillingType> findBillingTypeByName(String name);

    /**
     * @param name the payment type name, as submitted on an order or an order patch
     * @return the matching payment type, or empty if no such type is registered
     */
    Optional<PaymentType> findPaymentTypeByName(String name);

    /**
     * Resolves the billing company for an order, creating a new - <b>not yet persisted</b> - entity
     * when no record matches, exactly as the baseline checkout did.
     *
     * @param company the submitted billing company data
     * @return the matching registered company, or a new entity ready to be cascaded on save
     * @throws BadInputException if the submitted data is incomplete or invalid
     */
    BillingCompany resolveOrCreateBillingCompany(BillingCompanyPojo company) throws BadInputException;
}
