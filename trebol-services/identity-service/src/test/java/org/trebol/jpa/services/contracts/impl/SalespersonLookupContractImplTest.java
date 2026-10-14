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
import org.trebol.jpa.entities.Salesperson;
import org.trebol.jpa.services.conversion.SalespeopleConverterService;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 2 / HS-02: the <code>salesperson</code> domain's side of the published contract.
 * <p>
 * Read-only. The order domain never creates a salesperson.
 */
@ExtendWith(MockitoExtension.class)
class SalespersonLookupContractImplTest {
    static final String ANY = "any";
    @InjectMocks SalespersonLookupContractImpl instance;
    @Mock SalespeopleConverterService salespeopleConverterServiceMock;

    @Test
    void describes_a_salesperson_for_the_order_read_path() {
        Salesperson input = Salesperson.builder().id(1L).build();
        PersonPojo expected = PersonPojo.builder().idNumber(ANY).build();
        when(salespeopleConverterServiceMock.convertToPojo(any(Salesperson.class))).thenReturn(expected);
        assertSame(expected, instance.describeSalesperson(input));
        verify(salespeopleConverterServiceMock).convertToPojo(input);
    }
}
