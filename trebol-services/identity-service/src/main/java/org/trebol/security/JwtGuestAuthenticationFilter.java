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

package org.trebol.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.trebol.api.models.PersonPojo;
import org.trebol.api.services.GuestCustomerProvisioningService;
import org.trebol.common.exceptions.BadInputException;
import org.trebol.config.SecurityProperties;

import javax.crypto.SecretKey;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class JwtGuestAuthenticationFilter
    extends GenericJwtAuthenticationFilter {
    private final AuthenticationManager authenticationManager;
    private final GuestCustomerProvisioningService guestCustomerProvisioningService;
    private final SecurityProperties securityProperties;
    private final ObjectMapper objectMapper;

    public JwtGuestAuthenticationFilter(
        SecurityProperties securityProperties,
        SecretKey secretKey,
        AuthenticationManager authenticationManager,
        GuestCustomerProvisioningService guestCustomerProvisioningService,
        ObjectMapper objectMapper
    ) {
        super(securityProperties, secretKey);
        this.securityProperties = securityProperties;
        this.authenticationManager = authenticationManager;
        this.guestCustomerProvisioningService = guestCustomerProvisioningService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
        throws AuthenticationException {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return null;
        } else {
            try {
                PersonPojo guestCustomerData = objectMapper.readValue(request.getInputStream(), PersonPojo.class);
                final String credential = securityProperties.getGuestUserName();
                Authentication authentication = new UsernamePasswordAuthenticationToken(credential, credential);
                // Register item D14: authenticate BEFORE provisioning. This filter used to persist a
                // real person and customer row first, so a failed guest login still wrote business
                // data. The write itself now belongs to a transactional application service.
                Authentication result = authenticationManager.authenticate(authentication);
                guestCustomerProvisioningService.provisionCustomer(guestCustomerData);
                return result;
            } catch (IOException e) {
                throw new BadCredentialsException("Invalid request body for guest session");
            } catch (BadInputException e) {
                throw new BadCredentialsException("Insufficient or invalid profile data for guest");
            }
        }
    }
}
