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

package org.trebol.contracts.identity;

/**
 * Cross-service contract: resolve the customer behind a checkout, creating the underlying
 * person and customer records if they do not yet exist.
 * <p>
 * This is the second of the two genuinely cross-service lookups required by Gate A. It is
 * <b>owned and implemented by identity-service</b> and <b>consumed by orders-service</b>
 * during checkout.
 * <p>
 * In the monolith this was the one cross-domain resolution in the order converter that
 * already went through a service interface rather than a repository, which makes it the
 * least invasive of the four to lift.
 *
 * <h2>Why this is resolve-or-create, not a read</h2>
 * Checkout accepts a customer block and reuses the matching customer if one exists,
 * otherwise creates it. That makes this a <b>write</b> across the service boundary, inside
 * checkout - the single most consequential consistency decision in the conversion.
 * <p>
 * In the monolith neither {@code CheckoutServiceImpl} nor {@code RegistrationServiceImpl}
 * was transactional, so each write was already its own transaction and partial state was
 * already possible. This contract therefore does not introduce a new failure mode, but it
 * does make the existing one routine: a successful customer creation followed by a failed
 * order creation leaves an orphaned customer, and the monolith has no delete path for a
 * person.
 *
 * <h2>Behaviour that must be preserved</h2>
 * <ul>
 *   <li>Resolution is by national identification number, matched exactly and
 *       case-sensitively.</li>
 *   <li>A blank identification number is a client error: the monolith raised
 *       {@code "Customer does not have ID card"}.</li>
 *   <li>When a person already exists for the identification number, the stored record
 *       <b>wins</b> - the submitted name, e-mail and phone numbers are discarded without
 *       error.</li>
 *   <li>Phone numbers default to the empty string, never null, because both columns are
 *       NOT NULL with no database default.</li>
 * </ul>
 */
public interface CustomerLookup {

    /**
     * Resolve the customer for a checkout, creating the person and customer records if
     * absent.
     *
     * @param person the submitted customer block. Its identification number is the
     *               resolution key; all other fields are used only when creating.
     * @return a reference to the resolved or newly created customer. Never null.
     * @throws IllegalArgumentException if the identification number is null or blank - the
     *                                  caller is expected to translate this into the
     *                                  client-facing
     *                                  {@code "Customer does not have ID card"} error.
     */
    CustomerRef resolveOrCreate(PersonRef person);
}
