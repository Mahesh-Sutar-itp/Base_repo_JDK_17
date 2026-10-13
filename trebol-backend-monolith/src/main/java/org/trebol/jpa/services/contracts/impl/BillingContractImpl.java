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

package org.trebol.jpa.services.contracts.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.trebol.api.models.BillingCompanyPojo;
import org.trebol.common.exceptions.BadInputException;
import org.trebol.jpa.entities.BillingCompany;
import org.trebol.jpa.entities.BillingType;
import org.trebol.jpa.entities.PaymentType;
import org.trebol.jpa.repositories.BillingTypesRepository;
import org.trebol.jpa.repositories.PaymentTypesRepository;
import org.trebol.jpa.services.contracts.BillingContract;
import org.trebol.jpa.services.conversion.BillingCompaniesConverterService;
import org.trebol.jpa.services.crud.BillingCompaniesCrudService;

import java.util.Optional;

/**
 * The <code>billing</code> domain's side of {@link BillingContract}.
 * <p>
 * Owns the repository access for billing and payment types, and encapsulates the
 * resolve-or-create pair that the checkout path used to perform itself. The semantics are
 * identical to the baseline, deliberately: a newly created company is <b>not</b> persisted
 * here - it is cascaded when the order is saved.
 */
@Service
@Transactional
public class BillingContractImpl
    implements BillingContract {
    private final BillingTypesRepository billingTypesRepository;
    private final PaymentTypesRepository paymentTypesRepository;
    private final BillingCompaniesCrudService billingCompaniesCrudService;
    private final BillingCompaniesConverterService billingCompaniesConverterService;

    @Autowired
    public BillingContractImpl(
        BillingTypesRepository billingTypesRepository,
        PaymentTypesRepository paymentTypesRepository,
        BillingCompaniesCrudService billingCompaniesCrudService,
        BillingCompaniesConverterService billingCompaniesConverterService
    ) {
        this.billingTypesRepository = billingTypesRepository;
        this.paymentTypesRepository = paymentTypesRepository;
        this.billingCompaniesCrudService = billingCompaniesCrudService;
        this.billingCompaniesConverterService = billingCompaniesConverterService;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BillingType> findBillingTypeByName(String name) {
        return billingTypesRepository.findByName(name);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentType> findPaymentTypeByName(String name) {
        return paymentTypesRepository.findByName(name);
    }

    @Override
    public BillingCompany resolveOrCreateBillingCompany(BillingCompanyPojo company) throws BadInputException {
        Optional<BillingCompany> existingCompany = billingCompaniesCrudService.getExisting(company);
        if (existingCompany.isEmpty()) {
            return billingCompaniesConverterService.convertToNewEntity(company);
        }
        return existingCompany.get();
    }
}
