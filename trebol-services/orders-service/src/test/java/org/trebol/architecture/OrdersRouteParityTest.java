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

package org.trebol.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.trebol.api.controllers.DataBillingTypesController;
import org.trebol.api.controllers.DataOrderStatusesController;
import org.trebol.api.controllers.DataOrdersController;
import org.trebol.api.controllers.DataShippersController;
import org.trebol.api.controllers.PublicCheckoutController;
import org.trebol.api.controllers.PublicReceiptController;
import org.trebol.api.controllers.RootController;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Route parity between this service and the monolith it was extracted from.
 * <p>
 * This class is new in Phase 4. Catalogue and Identity each had a parity test from the phase that
 * extracted them; Orders did not, which left the largest and most security-sensitive surface in the
 * system - checkout and the payment callbacks - with no pin on its HTTP shape at all. A compiling
 * module proves nothing about exit criterion X3.
 * <p>
 * Routes are keyed {@code "VERB subpath"} rather than by verb alone, because
 * {@code /data/orders} is the one surface in the platform that serves <b>three extra sub-paths</b>
 * ({@code /confirmation}, {@code /rejection}, {@code /completion}) on top of the five CRUD verbs.
 * <p>
 * Authorities are asserted by <b>value</b>, not by constant name, because the strings are the
 * client-observable contract (G2). The two deliberately <b>anonymous</b> groups are the point of
 * this test rather than an afterthought:
 * <ul>
 *   <li>{@code GET /data/billing_types} and {@code GET /data/shippers} are <b>public reads</b> with
 *       no class-level guard and no authority, while their writes are fully authorised. Adding a
 *       guard to either read would break anonymous checkout, which needs both lists to render a
 *       form.</li>
 *   <li>{@code GET} and {@code POST /public/checkout/validate} are the <b>payment callbacks</b> and
 *       carry no authority by design. Transbank sends no credential, so an authority check would
 *       make them permanently unreachable. Their protection is register item D4's per-order nonce
 *       plus idempotency, <b>not</b> an authority - and that is exactly why this test asserts the
 *       absence explicitly. Someone "hardening" these routes with an authority would silently
 *       break every payment confirmation.</li>
 * </ul>
 */
class OrdersRouteParityTest {
    /** prefix -&gt; "VERB subpath" -&gt; expected authority value, or "" for a deliberately open route. */
    private static final Map<String, Map<String, String>> EXPECTED_SURFACE = new LinkedHashMap<>();

    static {
        // Five CRUD verbs plus the three status-transition sub-paths, all of which are an
        // orders:update rather than a distinct authority (BR-ORDER rulings, D5's transition table
        // is what constrains them, not a permission).
        Map<String, String> orders = new TreeMap<>();
        orders.put("GET", "orders:read");
        orders.put("POST", "orders:create");
        orders.put("PUT", "orders:update");
        orders.put("PATCH", "orders:update");
        orders.put("DELETE", "orders:delete");
        orders.put("POST /confirmation", "orders:update");
        orders.put("POST /rejection", "orders:update");
        orders.put("POST /completion", "orders:update");
        EXPECTED_SURFACE.put("/data/orders", orders);

        // Read-only: statuses are seeded reference data, written by no route.
        EXPECTED_SURFACE.put("/data/order_statuses", Map.of("GET", "order_statuses:read"));

        // Read-only AND anonymous - no class-level guard, no authority. Billing has no authorities
        // in the 54-code catalogue at all (BR-BILLING-019).
        EXPECTED_SURFACE.put("/data/billing_types", Map.of("GET", ""));

        // Anonymous read, authorised writes. The asymmetry is deliberate.
        Map<String, String> shippers = new TreeMap<>();
        shippers.put("GET", "");
        shippers.put("POST", "shippers:create");
        shippers.put("PUT", "shippers:update");
        shippers.put("PATCH", "shippers:update");
        shippers.put("DELETE", "shippers:delete");
        EXPECTED_SURFACE.put("/data/shippers", shippers);

        // Submitting a cart requires the checkout authority, which a guest token carries.
        // The two /validate callbacks are anonymous by necessity - see the class javadoc.
        Map<String, String> checkout = new TreeMap<>();
        checkout.put("POST", "checkout");
        checkout.put("GET /validate", "");
        checkout.put("POST /validate", "");
        EXPECTED_SURFACE.put("/public/checkout", checkout);

        // Token-addressed and fully anonymous: the token IS the credential.
        EXPECTED_SURFACE.put("/public/receipt", Map.of("GET /{token}", ""));
    }

    private static final Class<?>[] CONTROLLERS = {
        DataOrdersController.class,
        DataOrderStatusesController.class,
        DataBillingTypesController.class,
        DataShippersController.class,
        PublicCheckoutController.class,
        PublicReceiptController.class
    };

    /** The only two order-domain controllers carrying a class-level {@code isAuthenticated()}. */
    private static final Class<?>[] AUTHENTICATED_CLASSES = {
        DataOrdersController.class,
        DataOrderStatusesController.class
    };

    /**
     * Must NOT carry a class-level guard. Listed explicitly so that "tidying up" by adding one -
     * which would look like a security improvement - fails loudly instead.
     */
    private static final Class<?>[] DELIBERATELY_UNGUARDED_CLASSES = {
        DataBillingTypesController.class,
        DataShippersController.class,
        PublicCheckoutController.class,
        PublicReceiptController.class,
        RootController.class
    };

    @Test
    void the_service_exposes_exactly_the_order_domain_route_prefixes() {
        Map<String, Class<?>> actual = new TreeMap<>();
        for (Class<?> controller : CONTROLLERS) {
            RequestMapping mapping = controller.getAnnotation(RequestMapping.class);
            assertThat(mapping).as("%s must declare a class-level request mapping",
                controller.getSimpleName()).isNotNull();
            assertThat(mapping.value()).hasSize(1);
            actual.put(mapping.value()[0], controller);
        }
        assertThat(actual.keySet())
            .as("the order-domain route surface must match the monolith's exactly")
            .containsExactlyInAnyOrderElementsOf(EXPECTED_SURFACE.keySet());
    }

    @Test
    void every_route_serves_the_same_methods_with_the_same_authorities_as_the_monolith() {
        for (Class<?> controller : CONTROLLERS) {
            String prefix = controller.getAnnotation(RequestMapping.class).value()[0];
            Map<String, String> expected = EXPECTED_SURFACE.get(prefix);
            Map<String, String> actual = new TreeMap<>();
            for (Method method : controller.getDeclaredMethods()) {
                String route = routeOf(method);
                if (route == null) {
                    continue;
                }
                actual.put(route, authorityOf(method));
            }
            assertThat(actual)
                .as("%s (%s) must serve the same routes with the same authorities", prefix,
                    controller.getSimpleName())
                .containsExactlyInAnyOrderEntriesOf(expected);
        }
    }

    @Test
    void the_authenticated_controllers_keep_their_class_level_requirement() {
        for (Class<?> controller : AUTHENTICATED_CLASSES) {
            PreAuthorize classLevel = controller.getAnnotation(PreAuthorize.class);
            assertThat(classLevel)
                .as("%s must keep its class-level authentication requirement",
                    controller.getSimpleName())
                .isNotNull();
            assertThat(classLevel.value()).isEqualTo("isAuthenticated()");
        }
    }

    /**
     * The anonymous half of the surface. Separated so an accidental addition fails with an
     * unambiguous message: every one of these classes is reachable by an unauthenticated client in
     * the monolith, and {@code SecurityConfig} declares no request-matcher rules, so a class-level
     * guard here is the whole difference between a working and a broken anonymous flow.
     */
    @Test
    void the_anonymous_controllers_carry_no_class_level_guard() {
        for (Class<?> controller : DELIBERATELY_UNGUARDED_CLASSES) {
            assertThat(controller.getAnnotation(PreAuthorize.class))
                .as("%s is reachable anonymously in the monolith and must stay that way",
                    controller.getSimpleName())
                .isNull();
        }
    }

    /**
     * Phase 4 ported {@code GET /} to all three services. It was the single route the Phase 3 exit
     * census found served by the monolith and by no extracted service - 66 of 67 - so until now
     * decommissioning the monolith would have silently dropped the platform's health check.
     */
    @Test
    void the_health_check_route_is_served_anonymously_and_takes_no_authority() throws Exception {
        assertThat(RootController.class.getAnnotation(RequestMapping.class))
            .as("the health check answers on / and must declare no class-level prefix")
            .isNull();
        Method defaultMapping = RootController.class.getDeclaredMethod("defaultMapping");
        assertThat(defaultMapping.getAnnotation(GetMapping.class))
            .as("GET / must still be mapped")
            .isNotNull();
        assertThat(authorityOf(defaultMapping))
            .as("a health check that needs a credential is not a health check")
            .isEmpty();
    }

    /** Returns {@code "VERB"} or {@code "VERB subpath"}, or null when the method maps nothing. */
    private static String routeOf(Method method) {
        GetMapping get = method.getAnnotation(GetMapping.class);
        if (get != null) {
            return append("GET", get.value());
        }
        PostMapping post = method.getAnnotation(PostMapping.class);
        if (post != null) {
            return append("POST", post.value());
        }
        PutMapping put = method.getAnnotation(PutMapping.class);
        if (put != null) {
            return append("PUT", put.value());
        }
        PatchMapping patch = method.getAnnotation(PatchMapping.class);
        if (patch != null) {
            return append("PATCH", patch.value());
        }
        DeleteMapping delete = method.getAnnotation(DeleteMapping.class);
        if (delete != null) {
            return append("DELETE", delete.value());
        }
        return null;
    }

    private static String append(String verb, String[] paths) {
        if (paths.length == 0 || paths[0].isEmpty()) {
            return verb;
        }
        return verb + " " + paths[0];
    }

    /**
     * The authority VALUE, or "" when the method carries no authority check. Parsed out of the
     * {@code hasAuthority('...')} expression because that expression is what Spring evaluates -
     * reading the constant instead would make the test pass even if the annotation were repointed.
     */
    private static String authorityOf(Method method) {
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        if (annotation == null) {
            return "";
        }
        String expression = annotation.value();
        int open = expression.indexOf('\'');
        int close = expression.lastIndexOf('\'');
        if (open < 0 || close <= open) {
            return "";
        }
        return expression.substring(open + 1, close);
    }
}
