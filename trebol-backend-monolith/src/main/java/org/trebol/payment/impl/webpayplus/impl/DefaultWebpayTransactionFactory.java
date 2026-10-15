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

package org.trebol.payment.impl.webpayplus.impl;

import cl.transbank.common.IntegrationApiKeys;
import cl.transbank.common.IntegrationCommerceCodes;
import cl.transbank.common.IntegrationType;
import cl.transbank.webpay.common.WebpayOptions;
import cl.transbank.webpay.webpayplus.WebpayPlus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.trebol.payment.impl.webpayplus.WebpayTransactionFactory;
import org.trebol.payment.impl.webpayplus.WebpayplusPaymentProperties;

/**
 * Production implementation of {@link WebpayTransactionFactory}.
 * <p>
 * The body of {@link #newTransaction()} is the former
 * {@code WebpayplusPaymentServiceImpl.createWebpayTransaction()} <b>verbatim</b> - same constants,
 * same branch, same order of operations. Register item D18 moved the construction site so the
 * adapter could be unit-tested; it changed nothing about what gets constructed.
 * <p>
 * Note which credentials each mode uses, because it is the difference between touching a sandbox
 * and touching real money:
 * <ul>
 *   <li><b>not production</b> - Transbank's public integration constants
 *       ({@code IntegrationCommerceCodes.WEBPAY_PLUS}, {@code IntegrationApiKeys.WEBPAY}) against
 *       {@code IntegrationType.TEST};</li>
 *   <li><b>production</b> - the configured {@code commerceCode} and {@code apiKey} against
 *       {@code IntegrationType.LIVE}.</li>
 * </ul>
 * The production branch is selected by {@code Boolean.TRUE.equals(properties.isProduction())}, so a
 * null property means test mode - preserved exactly as it was.
 */
@Component
public class DefaultWebpayTransactionFactory
    implements WebpayTransactionFactory {
    private final WebpayplusPaymentProperties properties;

    @Autowired
    public DefaultWebpayTransactionFactory(
        WebpayplusPaymentProperties properties
    ) {
        this.properties = properties;
    }

    @Override
    public WebpayPlus.Transaction newTransaction() {
        String commerceCode = IntegrationCommerceCodes.WEBPAY_PLUS;
        String apiKey = IntegrationApiKeys.WEBPAY;
        IntegrationType integrationType = IntegrationType.TEST;
        if (Boolean.TRUE.equals(properties.isProduction())) {
            commerceCode = properties.getCommerceCode();
            apiKey = properties.getApiKey();
            integrationType = IntegrationType.LIVE;
        }

        WebpayOptions wpOptions = new WebpayOptions(commerceCode, apiKey, integrationType);

        return new WebpayPlus.Transaction(wpOptions);
    }
}
