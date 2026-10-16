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

import org.trebol.api.models.PersonPojo;

/**
 * Reads a salesperson's details from the Identity service.
 * <p>
 * <b>Phase 3 cut.</b> In the monolith this took the {@code Salesperson} <b>entity</b> -
 * {@code describeSalesperson(Salesperson)} - which only worked because the caller already held one
 * through {@code Order.salesperson}. With {@code salespeople} owned by Identity, the parameter
 * becomes the id number the order actually stores - Identity's API exposes no surrogate id.
 * <p>
 * <b>Read-only</b>, which makes this the simplest of the three remote contracts: nothing in this
 * service ever creates a salesperson, so there is no distributed-write question of the kind
 * {@link CustomerProvisioningContract} carries.
 * <p>
 * An order may have no salesperson at all, so a null identifier is a normal input and not an error.
 */
public interface SalespersonLookupContract {
    /**
     * @param salespersonIdNumber the id number stored on the order, which may be {@code null}
     * @return the person details, or {@code null} if the identifier is null or does not resolve
     */
    PersonPojo describeSalesperson(String salespersonIdNumber);
}
