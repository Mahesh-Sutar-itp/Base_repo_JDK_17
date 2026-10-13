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

package org.trebol.security.authorities;

import org.junit.jupiter.api.Test;
import org.trebol.config.Constants;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.trebol.security.authorities.AuthorityCatalogueGenerator.GENERATED_FILE_PATH;
import static org.trebol.security.authorities.AuthorityCatalogueGenerator.SEED_DATA_PATH;

/**
 * Gate B of the microservices conversion.
 * <p>
 * The authority catalogue is a generated artefact whose single source of truth is the
 * database seed data. This test is the enforcement mechanism: it regenerates the
 * catalogue on every build and fails on any drift, so that an added, renamed or removed
 * authority code is a <strong>build failure</strong> instead of a silently disabled
 * access check.
 */
class AuthorityCatalogueIntegrityTest {
    private List<String> seededCodes() throws IOException {
        return AuthorityCatalogueGenerator.readSeededAuthorityCodes(SEED_DATA_PATH);
    }

    @Test
    void seed_data_is_reachable_from_the_build() {
        assertThat(SEED_DATA_PATH).as("authority catalogue source of truth").exists();
        assertThat(GENERATED_FILE_PATH).as("generated authority catalogue").exists();
    }

    @Test
    void committed_catalogue_matches_the_seed_data_exactly() throws IOException {
        String expected = AuthorityCatalogueGenerator.generateSource(seededCodes());
        String actual = Files.readString(GENERATED_FILE_PATH, StandardCharsets.UTF_8);
        assertThat(normalize(actual))
            .as("%s has drifted from %s - regenerate it by running AuthorityCatalogueGenerator.main",
                GENERATED_FILE_PATH, SEED_DATA_PATH)
            .isEqualTo(normalize(expected));
    }

    @Test
    void catalogue_declares_exactly_the_seeded_codes() throws IOException {
        List<String> codes = seededCodes();
        assertThat(AuthorityCodes.CATALOGUE_SIZE).isEqualTo(codes.size());
        assertThat(AuthorityCodes.ALL_CODES)
            .as("every seeded code must be in the catalogue, and no code may exist that is not seeded")
            .containsExactlyInAnyOrderElementsOf(codes);
    }

    @Test
    void catalogue_holds_the_54_codes_recorded_by_the_conversion_analysis() {
        assertThat(AuthorityCodes.ALL_CODES).hasSize(54);
        assertThat(AuthorityCodes.CATALOGUE_VERSION).isNotBlank();
        assertThat(AuthorityCodes.CATALOGUE_CHECKSUM).hasSize(64);
    }

    @Test
    void every_declared_constant_is_a_known_code() throws IllegalAccessException {
        List<String> declared = new ArrayList<>();
        for (Field field : AuthorityCodes.class.getDeclaredFields()) {
            if (field.getType().equals(String.class)
                && Modifier.isStatic(field.getModifiers())
                && !field.getName().startsWith("CATALOGUE_")) {
                declared.add((String) field.get(null));
            }
        }
        assertThat(declared).hasSize(AuthorityCodes.CATALOGUE_SIZE);
        assertThat(declared).allMatch(AuthorityCodes::exists);
    }

    @Test
    void unknown_codes_are_rejected() {
        assertThat(AuthorityCodes.exists("products:read")).isTrue();
        assertThat(AuthorityCodes.exists("products:reed")).isFalse();
        assertThat(AuthorityCodes.exists("")).isFalse();
    }

    /**
     * The one authority that was already a constant before Gate B. It must keep its exact
     * value, because it is embedded in issued tokens and asserted by three security tests.
     */
    @Test
    void the_pre_existing_checkout_constant_still_resolves_to_the_catalogue_value() {
        assertThat(Constants.AUTHORITY_CHECKOUT).isEqualTo("checkout");
        assertThat(Constants.AUTHORITY_CHECKOUT).isEqualTo(AuthorityCodes.CHECKOUT);
    }

    private static String normalize(String source) {
        return source.replace("\r\n", "\n");
    }
}
