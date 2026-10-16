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

package org.trebol.payment.impl.webpayplus;

import org.junit.jupiter.api.Test;
import org.trebol.api.models.OrderPojo;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Register item D9 - payment session identifier stability.
 * <p>
 * Deliberately a separate test class from {@code WebpayplusPaymentServiceImplTest}: that test mocks
 * only the configuration properties and not the Transbank SDK, so every one of its cases performs a
 * real HTTP call to the sandbox (register item D18, which has its own ruling and is to be fixed as
 * its own reviewed change before Phase 3). These assertions exercise the identifier generator
 * directly and therefore never touch the network.
 */
class WebpayplusPaymentSessionIdTest {
    @Test
    void session_id_is_not_the_order_hash_code() {
        OrderPojo transaction = OrderPojo.builder()
            .buyOrder(1L)
            .totalValue(1000)
            .build();

        assertNotEquals(String.valueOf(transaction.hashCode()),
            WebpayplusPaymentServiceImpl.newSessionId());
    }

    @Test
    void session_ids_are_unique_across_calls() {
        Set<String> generated = new HashSet<>();
        IntStream.range(0, 1000).forEach(i -> generated.add(WebpayplusPaymentServiceImpl.newSessionId()));

        assertEquals(1000, generated.size());
    }

    /**
     * Webpay limits the session identifier to 61 characters.
     */
    @Test
    void session_id_fits_the_provider_field() {
        String sessionId = WebpayplusPaymentServiceImpl.newSessionId();

        assertEquals(32, sessionId.length());
        assertTrue(sessionId.matches("[0-9a-f]{32}"));
    }
}
