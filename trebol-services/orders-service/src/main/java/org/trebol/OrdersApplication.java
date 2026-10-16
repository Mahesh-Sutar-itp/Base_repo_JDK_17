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
 * The Orders service.
 * <p>
 * Phase 3 of the microservices conversion. The transactional core: orders, order lines, the status
 * lifecycle, checkout, the Webpay Plus payment adapter, the order-status notification outbox and the
 * public receipt - carrying the billing and shipper domains with it, because every foreign key
 * between them and {@code orders} points inward and nothing in either domain points out.
 * <p>
 * Owns nine tables exclusively: {@code orders}, {@code order_details}, {@code order_statuses},
 * {@code order_mail_outbox}, {@code billing_types}, {@code billing_companies}, {@code addresses},
 * {@code payment_types} and {@code shippers}. It reads no other service's schema.
 * <p>
 * <b>Extracted last, because it is the only service with cross-service couplings to cut.</b> Three
 * of them, all recorded in the porting-depth specification:
 * <ul>
 *   <li><b>C1</b> - {@code OrderDetail.product} was an EAGER {@code @ManyToOne} to the catalogue's
 *       {@code Product}. It is now an identifier; the line's own description and unit value already
 *       carry the price-stable copy the order needs.</li>
 *   <li><b>C2</b> - {@code ReceiptServiceImpl} imported and dereferenced that entity directly, which
 *       Gate A's repository-only grep did not catch. It now goes through the published catalogue
 *       contract.</li>
 *   <li><b>C3</b> - {@code OrdersSortSpec}'s {@code "customer"} key joined into Identity's
 *       {@code people} table. It is dropped, leaving eight keys - the one client-visible capability
 *       this conversion removes.</li>
 * </ul>
 * {@code Order.customer} and {@code Order.salesperson} likewise became identifiers, cutting
 * {@code FK_ORDER_CUSTOM_ID} and {@code FK_ORDER_SPERSON}.
 * <p>
 * <b>This service verifies tokens and never issues them</b> ({@code ADR-002}): no
 * {@code UserDetailsService}, no {@code AuthenticationManager}, no {@code PasswordEncoder}. It holds
 * the only outbound third-party call in the platform, to Transbank, and the only scheduled work -
 * the mail outbox dispatcher added by register item D11.
 */
@SpringBootApplication
public class OrdersApplication {
    /**
     * Application starting point
     *
     * @param args Runtime arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(OrdersApplication.class, args);
    }
}
