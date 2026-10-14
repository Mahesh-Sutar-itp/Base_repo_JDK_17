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
import org.trebol.api.controllers.AccessController;
import org.trebol.api.controllers.AccountProfileController;
import org.trebol.api.controllers.DataCustomersController;
import org.trebol.api.controllers.DataPeopleController;
import org.trebol.api.controllers.DataSalespeopleController;
import org.trebol.api.controllers.DataUserRolesController;
import org.trebol.api.controllers.DataUsersController;
import org.trebol.api.controllers.PublicRegisterController;
import org.trebol.config.SecurityConfig;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Route parity between this service and the monolith it was extracted from.
 * <p>
 * Exit criterion X3 requires that every route continues to be served and to behave identically
 * except where a defect-register ruling records an intended change. A compiling module proves
 * nothing about that; this test pins the actual HTTP surface.
 * <p>
 * Identity's surface is harder to pin than the catalogue's, because three of its route groups are
 * guarded in three <em>different</em> ways and two of its endpoints are not controller mappings at
 * all:
 * <ul>
 *   <li>{@code /data/users}, {@code /data/user_roles}, {@code /data/customers},
 *       {@code /data/salespeople} and {@code /data/people} carry <b>two</b> layers - a class-level
 *       {@code isAuthenticated()} and a per-method {@code hasAuthority(...)};</li>
 *   <li>{@code /account/profile} and {@code /access} carry the class-level layer <b>only</b>, with
 *       no authority check at all (BR-USER-050, BR-USER-034). Requiring an authority on the profile
 *       routes would break self-service for ordinary customers, who hold only {@code checkout};</li>
 *   <li>{@code /public/register} is <b>fully anonymous</b> - no annotation anywhere on the class.
 *       Accidentally securing it would silently break every anonymous client;</li>
 *   <li>{@code /public/login} and {@code /public/guest} are <b>filter-processed URLs wired in
 *       SecurityConfig</b>, not controller mappings (BR-USER-014). The definition requires them to
 *       be ported as filters, so this test asserts both that no controller claims those paths and
 *       that {@code SecurityConfig} still names them.</li>
 * </ul>
 * Authorities are asserted by <b>value</b>, not by constant name, because the strings are the
 * client-observable contract (G2). Values are transcribed from the monolith's controllers as of the
 * Phase 2 port; any change to them is a contract change needing a register ruling (G1).
 */
class IdentityRouteParityTest {
    /** prefix -&gt; "HTTP_METHOD" -&gt; expected authority value, or "" for a deliberately open route. */
    private static final Map<String, Map<String, String>> EXPECTED_SURFACE = new LinkedHashMap<>();

    static {
        // Four writes plus an enforced read. DELETE additionally carries the self-delete guard,
        // which is application logic rather than an annotation and is covered by
        // DataUsersControllerTest.
        EXPECTED_SURFACE.put("/data/users", Map.of(
            "GET", "users:read",
            "POST", "users:create",
            "PUT", "users:update",
            "DELETE", "users:delete"));
        EXPECTED_SURFACE.put("/data/user_roles", Map.of(
            "GET", "user_roles:read",
            "POST", "user_roles:create",
            "PUT", "user_roles:update",
            "DELETE", "user_roles:delete"));
        EXPECTED_SURFACE.put("/data/customers", Map.of(
            "GET", "customers:read",
            "POST", "customers:create",
            "PUT", "customers:update",
            "DELETE", "customers:delete"));
        EXPECTED_SURFACE.put("/data/salespeople", Map.of(
            "GET", "salespeople:read",
            "POST", "salespeople:create",
            "PUT", "salespeople:update",
            "DELETE", "salespeople:delete"));
        // Read-only in the monolith: there is no route that writes a person directly, because
        // people are created through registration, customers or salespeople.
        EXPECTED_SURFACE.put("/data/people", Map.of(
            "GET", "people:read"));
        // isAuthenticated() only - no authority. The identity comes from the Principal, never from
        // the request, so there is no horizontal privilege path (BR-USER-050).
        EXPECTED_SURFACE.put("/account/profile", Map.of(
            "GET", "",
            "PUT", ""));
        // Fully anonymous (no class-level annotation either).
        EXPECTED_SURFACE.put("/public/register", Map.of(
            "POST", ""));
    }

    private static final Class<?>[] CONTROLLERS = {
        DataUsersController.class,
        DataUserRolesController.class,
        DataCustomersController.class,
        DataSalespeopleController.class,
        DataPeopleController.class,
        AccountProfileController.class,
        PublicRegisterController.class
    };

    /** Guarded by a class-level {@code isAuthenticated()} in the monolith. */
    private static final Class<?>[] AUTHENTICATED_CLASSES = {
        DataUsersController.class,
        DataUserRolesController.class,
        DataCustomersController.class,
        DataSalespeopleController.class,
        DataPeopleController.class,
        AccountProfileController.class,
        AccessController.class
    };

    @Test
    void the_service_exposes_exactly_the_identity_route_prefixes() {
        Map<String, Class<?>> actual = new TreeMap<>();
        for (Class<?> controller : CONTROLLERS) {
            RequestMapping mapping = controller.getAnnotation(RequestMapping.class);
            assertThat(mapping).as("%s must declare a class-level request mapping",
                controller.getSimpleName()).isNotNull();
            assertThat(mapping.value()).hasSize(1);
            actual.put(mapping.value()[0], controller);
        }
        assertThat(actual.keySet())
            .as("the identity route surface must match the monolith's exactly")
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
     * The class-level guard is a separate layer from the per-method authorities, and losing it
     * would change an anonymous request's response from 401 to 403 while also removing the safety
     * net that keeps an un-annotated method on these classes non-public. That net matters here more
     * than anywhere, because {@code SecurityConfig} declares no request-matcher rules at all
     * (BR-USER-025), so an unguarded method really is reachable by anyone.
     */
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
     * Separated so that an accidental addition fails with an unambiguous message.
     * Self-registration is anonymous in the monolith and every unauthenticated client depends on
     * that.
     */
    @Test
    void self_registration_remains_fully_anonymous() {
        assertThat(PublicRegisterController.class.getAnnotation(PreAuthorize.class))
            .as("PublicRegisterController has no class-level guard in the monolith; adding one "
                + "would break anonymous self-registration")
            .isNull();
        for (Method method : PublicRegisterController.class.getDeclaredMethods()) {
            if (httpMethodOf(method) == null) {
                continue;
            }
            assertThat(method.getAnnotation(PreAuthorize.class))
                .as("PublicRegisterController.%s must stay unsecured", method.getName())
                .isNull();
        }
    }

    /**
     * {@code /account/profile} requires authentication and nothing more (BR-USER-050). Pinned on
     * its own because "tightening" it to {@code users:update} is an easy and plausible mistake that
     * would break self-service for every ordinary customer - they hold only {@code checkout}.
     */
    @Test
    void the_profile_routes_require_authentication_and_no_authority() {
        for (Method method : AccountProfileController.class.getDeclaredMethods()) {
            if (httpMethodOf(method) == null) {
                continue;
            }
            assertThat(method.getAnnotation(PreAuthorize.class))
                .as("AccountProfileController.%s is authenticated-only in the monolith; demanding "
                    + "an authority here is a contract change", method.getName())
                .isNull();
        }
    }

    /**
     * Login and guest access are <b>filters</b>, not controller routes (BR-USER-014). Two things
     * are asserted: that no controller has quietly claimed those paths during the port, and that
     * {@code SecurityConfig} still wires them as filter-processed URLs.
     */
    @Test
    void login_and_guest_are_filter_processed_urls_and_not_controller_mappings() throws Exception {
        for (Class<?> controller : CONTROLLERS) {
            String prefix = controller.getAnnotation(RequestMapping.class).value()[0];
            assertThat(prefix)
                .as("%s must not map a filter-processed URL", controller.getSimpleName())
                .isNotEqualTo("/public/login")
                .isNotEqualTo("/public/guest");
        }

        Path securityConfig = Paths.get("src", "main", "java", "org", "trebol", "config",
            "SecurityConfig.java");
        String source = Files.readString(securityConfig, StandardCharsets.UTF_8);
        assertThat(source)
            .as("SecurityConfig must wire the login filter on /public/login")
            .contains("loginFilterForUrl(\"/public/login\")");
        assertThat(source)
            .as("SecurityConfig must wire the guest filter on /public/guest")
            .contains("guestFilterForUrl(\"/public/guest\")");
        assertThat(SecurityConfig.class.getDeclaredMethod("filterChain",
            org.springframework.security.config.annotation.web.builders.HttpSecurity.class))
            .as("SecurityConfig must still declare the filter chain bean")
            .isNotNull();
    }

    /**
     * There is no password change or reset anywhere in the baseline - register item D15, ruled
     * PRESERVE and out of scope. Inventing the route during an extraction would be new product
     * behaviour, so the absence is pinned.
     */
    @Test
    void no_password_route_is_invented() {
        for (Class<?> controller : CONTROLLERS) {
            String prefix = controller.getAnnotation(RequestMapping.class).value()[0];
            assertThat(prefix).as("no password route exists in the baseline (register item D15)")
                .doesNotContain("password");
            for (Method method : controller.getDeclaredMethods()) {
                assertThat(method.getName().toLowerCase())
                    .as("%s.%s suggests a password route; D15 rules that out of scope",
                        controller.getSimpleName(), method.getName())
                    .doesNotContain("password");
            }
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

    /** The authority value demanded by the method, or the empty string when no authority is demanded. */
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
