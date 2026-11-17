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

package org.trebol.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Read cache kept in the memory of each app instance (task).<br/>
 * Most traffic is the same few public reads (product list and search, store info, billing types).
 * Caching them for a short time keeps database load nearly flat as the number of tasks grows,
 * because every new task brings its own cache.<br/>
 * Which cache backs it is a setting: {@code spring.cache.type=none} by default (tests always see
 * fresh data), {@code caffeine} in the prod profile. Names and time to live are in
 * application-prod.properties.<br/>
 * Trade-off: a change made through one task clears that task's cache at once, but other tasks can
 * serve the old list until their entry expires (the configured time to live, 30 s by default).
 * Lookups of one specific record are never cached.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Store information (/public/about). */
    public static final String ABOUT = "about";
    /** Billing types (/data/billing_types). */
    public static final String BILLING_TYPES = "billingTypes";
    /** Product list and search pages (/data/products without an identity filter). */
    public static final String PRODUCT_PAGES = "productPages";

    /**
     * SpEL condition for {@link org.springframework.cache.annotation.Cacheable}: cache a request
     * only when it does not look up one specific record. Used on handlers whose first argument is
     * the request parameter map.
     */
    public static final String NOT_AN_IDENTITY_LOOKUP =
        "!#root.args[0].containsKey('id') and !#root.args[0].containsKey('barcode')"
            + " and !#root.args[0].containsKey('name') and !#root.args[0].containsKey('code')";
}
