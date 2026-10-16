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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.trebol.api.models.BillingCompanyPojo;
import org.trebol.common.exceptions.BadInputException;
import org.trebol.jpa.entities.BillingCompany;
import org.trebol.jpa.entities.BillingType;
import org.trebol.jpa.entities.PaymentType;
import org.trebol.jpa.repositories.BillingTypesRepository;
import org.trebol.jpa.repositories.PaymentTypesRepository;
import org.trebol.jpa.services.conversion.BillingCompaniesConverterService;
import org.trebol.jpa.services.crud.BillingCompaniesCrudService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Gate A: the <code>billing</code> domain's side of the published contract.
 * <p>
 * The resolve-or-create behaviour tested here used to live inline in
 * {@code OrdersConverterServiceImpl} and was covered by its test; moving the behaviour moves
 * the coverage with it, rather than dropping it.
 */
@ExtendWith(MockitoExtension.class)
class BillingContractImplTest {
    static final String ANY = "any";
    @InjectMocks BillingContractImpl instance;
    @Mock BillingTypesRepository billingTypesRepositoryMock;
    @Mock PaymentTypesRepository paymentTypesRepositoryMock;
    @Mock BillingCompaniesCrudService billingCompaniesCrudServiceMock;
    @Mock BillingCompaniesConverterService billingCompaniesConverterServiceMock;

    @Test
    void finds_billing_type_by_name() {
        BillingType expected = BillingType.builder().id(1L).name(ANY).build();
        when(billingTypesRepositoryMock.findByName(anyString())).thenReturn(Optional.of(expected));
        assertSame(expected, instance.findBillingTypeByName(ANY).orElseThrow());
        verify(billingTypesRepositoryMock).findByName(ANY);
    }

    @Test
    void returns_empty_for_an_unregistered_billing_type() {
        when(billingTypesRepositoryMock.findByName(anyString())).thenReturn(Optional.empty());
        assertTrue(instance.findBillingTypeByName(ANY).isEmpty());
    }

    @Test
    void finds_payment_type_by_name() {
        PaymentType expected = PaymentType.builder().id(1L).name(ANY).build();
        when(paymentTypesRepositoryMock.findByName(anyString())).thenReturn(Optional.of(expected));
        assertSame(expected, instance.findPaymentTypeByName(ANY).orElseThrow());
        verify(paymentTypesRepositoryMock).findByName(ANY);
    }

    @Test
    void returns_empty_for_an_unregistered_payment_type() {
        when(paymentTypesRepositoryMock.findByName(anyString())).thenReturn(Optional.empty());
        assertTrue(instance.findPaymentTypeByName(ANY).isEmpty());
    }

    @Test
    void resolves_an_already_registered_billing_company() throws BadInputException {
        BillingCompanyPojo input = BillingCompanyPojo.builder().idNumber(ANY).build();
        BillingCompany existing = BillingCompany.builder().id(1L).idNumber(ANY).build();
        when(billingCompaniesCrudServiceMock.getExisting(any(BillingCompanyPojo.class)))
            .thenReturn(Optional.of(existing));
        assertSame(existing, instance.resolveOrCreateBillingCompany(input));
        verify(billingCompaniesCrudServiceMock).getExisting(input);
        verifyNoInteractions(billingCompaniesConverterServiceMock);
    }

    @Test
    void creates_a_billing_company_when_none_is_registered() throws BadInputException {
        BillingCompanyPojo input = BillingCompanyPojo.builder().idNumber(ANY).build();
        BillingCompany created = BillingCompany.builder().idNumber(ANY).build();
        when(billingCompaniesCrudServiceMock.getExisting(any(BillingCompanyPojo.class)))
            .thenReturn(Optional.empty());
        when(billingCompaniesConverterServiceMock.convertToNewEntity(any(BillingCompanyPojo.class)))
            .thenReturn(created);
        assertSame(created, instance.resolveOrCreateBillingCompany(input));
        verify(billingCompaniesConverterServiceMock).convertToNewEntity(input);
    }

    @Test
    void propagates_bad_input_when_resolving_a_billing_company() throws BadInputException {
        when(billingCompaniesCrudServiceMock.getExisting(any()))
            .thenThrow(new BadInputException(ANY));
        BadInputException result = assertThrows(BadInputException.class,
            () -> instance.resolveOrCreateBillingCompany(null));
        assertEquals(ANY, result.getMessage());
    }
}
