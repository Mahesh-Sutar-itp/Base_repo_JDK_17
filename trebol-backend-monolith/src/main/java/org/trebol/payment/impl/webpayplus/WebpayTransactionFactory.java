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

package org.trebol.payment.impl.webpayplus;

import cl.transbank.webpay.webpayplus.WebpayPlus;

/**
 * Supplies the Transbank SDK transaction object that {@link WebpayplusPaymentServiceImpl} uses.
 * <p>
 * <b>This interface exists for register item D18 and for no other reason.</b>
 * {@code WebpayplusPaymentServiceImpl} used to construct
 * {@code new WebpayPlus.Transaction(wpOptions)} inline, which meant nothing could intercept it:
 * the adapter's test mocked only {@code WebpayplusPaymentProperties}, so every test made a <b>real
 * HTTP call to the Transbank sandbox</b>. It was an integration test presented as a unit test, and
 * it failed with HTTP 403 off-network - which is why three tests were in the project's "known
 * failures" set from the very first baseline.
 * <p>
 * The consequence D18 records is the serious part: <b>the payment adapter had no genuine unit-test
 * coverage at all</b> - the most critical and least safely changeable component in the system, and
 * the one Phase 3 must port with the D3 and D4 fixes applied. Four of its catch branches were
 * simply untestable.
 * <p>
 * Introducing this seam is a <b>pure refactor with no behavioural change</b>: the same
 * {@code WebpayOptions} are built from the same properties in the same order, and only the
 * construction site moves. It is deliberately an injectable collaborator rather than a static
 * utility, so a test can substitute it without a mock-construction framework - no new test
 * dependency is added.
 *
 * @see DefaultWebpayTransactionFactory for the production implementation, which holds the original
 *     body verbatim
 */
public interface WebpayTransactionFactory {
    /**
     * @return a transaction object configured for the current mode - integration credentials against
     *     {@code IntegrationType.TEST}, or the configured commerce code and API key against
     *     {@code IntegrationType.LIVE} when running in production
     */
    WebpayPlus.Transaction newTransaction();
}
