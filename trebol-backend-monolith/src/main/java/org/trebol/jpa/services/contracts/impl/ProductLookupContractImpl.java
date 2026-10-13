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

package org.trebol.jpa.services.contracts.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.trebol.jpa.entities.Product;
import org.trebol.jpa.repositories.ProductsRepository;
import org.trebol.jpa.services.contracts.ProductLookupContract;

import java.util.Optional;

/**
 * The <code>product</code> domain's side of {@link ProductLookupContract}.
 * Owns the repository access; consumers in other domains do not.
 */
@Service
@Transactional(readOnly = true)
public class ProductLookupContractImpl
    implements ProductLookupContract {
    private final ProductsRepository productsRepository;

    @Autowired
    public ProductLookupContractImpl(
        ProductsRepository productsRepository
    ) {
        this.productsRepository = productsRepository;
    }

    @Override
    public Optional<Product> findProductByBarcode(String barcode) {
        return productsRepository.findByBarcode(barcode);
    }
}
