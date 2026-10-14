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
import org.trebol.api.models.PersonPojo;
import org.trebol.common.exceptions.BadInputException;
import org.trebol.jpa.entities.Customer;
import org.trebol.jpa.services.contracts.CustomerProvisioningContract;
import org.trebol.jpa.services.conversion.CustomersConverterService;
import org.trebol.jpa.services.crud.CustomersCrudService;

import java.util.Optional;

/**
 * The <code>customer</code> domain's side of {@link CustomerProvisioningContract}.
 * <p>
 * Pure delegation to the services the checkout path used to call itself - no logic is rewritten,
 * so the resolve-or-create semantics, the {@link BadInputException} paths and their literal
 * messages are unchanged (G1, G2). A newly created customer is <b>not</b> persisted here; it is
 * cascaded when the order is saved, exactly as in the baseline.
 */
@Service
@Transactional
public class CustomerProvisioningContractImpl
    implements CustomerProvisioningContract {
    private final CustomersCrudService customersCrudService;
    private final CustomersConverterService customersConverterService;

    @Autowired
    public CustomerProvisioningContractImpl(
        CustomersCrudService customersCrudService,
        CustomersConverterService customersConverterService
    ) {
        this.customersCrudService = customersCrudService;
        this.customersConverterService = customersConverterService;
    }

    @Override
    public Customer resolveOrCreateCustomer(PersonPojo customer) throws BadInputException {
        Optional<Customer> existingCustomer = customersCrudService.getExisting(customer);
        if (existingCustomer.isEmpty()) {
            return customersConverterService.convertToNewEntity(customer);
        }
        return existingCustomer.get();
    }

    @Override
    @Transactional(readOnly = true)
    public PersonPojo describeCustomer(Customer customer) {
        return customersConverterService.convertToPojo(customer);
    }
}
