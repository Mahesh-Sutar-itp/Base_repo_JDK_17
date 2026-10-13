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

package org.trebol.orders;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Orders service - owns the commercial transaction end to end.
 * <p>
 * Absorbs three bounded contexts from the monolith: {@code order}, {@code billing} and
 * {@code shipper}. The latter two are grouped here because orders is their only consumer
 * and both are lookup tables in practice. That grouping also collapses three of the four
 * cross-domain lookups identified by Gate A into <b>internal</b> calls, leaving only
 * product and customer as genuinely cross-service.
 * <p>
 * Owns eight tables: {@code orders}, {@code order_details}, {@code order_statuses},
 * {@code addresses}, {@code payment_types}, {@code billing_types},
 * {@code billing_companies}, {@code shippers}.
 * <p>
 * Holds the system's only real state machine, its only two outbound integrations (payment
 * and notification), and the public payment callbacks. Extracted last because it reads from
 * both other services.
 */
@SpringBootApplication
public class OrdersServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrdersServiceApplication.class, args);
    }
}
