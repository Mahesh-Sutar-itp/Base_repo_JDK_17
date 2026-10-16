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

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.trebol.api.models.DataPagePojo;
import org.trebol.api.models.PersonPojo;
import org.trebol.integration.RemoteLookupSupport;
import org.trebol.jpa.services.contracts.SalespersonLookupContract;

import java.util.Map;

/**
 * Reads salespeople from the Identity service over HTTP.
 * <p>
 * <b>Phase 3.</b> The simplest of the three remote contracts: read-only, keyed by the identifier
 * the order already stores, and reached only when rendering an order that actually has a
 * salesperson.
 * <p>
 * A null identifier is a normal input - most orders have no salesperson - and yields {@code null}
 * without a call. A failed lookup also yields {@code null}, so an unavailable Identity service
 * degrades an order read to one without salesperson details rather than failing it. That is a
 * deliberate choice: the salesperson is incidental to an order, and refusing to show the order at
 * all would be a worse outcome than showing it without a name.
 */
@Service
public class SalespersonLookupContractImpl
    implements SalespersonLookupContract {
    private static final ParameterizedTypeReference<DataPagePojo<PersonPojo>> PAGE_OF_PEOPLE =
        new ParameterizedTypeReference<>() {
        };
    private final RestClient identityRestClient;

    public SalespersonLookupContractImpl(@Qualifier("identityRestClient") RestClient identityRestClient) {
        this.identityRestClient = identityRestClient;
    }

    @Override
    public PersonPojo describeSalesperson(String salespersonIdNumber) {
        if (salespersonIdNumber==null || salespersonIdNumber.isBlank()) {
            return null;
        }
        return RemoteLookupSupport.findOne(
                identityRestClient, "Identity", "/data/salespeople",
                Map.of("idNumber", salespersonIdNumber), PAGE_OF_PEOPLE)
            .orElse(null);
    }
}
