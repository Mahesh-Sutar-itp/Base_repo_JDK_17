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
import org.trebol.security.authorities.AuthorityCodes;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gate B enforcement, second half: the catalogue must actually be <em>consumed</em>.
 * <p>
 * Generating the constants is not enough - 53 of the 54 authority codes previously existed only
 * as duplicated string literals, so a rename silently disabled an access check. These tests fail
 * the build if a raw literal reappears anywhere in main source, and if any authority actually
 * demanded at runtime is not a known catalogue code.
 */
class NoAuthorityLiteralsArchitectureTest {
    private static final Path MAIN_SOURCE_ROOT = Paths.get("src", "main", "java");
    /**
     * Matches a hard-coded code inside hasAuthority(...) / hasAnyAuthority(...). The quotation
     * mark is excluded from the captured code so that the compile-time concatenation form
     * {@code hasAuthority('" + CONSTANT + "')} is not mistaken for a literal.
     */
    private static final Pattern AUTHORITY_LITERAL = Pattern.compile(
        "has(?:Any)?Authority\\(\\s*'([^'\"]*)'\\s*\\)");
    /** Matches the authority demanded through the catalogue: hasAuthority('" + CONST + "') */
    private static final Pattern CATALOGUE_REFERENCE = Pattern.compile(
        "has(?:Any)?Authority\\('\"\\s*\\+\\s*([A-Z0-9_]+)\\s*\\+\\s*\"'\\)");

    private static List<Path> mainJavaSources() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN_SOURCE_ROOT)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    @Test
    void no_authority_string_literal_exists_anywhere_in_main_source() throws IOException {
        Map<String, List<String>> offenders = new LinkedHashMap<>();
        for (Path source : mainJavaSources()) {
            List<String> found = new ArrayList<>();
            Matcher matcher = AUTHORITY_LITERAL.matcher(Files.readString(source, StandardCharsets.UTF_8));
            while (matcher.find()) {
                found.add(matcher.group(1));
            }
            if (!found.isEmpty()) {
                offenders.put(source.toString(), found);
            }
        }
        assertThat(offenders)
            .as("authority codes must come from the generated catalogue "
                + "org.trebol.security.authorities.AuthorityCodes, never from a string literal")
            .isEmpty();
    }

    @Test
    void every_authority_demanded_in_main_source_is_a_known_catalogue_code() throws IOException {
        List<String> referenced = new ArrayList<>();
        for (Path source : mainJavaSources()) {
            Matcher matcher = CATALOGUE_REFERENCE.matcher(Files.readString(source, StandardCharsets.UTF_8));
            while (matcher.find()) {
                referenced.add(matcher.group(1));
            }
        }
        assertThat(referenced).as("every @PreAuthorize authority check must reference the catalogue")
            .isNotEmpty();
        for (String constantName : referenced) {
            assertThat(catalogueValueOf(constantName))
                .as("%s is referenced in an authority check but is not a catalogue code", constantName)
                .isNotNull()
                .matches(AuthorityCodes::exists);
        }
    }

    /**
     * Resolved reflectively against the live annotation values, so it catches a demanded authority
     * even if it were assembled in a way the source regexes above do not see.
     */
    @Test
    void every_PreAuthorize_authority_resolved_at_runtime_is_a_known_catalogue_code() throws Exception {
        List<String> checked = new ArrayList<>();
        for (Path source : mainJavaSources()) {
            String className = MAIN_SOURCE_ROOT.relativize(source).toString()
                .replace(".java", "").replace('/', '.').replace('\\', '.');
            Class<?> type;
            try {
                type = Class.forName(className, false, getClass().getClassLoader());
            } catch (Throwable ignoredNotLoadable) {
                continue;
            }
            for (Method method : type.getDeclaredMethods()) {
                PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
                if (annotation == null) {
                    continue;
                }
                Matcher matcher = AUTHORITY_LITERAL.matcher(annotation.value());
                while (matcher.find()) {
                    String authority = matcher.group(1);
                    checked.add(authority);
                    assertThat(AuthorityCodes.exists(authority))
                        .as("%s.%s demands authority '%s', which is not in the generated catalogue",
                            type.getSimpleName(), method.getName(), authority)
                        .isTrue();
                }
            }
        }
        // Count floor for THIS module, not the monolith's 51. The Identity service declares exactly
        // 17 method-level authority checks: four each on /data/users, /data/user_roles,
        // /data/customers and /data/salespeople, plus the single PEOPLE_READ on /data/people.
        // AccessController, AccountProfileController and PublicRegisterController declare none by
        // design - the first two are isAuthenticated()-only (BR-USER-050) and the third is anonymous.
        // The floor exists so that silently losing a @PreAuthorize fails the build; it is the only
        // line of this test adapted for the port (G3 permits compatibility changes, not deletions).
        assertThat(checked).as("authority checks found by reflection").hasSizeGreaterThanOrEqualTo(17);
    }

    private static String catalogueValueOf(String constantName) {
        try {
            return (String) AuthorityCodes.class.getDeclaredField(constantName).get(null);
        } catch (NoSuchFieldException | IllegalAccessException exc) {
            return null;
        }
    }
}
