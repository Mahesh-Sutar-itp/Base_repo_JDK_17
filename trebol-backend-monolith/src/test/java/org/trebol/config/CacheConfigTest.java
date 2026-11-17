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

package org.trebol.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The cache condition decides correctness: a lookup of one specific record must never be served
 * from cache, or an admin could read back stale data right after changing it.
 */
class CacheConfigTest {
    final Expression condition = new SpelExpressionParser().parseExpression(CacheConfig.NOT_AN_IDENTITY_LOOKUP);

    /** Stand-in for Spring's cache root object, which exposes the method arguments as "args". */
    public static class Root {
        private final Object[] args;
        Root(Object... args) { this.args = args; }
        public Object[] getArgs() { return args; }
    }

    private boolean cacheable(Map<String, String> requestParams) {
        return Boolean.TRUE.equals(condition.getValue(new Root(requestParams), Boolean.class));
    }

    @Test
    void list_and_search_pages_are_cached() {
        assertEquals(true, cacheable(Map.of()));
        assertEquals(true, cacheable(Map.of("pageIndex", "1", "pageSize", "20")));
        assertEquals(true, cacheable(Map.of("nameLike", "lap")));
        assertEquals(true, cacheable(Map.of("categoryCode", "electronics")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "barcode", "name", "code"})
    void lookups_of_one_record_are_never_cached(String identityParam) {
        assertEquals(false, cacheable(Map.of(identityParam, "x")));
        assertEquals(false, cacheable(Map.of(identityParam, "x", "pageSize", "10")));
    }
}
