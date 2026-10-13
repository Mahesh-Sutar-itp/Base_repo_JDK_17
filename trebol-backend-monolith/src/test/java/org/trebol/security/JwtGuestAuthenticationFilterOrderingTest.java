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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.trebol.api.models.PersonPojo;
import org.trebol.api.services.GuestCustomerProvisioningService;
import org.trebol.common.exceptions.BadInputException;
import org.trebol.config.SecurityProperties;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Register item D14: the guest session filter must not write business data before authenticating,
 * and must not hold the write itself.
 * <p>
 * Baseline behaviour, for the record: the filter parsed the request body, persisted a real
 * {@code person} and {@code customer} row, and only then called the authentication manager - so a
 * failed guest login still created rows, from inside an authentication filter.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtGuestAuthenticationFilterOrderingTest {
    static final String GUEST_NAME = "guest";
    @Mock SecurityProperties securityPropertiesMock;
    @Mock AuthenticationManager authenticationManagerMock;
    @Mock GuestCustomerProvisioningService guestCustomerProvisioningServiceMock;
    JwtGuestAuthenticationFilter instance;
    MockHttpServletRequest request;
    MockHttpServletResponse response;

    @BeforeEach
    void beforeEach() {
        SecretKey secretKey = new SecretKeySpec(
            "0123456789012345678901234567890123456789012345678901234567890123".getBytes(), "HmacSHA256");
        when(securityPropertiesMock.getGuestUserName()).thenReturn(GUEST_NAME);
        when(securityPropertiesMock.getJwtExpirationAfterHours()).thenReturn(1);
        instance = new JwtGuestAuthenticationFilter(
            securityPropertiesMock,
            secretKey,
            authenticationManagerMock,
            guestCustomerProvisioningServiceMock,
            new ObjectMapper());
        request = new MockHttpServletRequest("POST", "/public/guest");
        request.setContent("{\"idNumber\":\"1\",\"firstName\":\"first\",\"lastName\":\"last\",\"email\":\"a@b.c\"}"
            .getBytes());
        response = new MockHttpServletResponse();
    }

    @Test
    void authenticates_before_provisioning_the_customer() throws BadInputException {
        when(authenticationManagerMock.authenticate(any(Authentication.class)))
            .thenReturn(new UsernamePasswordAuthenticationToken(GUEST_NAME, null, List.of()));

        Authentication result = instance.attemptAuthentication(request, response);

        assertNotNull(result);
        InOrder order = inOrder(authenticationManagerMock, guestCustomerProvisioningServiceMock);
        order.verify(authenticationManagerMock).authenticate(any(Authentication.class));
        order.verify(guestCustomerProvisioningServiceMock).provisionCustomer(any(PersonPojo.class));
    }

    @Test
    void does_not_provision_a_customer_when_authentication_fails() throws BadInputException {
        when(authenticationManagerMock.authenticate(any(Authentication.class)))
            .thenThrow(new BadCredentialsException("nope"));

        assertThrows(BadCredentialsException.class, () -> instance.attemptAuthentication(request, response));

        verify(guestCustomerProvisioningServiceMock, never()).provisionCustomer(any());
    }

    @Test
    void reports_invalid_profile_data_as_bad_credentials() throws BadInputException {
        when(authenticationManagerMock.authenticate(any(Authentication.class)))
            .thenReturn(new UsernamePasswordAuthenticationToken(GUEST_NAME, null, List.of()));
        doThrow(new BadInputException("invalid"))
            .when(guestCustomerProvisioningServiceMock).provisionCustomer(any(PersonPojo.class));

        BadCredentialsException result = assertThrows(BadCredentialsException.class,
            () -> instance.attemptAuthentication(request, response));

        assertEquals("Insufficient or invalid profile data for guest", result.getMessage());
    }

    @Test
    void ignores_requests_that_are_not_post() {
        MockHttpServletRequest getRequest = new MockHttpServletRequest("GET", "/public/guest");

        assertEquals(null, instance.attemptAuthentication(getRequest, response));
    }
}
