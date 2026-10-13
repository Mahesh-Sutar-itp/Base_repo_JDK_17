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

package org.trebol.api.services.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.trebol.api.models.PersonPojo;
import org.trebol.common.exceptions.BadInputException;
import org.trebol.jpa.services.crud.CustomersCrudService;

import jakarta.persistence.EntityExistsException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Register item D14: the guest provisioning write, extracted out of the authentication filter.
 * Behaviour is unchanged from the baseline - an already-registered guest is tolerated, and
 * insufficient data still raises {@link BadInputException}.
 */
@ExtendWith(MockitoExtension.class)
class GuestCustomerProvisioningServiceImplTest {
    @InjectMocks GuestCustomerProvisioningServiceImpl instance;
    @Mock CustomersCrudService customersServiceMock;

    @Test
    void creates_the_guest_customer() throws BadInputException {
        PersonPojo guestData = PersonPojo.builder().idNumber("1").build();

        instance.provisionCustomer(guestData);

        verify(customersServiceMock).create(guestData);
    }

    @Test
    void tolerates_an_already_registered_guest() throws BadInputException {
        PersonPojo guestData = PersonPojo.builder().idNumber("1").build();
        doThrow(new EntityExistsException()).when(customersServiceMock).create(any(PersonPojo.class));

        assertDoesNotThrow(() -> instance.provisionCustomer(guestData));
    }

    @Test
    void propagates_invalid_guest_data() throws BadInputException {
        PersonPojo guestData = PersonPojo.builder().build();
        doThrow(new BadInputException("invalid")).when(customersServiceMock).create(any(PersonPojo.class));

        BadInputException result = assertThrows(BadInputException.class,
            () -> instance.provisionCustomer(guestData));

        assertEquals("invalid", result.getMessage());
    }
}
