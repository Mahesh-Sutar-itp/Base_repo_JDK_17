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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.trebol.api.models.DataPagePojo;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shared plumbing for the three remote contract implementations.
 * <p>
 * <b>Phase 3.</b> Each sibling service exposes its reads as a paged {@code /data/...} route, so
 * every lookup here has the same shape: filter by one field, expect zero or one item, take the
 * first. Rather than repeat that - and repeat the token relay and the failure handling - it lives
 * once.
 * <p>
 * <b>A failed lookup returns empty rather than throwing.</b> Each caller decides what an absent
 * result means, and they differ: an unresolvable product on the checkout path is a
 * {@code BadInputException} the customer must see, while an unresolvable product on the receipt
 * path is a line rendered without a name. Throwing here would deny them that choice.
 */
public final class RemoteLookupSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteLookupSupport.class);

    private RemoteLookupSupport() {
    }

    /**
     * Performs a single-result paged lookup against a sibling service.
     *
     * @param client   the configured client for that service
     * @param service  the service's name, for log messages only
     * @param path     the route, e.g. {@code /data/products}
     * @param filters  the filter parameters, which must be keys the target's predicate service
     *                 recognises - an unrecognised key is silently ignored there and would widen
     *                 the result set rather than failing
     * @param itemType the expected item type
     * @return the first item, or empty if none matched or the call failed
     */
    public static <T> Optional<T> findOne(
        RestClient client,
        String service,
        String path,
        Map<String, String> filters,
        ParameterizedTypeReference<DataPagePojo<T>> itemType
    ) {
        try {
            String authorization = CallerTokenRelay.currentAuthorizationHeader();
            DataPagePojo<T> page = client.get()
                .uri(uriBuilder -> {
                    uriBuilder.path(path);
                    filters.forEach(uriBuilder::queryParam);
                    return uriBuilder.build();
                })
                .headers(headers -> {
                    if (authorization!=null) {
                        headers.set(HttpHeaders.AUTHORIZATION, authorization);
                    }
                })
                .retrieve()
                .body(itemType);
            if (page==null || page.getItems()==null) {
                return Optional.empty();
            }
            List<T> items = List.copyOf(page.getItems());
            return items.isEmpty() ? Optional.empty():Optional.of(items.get(0));
        } catch (RuntimeException exc) {
            // Includes connection failures, timeouts and any non-2xx status. Deliberately does not
            // echo the filter values, which can carry customer data.
            LOGGER.warn("Lookup against the {} service at {} failed: {}", service, path, exc.getMessage());
            return Optional.empty();
        }
    }
}
