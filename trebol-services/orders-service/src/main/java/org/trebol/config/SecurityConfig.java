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

import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.trebol.security.JwtTokenVerifierFilter;
import org.trebol.security.services.AuthorizationHeaderParserService;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

/**
 * Security configuration of the Catalogue service - the <strong>verify-only</strong> half of the
 * monolith's {@code SecurityConfig}.
 * <p>
 * Per ADR-002 (Gate D) the decision recorded for the conversion is: one shared signing key, token
 * verification performed <strong>locally by each service</strong>, and method-level authority
 * enforcement retained; a gateway handles routing and TLS only. This class implements the Catalogue
 * end of that decision.
 * <p>
 * Deliberately <strong>absent</strong>, because this service issues no tokens and owns no user
 * tables - those belong to Identity:
 * <ul>
 *   <li>{@code JwtLoginAuthenticationFilter} and the {@code /public/login} route</li>
 *   <li>{@code JwtGuestAuthenticationFilter} and the {@code /public/guest} route</li>
 *   <li>{@code UserDetailsService}, the {@code DaoAuthenticationProvider} and the
 *       {@code AuthenticationManager}</li>
 *   <li>the bcrypt {@code PasswordEncoder}</li>
 * </ul>
 * <p>
 * Everything else is preserved exactly as the monolith behaves today, including the two properties
 * that are easy to mistake for oversights and are <strong>not</strong> to be corrected here:
 * <ol>
 *   <li><strong>No request-matcher rules are declared.</strong> Enforcement is entirely
 *       method-level, so any endpoint without an authority annotation is publicly reachable. The
 *       catalogue's read endpoints rely on this - the storefront reads products, categories, lists,
 *       list contents and the company profile anonymously.</li>
 *   <li>The verifier filter authenticates a request only when an {@code Authorization} header is
 *       present; a request without one proceeds unauthenticated rather than being rejected.</li>
 * </ol>
 * Changing either would break clients and would require a defect-register ruling (invariant G1).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    private final AuthorizationHeaderParserService<Claims> jwtClaimsParserService;

    @Autowired
    public SecurityConfig(AuthorizationHeaderParserService<Claims> jwtClaimsParserService) {
        this.jwtClaimsParserService = jwtClaimsParserService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
        return httpSecurity
            .headers(configure -> configure.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(configure -> configure.sessionCreationPolicy(STATELESS))
            .addFilterBefore(new JwtTokenVerifierFilter(jwtClaimsParserService),
                             org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
