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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.trebol.api.controllers.AccessController;
import org.trebol.api.controllers.AccountProfileController;
import org.trebol.api.controllers.DataCustomersController;
import org.trebol.api.controllers.DataPeopleController;
import org.trebol.api.controllers.DataSalespeopleController;
import org.trebol.api.controllers.DataUserRolesController;
import org.trebol.api.controllers.DataUsersController;
import org.trebol.api.controllers.PublicRegisterController;
import org.trebol.api.services.GuestCustomerProvisioningService;
import org.trebol.api.services.ProfileService;
import org.trebol.api.services.RegistrationService;
import org.trebol.jpa.services.contracts.CustomerProvisioningContract;
import org.trebol.jpa.services.contracts.SalespersonLookupContract;

import javax.crypto.SecretKey;
import java.util.List;

/**
 * Context-load test for the Identity service, modelled on the monolith's {@code BackendAppTest}.
 * <p>
 * It proves several things a compiling module does not:
 * <ul>
 *   <li>the Spring context actually starts - which also proves the
 *       {@code @Validated @ConfigurationProperties} beans bind, including the {@code @NotBlank}
 *       signing key and the two validation regexes;</li>
 *   <li>every ported controller is wired;</li>
 *   <li><b>the token-issuing machinery exists here and only here</b> - the
 *       {@code AuthenticationManager}, the {@code UserDetailsService}, the bcrypt
 *       {@code PasswordEncoder} and the shared {@code SecretKey}. The Catalogue service wires none
 *       of these, and its context test asserts their absence by simply not naming them;</li>
 *   <li>both <b>published</b> Phase 2 contract beans exist - {@link CustomerProvisioningContract}
 *       and {@link SalespersonLookupContract}, which closed coupling hotspot HS-02 and which the
 *       Orders service will depend on from Phase 3 onward.</li>
 * </ul>
 * The context starts with <b>no environment variable set</b>, because
 * {@code src/test/resources/application.properties} supplies throwaway values. That file is a test
 * fixture; the deployment configuration carries no secret and no default for any sensitive value
 * (invariant G7, exit criterion X10).
 */
@SpringBootTest
class IdentityApplicationTest {
    @Autowired DataUsersController usersController;
    @Autowired DataUserRolesController userRolesController;
    @Autowired DataCustomersController customersController;
    @Autowired DataSalespeopleController salespeopleController;
    @Autowired DataPeopleController peopleController;
    @Autowired AccountProfileController accountProfileController;
    @Autowired AccessController accessController;
    @Autowired PublicRegisterController registerController;
    @Autowired RegistrationService registrationService;
    @Autowired ProfileService profileService;
    @Autowired GuestCustomerProvisioningService guestCustomerProvisioningService;
    @Autowired CustomerProvisioningContract customerProvisioningContract;
    @Autowired SalespersonLookupContract salespersonLookupContract;
    @Autowired AuthenticationManager authenticationManager;
    @Autowired UserDetailsService userDetailsService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired SecretKey secretKey;

    @Test
    void contextLoads() {
        List.of(
            usersController,
            userRolesController,
            customersController,
            salespeopleController,
            peopleController,
            accountProfileController,
            accessController,
            registerController,
            registrationService,
            profileService,
            guestCustomerProvisioningService,
            customerProvisioningContract,
            salespersonLookupContract,
            authenticationManager,
            userDetailsService,
            passwordEncoder,
            secretKey
        ).forEach(Assertions::assertNotNull);
    }
}
