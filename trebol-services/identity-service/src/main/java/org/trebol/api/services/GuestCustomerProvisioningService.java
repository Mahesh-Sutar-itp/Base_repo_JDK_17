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

package org.trebol.api.services;

import org.trebol.api.models.PersonPojo;
import org.trebol.common.exceptions.BadInputException;

/**
 * Provisions the {@code person} and {@code customer} records backing a guest session.
 * <p>
 * Register item D14: these writes used to be performed inside
 * {@code JwtGuestAuthenticationFilter}, <b>before</b> authentication, which meant a failed guest
 * login still created rows, and coupled the availability of login to business tables. The writes now
 * live behind this application service and run only after authentication has succeeded.
 */
public interface GuestCustomerProvisioningService {
    /**
     * Creates the guest's customer record if it does not exist yet. Idempotent: an already-registered
     * guest is left untouched.
     *
     * @param guestData the guest profile data submitted with the session request
     * @throws BadInputException if the submitted profile data is insufficient or invalid
     */
    void provisionCustomer(PersonPojo guestData) throws BadInputException;
}
