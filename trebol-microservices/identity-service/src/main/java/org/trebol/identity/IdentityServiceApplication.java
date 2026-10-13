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

package org.trebol.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Identity service - owns who a caller is, what they may do, and the personal-identity
 * master data.
 * <p>
 * Absorbs four bounded contexts from the monolith: {@code person}, {@code user},
 * {@code customer} and {@code salesperson}. They are grouped because all four project onto
 * the same {@code Person} record, because a customer has no external representation of its
 * own, and because the access-control aggregate's invariant - effective authorities derived
 * across account, role, grant and authority - cannot survive being split.
 * <p>
 * Owns seven tables: {@code people}, {@code app_users}, {@code app_user_roles},
 * {@code app_permissions}, {@code app_user_role_permissions}, {@code customers},
 * {@code salespeople}.
 * <p>
 * This service is the <b>only</b> token issuer. The other two verify tokens locally with
 * the shared signing key and never call back here to authorize.
 */
@SpringBootApplication
public class IdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}
