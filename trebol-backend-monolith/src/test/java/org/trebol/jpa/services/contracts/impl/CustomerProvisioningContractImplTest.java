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
import org.trebol.api.models.PersonPojo;
import org.trebol.common.exceptions.BadInputException;
import org.trebol.jpa.entities.Customer;
import org.trebol.jpa.entities.Person;
import org.trebol.jpa.services.conversion.CustomersConverterService;
import org.trebol.jpa.services.crud.CustomersCrudService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 2 / HS-02: the <code>customer</code> domain's side of the published contract.
 * <p>
 * The resolve-or-create behaviour tested here used to live inline in
 * {@code OrdersConverterServiceImpl#convertCustomerInformationForEntity} and was covered by its
 * test; moving the behaviour moves the coverage with it, rather than dropping it.
 */
@ExtendWith(MockitoExtension.class)
class CustomerProvisioningContractImplTest {
    static final String ANY = "any";
    @InjectMocks CustomerProvisioningContractImpl instance;
    @Mock CustomersCrudService customersCrudServiceMock;
    @Mock CustomersConverterService customersConverterServiceMock;

    @Test
    void resolves_an_already_registered_customer() throws BadInputException {
        PersonPojo input = PersonPojo.builder().idNumber(ANY).build();
        Customer existing = Customer.builder().id(1L).person(Person.builder().idNumber(ANY).build()).build();
        when(customersCrudServiceMock.getExisting(any(PersonPojo.class))).thenReturn(Optional.of(existing));
        assertSame(existing, instance.resolveOrCreateCustomer(input));
        verify(customersCrudServiceMock).getExisting(input);
        verifyNoInteractions(customersConverterServiceMock);
    }

    @Test
    void creates_a_customer_when_none_is_registered() throws BadInputException {
        PersonPojo input = PersonPojo.builder().idNumber(ANY).build();
        Customer created = Customer.builder().person(Person.builder().idNumber(ANY).build()).build();
        when(customersCrudServiceMock.getExisting(any(PersonPojo.class))).thenReturn(Optional.empty());
        when(customersConverterServiceMock.convertToNewEntity(any(PersonPojo.class))).thenReturn(created);
        assertSame(created, instance.resolveOrCreateCustomer(input));
        verify(customersConverterServiceMock).convertToNewEntity(input);
    }

    @Test
    void propagates_bad_input_when_resolving_a_customer() throws BadInputException {
        when(customersCrudServiceMock.getExisting(any())).thenThrow(new BadInputException(ANY));
        BadInputException result = assertThrows(BadInputException.class,
            () -> instance.resolveOrCreateCustomer(null));
        assertEquals(ANY, result.getMessage());
        verifyNoInteractions(customersConverterServiceMock);
    }

    @Test
    void describes_a_customer_for_the_order_read_path() {
        Customer input = Customer.builder().id(1L).build();
        PersonPojo expected = PersonPojo.builder().idNumber(ANY).build();
        when(customersConverterServiceMock.convertToPojo(any(Customer.class))).thenReturn(expected);
        assertSame(expected, instance.describeCustomer(input));
        verify(customersConverterServiceMock).convertToPojo(input);
    }
}
