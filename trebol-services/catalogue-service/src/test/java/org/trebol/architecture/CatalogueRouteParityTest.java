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
import org.trebol.api.controllers.DataImagesController;
import org.trebol.api.controllers.DataProductCategoriesController;
import org.trebol.api.controllers.DataProductListContentsController;
import org.trebol.api.controllers.DataProductListsController;
import org.trebol.api.controllers.DataProductsController;
import org.trebol.api.controllers.PublicAboutController;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Route parity between this service and the monolith it was extracted from.
 * <p>
 * Exit criterion X3 requires that every route continues to be served and to behave identically
 * except where a defect-register ruling records an intended change. A compiling module proves
 * nothing about that; this test pins the actual HTTP surface:
 * <ul>
 *   <li>the six route prefixes, exactly as the monolith exposes them;</li>
 *   <li>which HTTP method each controller serves on each prefix;</li>
 *   <li>which authority each write demands - by <em>value</em>, since the authority strings are
 *       the client-observable contract, not the constant names;</li>
 *   <li>and, just as importantly, <strong>which reads are deliberately unauthenticated</strong>.
 *       All six read surfaces are anonymous in the monolith (BR-PRODUCT-025, BR-PRODCAT-014,
 *       BR-PRODLIST-025, BR-COMPANY-007) because the storefront depends on it. Adding a check
 *       would break clients, so an accidental addition must fail the build just as an accidental
 *       removal must.</li>
 * </ul>
 * Values below are transcribed from the monolith's controllers as of the Phase 1 port. Any change
 * to them is a contract change and needs a register ruling (invariant G1, G2).
 */
class CatalogueRouteParityTest {
    /** prefix -> "HTTP_METHOD" -> expected authority value, or null for a deliberately open route. */
    private static final Map<String, Map<String, String>> EXPECTED_SURFACE = new LinkedHashMap<>();

    static {
        EXPECTED_SURFACE.put("/data/products", Map.of(
            "GET", "", // deliberately unauthenticated
            "POST", "products:create",
            "PUT", "products:update",
            "PATCH", "products:update",
            "DELETE", "products:delete"));
        EXPECTED_SURFACE.put("/data/product_categories", Map.of(
            "GET", "",
            "POST", "product_categories:create",
            "PUT", "product_categories:update",
            "PATCH", "product_categories:update",
            "DELETE", "product_categories:delete"));
        EXPECTED_SURFACE.put("/data/product_lists", Map.of(
            "GET", "",
            "POST", "product_lists:create",
            "PUT", "product_lists:update",
            "PATCH", "product_lists:update",
            "DELETE", "product_lists:delete"));
        // Three writes, ONE authority - and no PATCH, because membership rows are immutable
        // (BR-PRODLIST-019, BR-PRODLIST-027).
        EXPECTED_SURFACE.put("/data/product_list_contents", Map.of(
            "GET", "",
            "POST", "product_lists:contents",
            "PUT", "product_lists:contents",
            "DELETE", "product_lists:contents"));
        // The image registry is the ONE exception in the catalogue: it is admin-facing, carries a
        // class-level @PreAuthorize("isAuthenticated()"), and is the only domain whose `:read`
        // authority is actually enforced.
        EXPECTED_SURFACE.put("/data/images", Map.of(
            "GET", "images:read",
            "POST", "images:create",
            "PUT", "images:update",
            "PATCH", "images:update",
            "DELETE", "images:delete"));
        EXPECTED_SURFACE.put("/public/about", Map.of("GET", ""));
    }

    private static final Class<?>[] CONTROLLERS = {
        DataProductsController.class,
        DataProductCategoriesController.class,
        DataProductListsController.class,
        DataProductListContentsController.class,
        DataImagesController.class,
        PublicAboutController.class
    };

    @Test
    void the_service_exposes_exactly_the_six_catalogue_route_prefixes() {
        Map<String, Class<?>> actual = new TreeMap<>();
        for (Class<?> controller : CONTROLLERS) {
            RequestMapping mapping = controller.getAnnotation(RequestMapping.class);
            assertThat(mapping).as("%s must declare a class-level request mapping",
                controller.getSimpleName()).isNotNull();
            assertThat(mapping.value()).hasSize(1);
            actual.put(mapping.value()[0], controller);
        }
        assertThat(actual.keySet())
            .as("the catalogue route surface must match the monolith's exactly")
            .containsExactlyInAnyOrderElementsOf(EXPECTED_SURFACE.keySet());
    }

    @Test
    void every_route_serves_the_same_methods_with_the_same_authorities_as_the_monolith() {
        for (Class<?> controller : CONTROLLERS) {
            String prefix = controller.getAnnotation(RequestMapping.class).value()[0];
            Map<String, String> expected = EXPECTED_SURFACE.get(prefix);
            Map<String, String> actual = new TreeMap<>();
            for (Method method : controller.getDeclaredMethods()) {
                String httpMethod = httpMethodOf(method);
                if (httpMethod == null) {
                    continue;
                }
                actual.put(httpMethod, authorityOf(method));
            }
            assertThat(actual)
                .as("%s (%s) must serve the same methods with the same authorities", prefix,
                    controller.getSimpleName())
                .containsExactlyInAnyOrderEntriesOf(expected);
        }
    }

    /**
     * Separated from the test above so that an accidentally added authority check on a read route
     * fails with an unambiguous message. These five reads are anonymous by design; the image
     * registry read is deliberately excluded, being the one enforced read in the catalogue.
     */
    @Test
    void the_five_storefront_read_routes_remain_deliberately_unauthenticated() {
        for (Class<?> controller : CONTROLLERS) {
            if (controller.equals(DataImagesController.class)) {
                continue;
            }
            for (Method method : controller.getDeclaredMethods()) {
                if (!"GET".equals(httpMethodOf(method))) {
                    continue;
                }
                assertThat(method.getAnnotation(PreAuthorize.class))
                    .as("%s.%s is anonymous in the monolith and the storefront depends on that; "
                            + "adding an authority check here is a contract change and needs a "
                            + "defect-register ruling",
                        controller.getSimpleName(), method.getName())
                    .isNull();
            }
        }
    }

    /**
     * The image registry's class-level guard. Pinned explicitly because it is the only
     * controller-wide authentication requirement in the catalogue, and because losing it would
     * silently open five admin endpoints to anonymous callers.
     */
    @Test
    void the_image_registry_keeps_its_class_level_authentication_requirement() {
        PreAuthorize classLevel = DataImagesController.class.getAnnotation(PreAuthorize.class);
        assertThat(classLevel)
            .as("DataImagesController must keep its class-level authentication requirement")
            .isNotNull();
        assertThat(classLevel.value()).isEqualTo("isAuthenticated()");

        for (Class<?> controller : CONTROLLERS) {
            if (controller.equals(DataImagesController.class)) {
                continue;
            }
            assertThat(controller.getAnnotation(PreAuthorize.class))
                .as("%s has no class-level guard in the monolith; adding one would change the "
                        + "reachability of its read route", controller.getSimpleName())
                .isNull();
        }
    }

    private static String httpMethodOf(Method method) {
        for (Annotation annotation : method.getAnnotations()) {
            Class<?> type = annotation.annotationType();
            if (type.equals(GetMapping.class)) {
                return "GET";
            } else if (type.equals(PostMapping.class)) {
                return "POST";
            } else if (type.equals(PutMapping.class)) {
                return "PUT";
            } else if (type.equals(PatchMapping.class)) {
                return "PATCH";
            } else if (type.equals(DeleteMapping.class)) {
                return "DELETE";
            }
        }
        return null;
    }

    /** The authority value demanded by the method, or the empty string when the route is open. */
    private static String authorityOf(Method method) {
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        if (annotation == null) {
            return "";
        }
        String expression = annotation.value();
        int start = expression.indexOf('\'');
        int end = expression.lastIndexOf('\'');
        assertThat(start).as("unparseable authority expression on %s: %s", method, expression)
            .isNotNegative();
        return expression.substring(start + 1, end);
    }
}
