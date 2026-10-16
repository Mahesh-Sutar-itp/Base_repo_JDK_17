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

package org.trebol.api;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import org.trebol.api.services.PaginationService;
import org.trebol.common.exceptions.BadInputException;
import org.trebol.jpa.services.CrudService;
import org.trebol.jpa.services.PredicateService;
import org.trebol.jpa.services.SortSpecParserService;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import java.util.Map;

/**
 * Base class that implements {@link DataCrudController}.<br/>
 * Thus, it implements all four CRUD operations for a type of data, and its method are named as such.
 *
 * @param <M> Its signature model class
 * @param <E> Its signature entity class
 */
public abstract class DataCrudGenericController<M, E>
    extends DataGenericController<M, E>
    implements DataCrudController<M> {
    /**
     * Rejection message for a request whose filtering parameters contain no recognised selector.
     * See register item D1.
     */
    public static final String NO_RECOGNIZED_FILTERING_PARAMS = "No recognized filtering parameters were provided";

    protected DataCrudGenericController(
        PaginationService paginationService,
        SortSpecParserService sortSpecParserService,
        CrudService<M, E> crudService,
        PredicateService predicateService
    ) {
        super(paginationService, sortSpecParserService, crudService, predicateService);
    }

    @Override
    public void update(@Valid M input, Map<String, String> requestParams) throws BadInputException, EntityNotFoundException {
        if (requestParams.isEmpty()) {
            throw new BadInputException("Missing request params");
        }
        Predicate predicate = predicateService.parseMap(requestParams);
        crudService.update(input, predicate)
            .orElseThrow(() -> new EntityNotFoundException("No element was found to update"));
    }

    @Override
    public void partialUpdate(Map<String, Object> input, Map<String, String> requestParams) throws BadInputException, EntityNotFoundException {
        if (requestParams.isEmpty()) {
            throw new BadInputException("Missing request params");
        }
        Predicate predicate = predicateService.parseMap(requestParams);
        crudService.partialUpdate(input, predicate)
            .orElseThrow(() -> new EntityNotFoundException("No element was found to update"));
    }

    @Override
    public void delete(Map<String, String> requestParams) throws BadInputException, EntityNotFoundException {
        if (!requestParams.isEmpty()) {
            Predicate predicate = predicateService.parseMap(requestParams);
            if (predicate instanceof BooleanBuilder && !((BooleanBuilder) predicate).hasValue()) {
                // Register item D1: every PredicateService implementation ignores an unrecognised
                // parameter and returns the BooleanBuilder it built. With no recognised selector that
                // builder carries no condition, which QueryDSL treats as "match everything" - so a
                // single mistyped query parameter used to delete the whole table. Reject instead.
                throw new BadInputException(NO_RECOGNIZED_FILTERING_PARAMS);
            }
            crudService.delete(predicate);
        }
    }
}
