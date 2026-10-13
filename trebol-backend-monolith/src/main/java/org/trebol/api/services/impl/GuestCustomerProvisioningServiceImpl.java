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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.trebol.api.models.PersonPojo;
import org.trebol.api.services.GuestCustomerProvisioningService;
import org.trebol.common.exceptions.BadInputException;
import org.trebol.jpa.services.crud.CustomersCrudService;

import jakarta.persistence.EntityExistsException;

/**
 * Register item D14. Holds the guest provisioning write that used to sit inside
 * {@code JwtGuestAuthenticationFilter}. Behaviour is otherwise unchanged: an already-registered
 * guest is reported at info level with the same message, and insufficient data still raises
 * {@link BadInputException}, which the filter turns into bad credentials.
 */
@Service
public class GuestCustomerProvisioningServiceImpl
    implements GuestCustomerProvisioningService {
    private final Logger logger = LoggerFactory.getLogger(GuestCustomerProvisioningServiceImpl.class);
    private final CustomersCrudService customersService;

    @Autowired
    public GuestCustomerProvisioningServiceImpl(
        CustomersCrudService customersService
    ) {
        this.customersService = customersService;
    }

    @Transactional
    @Override
    public void provisionCustomer(PersonPojo guestData) throws BadInputException {
        try {
            customersService.create(guestData);
        } catch (EntityExistsException exc) {
            logger.info("Guest with idNumber={} is already registered in the database", guestData.getIdNumber());
        }
    }
}
