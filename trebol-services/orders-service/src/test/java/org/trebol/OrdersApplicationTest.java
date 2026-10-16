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
import org.springframework.context.ApplicationContext;
import org.trebol.api.controllers.DataBillingTypesController;
import org.trebol.api.controllers.DataOrderStatusesController;
import org.trebol.api.controllers.DataOrdersController;
import org.trebol.api.controllers.DataShippersController;
import org.trebol.api.controllers.PublicCheckoutController;
import org.trebol.api.controllers.PublicReceiptController;
import org.trebol.api.controllers.RootController;
import org.trebol.api.services.CheckoutService;
import org.trebol.jpa.services.contracts.BillingContract;
import org.trebol.jpa.services.contracts.CustomerProvisioningContract;
import org.trebol.jpa.services.contracts.ProductLookupContract;
import org.trebol.jpa.services.contracts.SalespersonLookupContract;
import org.trebol.jpa.services.contracts.ShipperLookupContract;
import org.trebol.mailing.outbox.MailOutboxService;
import org.trebol.payment.PaymentService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Context-load test for the Orders service, modelled on the monolith's {@code BackendAppTest} and
 * on the equivalents in catalogue-service and identity-service.
 * <p>
 * Added in Phase 4. Orders was the only service without one: its context was being loaded
 * incidentally by {@code OrderStatusPatchPersistenceProbeTest}, which exists to interrogate
 * Hibernate metadata rather than to assert the service starts. That accident is what caught
 * register item D29 - a property prefix that made the service unstartable - so the guarantee is
 * worth asserting deliberately rather than relying on a side effect of an unrelated test.
 * <p>
 * It proves what a compiling module does not:
 * <ul>
 *   <li>the context starts, which also proves every {@code @Validated @ConfigurationProperties}
 *       bean binds - including the {@code @NotBlank} Webpay callback URLs that D29 broke;</li>
 *   <li>all six route-serving controllers plus the Phase 4 health check are wired;</li>
 *   <li><b>the three REMOTE contracts resolve to beans</b> - product, customer and salesperson.
 *       These are {@code RestClient}-backed, so a missing {@code @Qualifier} or endpoint bean fails
 *       here rather than on the first checkout;</li>
 *   <li>the two LOCAL contracts resolve as well - billing and shipper;</li>
 *   <li>the payment adapter and the D11 outbox service are present;</li>
 *   <li><b>no token-issuing machinery exists</b>, asserted by name rather than by omission: per
 *       ADR-002 this service verifies tokens and must not be able to mint one.</li>
 * </ul>
 */
@SpringBootTest
class OrdersApplicationTest {
    @Autowired ApplicationContext context;

    @Autowired DataOrdersController dataOrdersController;
    @Autowired DataOrderStatusesController dataOrderStatusesController;
    @Autowired DataBillingTypesController dataBillingTypesController;
    @Autowired DataShippersController dataShippersController;
    @Autowired PublicCheckoutController publicCheckoutController;
    @Autowired PublicReceiptController publicReceiptController;
    @Autowired RootController rootController;

    @Autowired CheckoutService checkoutService;
    @Autowired PaymentService paymentService;
    @Autowired MailOutboxService mailOutboxService;

    @Autowired ProductLookupContract productLookupContract;
    @Autowired CustomerProvisioningContract customerProvisioningContract;
    @Autowired SalespersonLookupContract salespersonLookupContract;
    @Autowired BillingContract billingContract;
    @Autowired ShipperLookupContract shipperLookupContract;

    @Test
    void the_context_starts_and_every_route_serving_controller_is_wired() {
        Assertions.assertAll(
            () -> Assertions.assertNotNull(dataOrdersController),
            () -> Assertions.assertNotNull(dataOrderStatusesController),
            () -> Assertions.assertNotNull(dataBillingTypesController),
            () -> Assertions.assertNotNull(dataShippersController),
            () -> Assertions.assertNotNull(publicCheckoutController),
            () -> Assertions.assertNotNull(publicReceiptController),
            () -> Assertions.assertNotNull(rootController));
    }

    @Test
    void the_checkout_payment_and_outbox_collaborators_are_wired() {
        Assertions.assertAll(
            () -> Assertions.assertNotNull(checkoutService),
            () -> Assertions.assertNotNull(paymentService),
            () -> Assertions.assertNotNull(mailOutboxService));
    }

    /**
     * The five contract seams. The first three cross a service boundary, so a wiring fault in them
     * is invisible until a real request tries to reach a sibling service.
     */
    @Test
    void all_five_contract_seams_resolve_to_beans() {
        Assertions.assertAll(
            () -> Assertions.assertNotNull(productLookupContract),
            () -> Assertions.assertNotNull(customerProvisioningContract),
            () -> Assertions.assertNotNull(salespersonLookupContract),
            () -> Assertions.assertNotNull(billingContract),
            () -> Assertions.assertNotNull(shipperLookupContract));
    }

    /**
     * ADR-002, asserted positively. Identity is the only issuer; this service verifies. Naming the
     * bean types explicitly means a future change that adds a password encoder or an authentication
     * manager here fails this test instead of quietly widening what the service can do.
     */
    @Test
    void no_token_issuing_or_credential_machinery_is_present() {
        List.of(
            "org.springframework.security.authentication.AuthenticationManager",
            "org.springframework.security.core.userdetails.UserDetailsService",
            "org.springframework.security.crypto.password.PasswordEncoder"
        ).forEach(typeName -> {
            Class<?> type;
            try {
                type = Class.forName(typeName);
            } catch (ClassNotFoundException notOnClasspath) {
                return;
            }
            assertThat(context.getBeanNamesForType(type))
                .as("%s must not exist in a verify-only service (ADR-002)", typeName)
                .isEmpty();
        });
    }
}
