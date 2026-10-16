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

package org.trebol.integration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Where the two services this one depends on can be reached.
 * <p>
 * <b>Phase 3.</b> The Orders service is the only one in the platform with outbound calls to
 * sibling services - the Catalogue, for product lookups on the checkout and receipt paths, and
 * Identity, for customer provisioning and salesperson reads.
 * <p>
 * No service discovery and no client-side load balancing: plain configured base URLs, consistent
 * with {@code ADR-002}'s decision that each service verifies tokens locally rather than calling a
 * central authority. Introducing a registry would be new infrastructure, and this conversion adds
 * infrastructure only where a defect requires it (the mail outbox was the one such case).
 */
@Configuration
@ConfigurationProperties(prefix = "trebol.services")
@Getter
@Setter
public class ServiceEndpointProperties {
    /** Base URL of the Catalogue service, e.g. {@code http://localhost:8081}. */
    private String catalogueBaseUrl = "http://localhost:8081";
    /** Base URL of the Identity service, e.g. {@code http://localhost:8082}. */
    private String identityBaseUrl = "http://localhost:8082";
    /**
     * How long to wait for a sibling service, in milliseconds.
     * <p>
     * Deliberately short. A product lookup sits on the checkout critical path, and a customer
     * waiting to pay is better served by a prompt failure than a long stall.
     */
    private int requestTimeoutMs = 5000;
}
