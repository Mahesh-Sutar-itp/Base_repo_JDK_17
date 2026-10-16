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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The service-side half of an assertion that Phase 4 had to split.
 * <p>
 * {@code Constants.AUTHORITY_CHECKOUT} is the one authority that was already a named constant
 * before Gate B; Phase 0 left it in place as a delegating alias rather than deleting it, because
 * it is referenced by three of this service's security tests. Its value is embedded in every
 * issued token, so it is a client-observable contract.
 * <p>
 * Until Phase 4 this was asserted by {@code AuthorityCatalogueIntegrityTest} in the monolith's
 * test tree, which could see both {@code Constants} and {@code AuthorityCodes}. That test moved
 * into {@code shared-contracts} when the monolith was decommissioned, and
 * {@code shared-contracts} is deliberately dependency-free - it cannot see {@code Constants},
 * which is per-service application code. The half that pins the catalogue's literal value stayed
 * there; this is the half that pins the alias still delegating to it.
 * <p>
 * It lives in identity-service specifically because this service <b>issues</b> the tokens the
 * value is embedded in. The equivalent alias also exists in catalogue-service and orders-service,
 * but those only verify tokens, so a drift there could not mint a token with a wrong authority.
 * <p>
 * Without this test the alias could be repointed at a different code, or hard-coded back to a
 * literal, and every existing test would still pass - because they all read the same constant.
 * That is precisely the drift Gate B exists to prevent.
 */
class AuthorityCheckoutAliasTest {
    @Test
    void the_checkout_alias_still_delegates_to_the_generated_catalogue() {
        assertThat(Constants.AUTHORITY_CHECKOUT)
            .as("the alias must resolve to the generated catalogue entry, never to its own literal")
            .isEqualTo(AuthorityCodes.CHECKOUT);
    }

    @Test
    void the_checkout_authority_keeps_its_exact_token_value() {
        assertThat(Constants.AUTHORITY_CHECKOUT)
            .as("this string is embedded in issued tokens; changing it invalidates live tokens")
            .isEqualTo("checkout");
    }
}
