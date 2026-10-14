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

package org.trebol.jpa.services.predicates.impl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImagesPredicateServiceImplTest {
    final ImagesPredicateServiceImpl instance = new ImagesPredicateServiceImpl();

    @Test
    void parses_map() {
        Predicate emptyPredicate = new BooleanBuilder();
        List<Predicate> predicates = List.of(emptyPredicate,
            instance.parseMap(Map.of("id", "1")),
            instance.parseMap(Map.of("code", "code test")),
            instance.parseMap(Map.of("filename", "filename test")),
            instance.parseMap(Map.of("codeLike", "code portion")),
            instance.parseMap(Map.of("filenameLike", "filename portion")));
        Set<Predicate> distinctPredicates = new HashSet<>(predicates);
        assertEquals(predicates.size(), distinctPredicates.size());
    }

    /**
     * Evidence for register item D1: an unrecognised parameter is ignored and the builder is
     * returned carrying no condition at all - which QueryDSL applies as "match everything".
     * {@code DataCrudGenericController.delete} is the single point that now rejects this.
     */
    @Test
    void yields_a_value_less_builder_when_no_param_is_recognized() {
        Predicate result = instance.parseMap(Map.of("mistyped", "whatever"));

        assertInstanceOf(BooleanBuilder.class, result);
        assertFalse(((BooleanBuilder) result).hasValue());
    }

    @Test
    void yields_a_builder_with_a_value_when_a_param_is_recognized() {
        Predicate result = instance.parseMap(Map.of("codeLike", "code portion"));

        assertInstanceOf(BooleanBuilder.class, result);
        assertTrue(((BooleanBuilder) result).hasValue());
    }
}
