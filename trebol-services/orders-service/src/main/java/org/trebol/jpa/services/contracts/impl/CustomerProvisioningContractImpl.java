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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.trebol.api.models.DataPagePojo;
import org.trebol.api.models.PersonPojo;
import org.trebol.common.exceptions.BadInputException;
import org.trebol.integration.CallerTokenRelay;
import org.trebol.integration.RemoteLookupSupport;
import org.trebol.jpa.services.contracts.CustomerProvisioningContract;

import java.util.Map;
import java.util.Optional;

/**
 * Resolves customers in the Identity service over HTTP.
 * <p>
 * <b>Phase 3.</b> Replaces the in-process implementation that returned the {@code Customer} entity
 * and relied on a {@code PERSIST} cascade to create the row.
 *
 * <h2>Why this is a read on the path that matters</h2>
 * The owner's decision on the provisioning question was <b>option (c)</b> - provision the customer
 * before checkout - and investigation found <b>Phase 2 had already implemented it</b>. Register item
 * <b>D14</b> moved guest customer provisioning out of {@code JwtGuestAuthenticationFilter} and into
 * {@code GuestCustomerProvisioningService}, which Identity runs <b>after authentication, at
 * guest-session time</b>, idempotently.
 * <p>
 * So by the time a guest reaches checkout, their {@code customers} and {@code people} rows
 * <b>already exist</b>. A registered customer's rows were created at registration. On both paths
 * {@link #resolveOrCreateCustomerId} therefore resolves and <b>never reaches the create branch</b>,
 * which means:
 * <ul>
 *   <li><b>no remote write happens inside the checkout transaction</b>, so the orphaned-customer
 *       window {@code ADR-001} warned about does not arise on the checkout path;</li>
 *   <li><b>no authority is widened.</b> A guest token carrying only {@code checkout} is sufficient,
 *       because resolving is a read of Identity's own data route.</li>
 * </ul>
 *
 * <h2>The create branch, and who can reach it</h2>
 * It remains, for one path: an administrator or salesperson creating an order through
 * {@code POST /data/orders} for a customer who has never been in the system. That caller holds
 * {@code orders:create}, and their relayed token carries {@code customers:create} if their role
 * grants it - so authorisation resolves correctly <b>without any grant change</b>, which is the
 * property that made token relay the right default.
 * <p>
 * A caller whose token lacks {@code customers:create} gets a {@code BadInputException} naming the
 * cause rather than an opaque failure. That is a <b>behavioural change on that one admin path</b>,
 * recorded in {@code PHASE-3-EXIT.md}: an order can no longer implicitly create a customer unless
 * the caller is separately entitled to create customers. Implicit creation of identity records from
 * an order payload is also how duplicate customers accumulate, so the stricter behaviour is
 * defensible on its own terms - but it is a change, not a preservation.
 */
@Service
public class CustomerProvisioningContractImpl
    implements CustomerProvisioningContract {
    private static final ParameterizedTypeReference<DataPagePojo<PersonPojo>> PAGE_OF_PEOPLE =
        new ParameterizedTypeReference<>() {
        };
    static final String CUSTOMER_COULD_NOT_BE_RESOLVED =
        "The customer could not be resolved, and this request is not entitled to create one";
    static final String CUSTOMER_HAS_NO_ID_NUMBER = "Customer has no id number";
    private final Logger logger = LoggerFactory.getLogger(CustomerProvisioningContractImpl.class);
    private final RestClient identityRestClient;

    public CustomerProvisioningContractImpl(@Qualifier("identityRestClient") RestClient identityRestClient) {
        this.identityRestClient = identityRestClient;
    }

    @Override
    public String resolveOrCreateCustomerIdNumber(PersonPojo customer) throws BadInputException {
        if (customer==null || customer.getIdNumber()==null || customer.getIdNumber().isBlank()) {
            // The literal Identity's own CustomersCrudServiceImpl.getExisting raises, preserved so
            // the client sees the same message it saw before the extraction.
            throw new BadInputException(CUSTOMER_HAS_NO_ID_NUMBER);
        }
        Optional<PersonPojo> existing = RemoteLookupSupport.findOne(
            identityRestClient, "Identity", "/data/customers",
            Map.of("idNumber", customer.getIdNumber()), PAGE_OF_PEOPLE);
        if (existing.isPresent()) {
            // The id number IS the key, so there is nothing to read back - which is the second
            // reason business keys suit a service boundary: resolution needs no extra round trip.
            return customer.getIdNumber();
        }
        return this.createCustomer(customer);
    }

    /**
     * The create branch. Reached only by an entitled administrative caller - see the class javadoc.
     */
    private String createCustomer(PersonPojo customer) throws BadInputException {
        try {
            String authorization = CallerTokenRelay.currentAuthorizationHeader();
            identityRestClient.post()
                .uri("/data/customers")
                .headers(headers -> {
                    if (authorization!=null) {
                        headers.set(HttpHeaders.AUTHORIZATION, authorization);
                    }
                })
                .body(customer)
                .retrieve()
                .toBodilessEntity();
        } catch (RuntimeException exc) {
            // A 403 from Identity lands here, and so does any transport failure. Deliberately does
            // not relay Identity's status or echo the submitted customer data.
            logger.warn("Could not provision a customer through the Identity service: {}", exc.getMessage());
            throw new BadInputException(CUSTOMER_COULD_NOT_BE_RESOLVED);
        }
        // POST /data/customers returns 201 with no body, and none is needed: the id number the
        // caller submitted is the key, so there is nothing to read back.
        return customer.getIdNumber();
    }

    @Override
    public PersonPojo describeCustomer(String customerIdNumber) {
        if (customerIdNumber==null || customerIdNumber.isBlank()) {
            return null;
        }
        return RemoteLookupSupport.findOne(
                identityRestClient, "Identity", "/data/customers",
                Map.of("idNumber", customerIdNumber), PAGE_OF_PEOPLE)
            .orElse(null);
    }
}
