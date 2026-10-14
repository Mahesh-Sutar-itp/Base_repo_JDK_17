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
import org.trebol.jpa.entities.Salesperson;
import org.trebol.jpa.services.contracts.SalespersonLookupContract;
import org.trebol.jpa.services.conversion.SalespeopleConverterService;

/**
 * The <code>salesperson</code> domain's side of {@link SalespersonLookupContract}.
 * <p>
 * Pure delegation to {@link SalespeopleConverterService} - behaviour-identical to the baseline
 * order read path (G1).
 */
@Service
@Transactional(readOnly = true)
public class SalespersonLookupContractImpl
    implements SalespersonLookupContract {
    private final SalespeopleConverterService salespeopleConverterService;

    @Autowired
    public SalespersonLookupContractImpl(
        SalespeopleConverterService salespeopleConverterService
    ) {
        this.salespeopleConverterService = salespeopleConverterService;
    }

    @Override
    public PersonPojo describeSalesperson(Salesperson salesperson) {
        return salespeopleConverterService.convertToPojo(salesperson);
    }
}
