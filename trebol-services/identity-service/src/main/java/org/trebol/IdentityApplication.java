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

/**
 * Starting point of the Identity service.
 * <p>
 * Owns seven tables exclusively - {@code people}, {@code app_users}, {@code app_user_roles},
 * {@code app_permissions}, {@code app_user_role_permissions}, {@code customers} and
 * {@code salespeople} - and reads no other service's schema. Every foreign key among those seven
 * points inside this schema; the two inbound keys from the order domain
 * ({@code FK_ORDER_CUSTOM_ID}, {@code FK_ORDER_SPERSON}) are cut in Phase 3.
 * <p>
 * Route surfaces served, ported verbatim from the monolith:
 * <ul>
 *   <li>{@code /data/users}, {@code /data/user_roles} - account and role administration, guarded by
 *       the eight {@code users:*} / {@code user_roles:*} authorities</li>
 *   <li>{@code /data/people}, {@code /data/customers}, {@code /data/salespeople} - profile,
 *       customer and salesperson administration</li>
 *   <li>{@code /account/profile} - self-service profile, authenticated-only with no authority
 *       check (BR-USER-050)</li>
 *   <li>{@code /access}, {@code /access/{apiRoute}} - authority discovery</li>
 *   <li>{@code /public/register} - self-registration, fully anonymous</li>
 *   <li>{@code /public/login} and {@code /public/guest} - <b>authentication filters, not controller
 *       mappings</b>. They are filter-processed URLs wired in {@code SecurityConfig}</li>
 * </ul>
 * <p>
 * <b>This is the only service that issues tokens.</b> It owns the login and guest filters, the
 * {@code UserDetailsService}, the {@code DaoAuthenticationProvider}, the
 * {@code AuthenticationManager} and the bcrypt {@code PasswordEncoder}. Every other service only
 * verifies, locally, against the shared signing key - see {@code ADR-002}.
 */
@SpringBootApplication
public class IdentityApplication {
    /**
     * Application starting point
     *
     * @param args Command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(IdentityApplication.class, args);
    }

}
