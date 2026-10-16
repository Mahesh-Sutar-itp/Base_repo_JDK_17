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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * The two {@link RestClient} beans this service uses to reach its siblings.
 * <p>
 * <b>Phase 3.</b> {@code RestClient} rather than {@code RestTemplate} (deprecated for new code) or
 * {@code WebClient} (which would pull in the reactive stack for no benefit in a servlet
 * application). This was the recommended option of the four recorded in the Phase 3 plan.
 * <p>
 * Both clients carry the configured timeout. Neither carries a retry: a retry on the checkout
 * critical path multiplies latency for a customer who is waiting, and the one call that genuinely
 * needs retry semantics - the mail dispatch - already has them, bounded, in the outbox.
 */
@Configuration
public class ServiceClientConfig {
    private SimpleClientHttpRequestFactory requestFactory(int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return factory;
    }

    @Bean
    public RestClient catalogueRestClient(ServiceEndpointProperties properties) {
        return RestClient.builder()
            .baseUrl(properties.getCatalogueBaseUrl())
            .requestFactory(this.requestFactory(properties.getRequestTimeoutMs()))
            .build();
    }

    @Bean
    public RestClient identityRestClient(ServiceEndpointProperties properties) {
        return RestClient.builder()
            .baseUrl(properties.getIdentityBaseUrl())
            .requestFactory(this.requestFactory(properties.getRequestTimeoutMs()))
            .build();
    }
}
