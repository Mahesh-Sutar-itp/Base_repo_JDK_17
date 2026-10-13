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

package org.trebol.contracts.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards Gate B: the authority catalogue must match the monolith's seed data exactly.
 * A code that silently changes value disables an access check without any compiler error -
 * which is the defect this catalogue exists to prevent.
 */
class AuthorityCodesTest {

    /**
     * The monolith seeds 53 codes following the {@code resource:action} convention plus the
     * standalone {@code checkout}, extracted from {@code src/main/resources/data.sql}.
     */
    @Test
    void catalogue_holds_exactly_the_seeded_codes() {
        assertEquals(54, AuthorityCodes.ALL.size());
    }

    @Test
    void every_code_except_checkout_follows_the_resource_action_convention() {
        long conventional = AuthorityCodes.ALL.stream()
            .filter(code -> code.matches("^[a-z_]+:[a-z_]+$"))
            .count();
        assertEquals(53, conventional);
        assertTrue(AuthorityCodes.ALL.contains(AuthorityCodes.CHECKOUT));
        assertFalse(AuthorityCodes.CHECKOUT.contains(":"));
    }

    @Test
    void recognises_known_codes() {
        assertTrue(AuthorityCodes.isKnown("people:read"));
        assertTrue(AuthorityCodes.isKnown("product_lists:contents"));
        assertTrue(AuthorityCodes.isKnown("checkout"));
    }

    @Test
    void rejects_unknown_codes() {
        assertFalse(AuthorityCodes.isKnown("people:reed"));
        assertFalse(AuthorityCodes.isKnown("images:purge"));
        assertFalse(AuthorityCodes.isKnown(""));
    }

    /**
     * Literal values are the exact strings checked at runtime. Pinning a sample of them
     * makes an accidental rename a test failure rather than a silent security regression.
     */
    @Test
    void literal_values_are_pinned() {
        assertEquals("people:read", AuthorityCodes.PEOPLE_READ);
        assertEquals("images:delete", AuthorityCodes.IMAGES_DELETE);
        assertEquals("orders:update", AuthorityCodes.ORDERS_UPDATE);
        assertEquals("product_lists:contents", AuthorityCodes.PRODUCT_LISTS_CONTENTS);
        assertEquals("checkout", AuthorityCodes.CHECKOUT);
    }
}
