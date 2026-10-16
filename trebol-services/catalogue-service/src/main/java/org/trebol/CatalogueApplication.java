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

package org.trebol;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * Starting point of the Catalogue service.
 * <p>
 * Owns seven tables exclusively - {@code products}, {@code product_categories},
 * {@code product_images}, {@code product_lists}, {@code product_list_items}, {@code images} and
 * {@code app_params} - and reads no other service's schema. Serves the six catalogue route surfaces
 * ported verbatim from the monolith.
 */
/*
 * Phase 4, register item D31: Spring Boot's UserDetailsServiceAutoConfiguration creates an
 * InMemoryUserDetailsManager with a RANDOM GENERATED PASSWORD whenever no UserDetailsService,
 * AuthenticationProvider or AuthenticationManager bean is defined - which is exactly the situation
 * in a verify-only service. The result was a credential printed to the log on every startup
 * ("Using generated security password: ..."), in a service whose SecurityConfig javadoc states it
 * has no UserDetailsService and whose ADR-002 role is to verify tokens, never to authenticate.
 *
 * It was inert: the filter chain configures no formLogin and no httpBasic, so that user had no way
 * to authenticate over HTTP. It is excluded anyway, because "inert" depended on an absence
 * elsewhere - adding httpBasic() for any reason would have silently promoted it to a working
 * account. Asserted going forward by the context test's
 * no_token_issuing_or_credential_machinery_is_present.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class CatalogueApplication {
    /**
     * Application starting point
     *
     * @param args Command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(CatalogueApplication.class, args);
    }

}
