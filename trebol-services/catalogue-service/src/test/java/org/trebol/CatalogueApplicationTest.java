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

package org.trebol;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.trebol.api.controllers.DataImagesController;
import org.trebol.api.controllers.DataProductCategoriesController;
import org.trebol.api.controllers.DataProductListContentsController;
import org.trebol.api.controllers.DataProductListsController;
import org.trebol.api.controllers.DataProductsController;
import org.trebol.api.controllers.PublicAboutController;
import org.trebol.jpa.services.contracts.ProductLookupContract;

import java.util.List;

/**
 * Context-load test for the Catalogue service, modelled on the monolith's {@code BackendAppTest}.
 * <p>
 * It proves three things that a compiling module does not: that the Spring context actually starts,
 * that every ported controller is wired, and that the <strong>published</strong> Gate A contract
 * bean exists - the latter being what the Orders service will depend on from Phase 3 onward.
 * <p>
 * The identity-side beans the monolith wires here are deliberately absent: this service issues no
 * tokens, owns no user tables and has no login or guest route.
 */
@SpringBootTest
class CatalogueApplicationTest {
    @Autowired DataImagesController imagesController;
    @Autowired DataProductCategoriesController productCategoriesController;
    @Autowired DataProductListContentsController productListContentsController;
    @Autowired DataProductListsController productListsController;
    @Autowired DataProductsController productsController;
    @Autowired PublicAboutController aboutController;
    @Autowired ProductLookupContract productLookupContract;

    @Test
    void contextLoads() {
        List.of(
            imagesController,
            productCategoriesController,
            productListContentsController,
            productListsController,
            productsController,
            aboutController,
            productLookupContract
        ).forEach(Assertions::assertNotNull);
    }
}
