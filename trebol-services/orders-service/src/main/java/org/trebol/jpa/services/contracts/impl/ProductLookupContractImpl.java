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

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.trebol.api.models.DataPagePojo;
import org.trebol.api.models.ProductPojo;
import org.trebol.integration.RemoteLookupSupport;
import org.trebol.jpa.services.contracts.ProductLookupContract;
import org.trebol.jpa.services.contracts.ProductSnapshot;

import java.util.Map;
import java.util.Optional;

/**
 * Reads products from the Catalogue service over HTTP.
 * <p>
 * <b>Phase 3 cuts C1 and C2.</b> Replaces the in-process implementation that returned the
 * {@code Product} entity straight from {@code ProductsRepository}.
 * <p>
 * Both routes used here - {@code GET /data/products?barcode=} and {@code ?id=} - are
 * <b>anonymous storefront reads</b> in the Catalogue service, which is what makes this hop work for
 * an unauthenticated guest checkout without any authority at all. The caller's token is still
 * relayed when there is one, so an authenticated caller is not silently downgraded.
 */
@Service
public class ProductLookupContractImpl
    implements ProductLookupContract {
    private static final ParameterizedTypeReference<DataPagePojo<ProductPojo>> PAGE_OF_PRODUCTS =
        new ParameterizedTypeReference<>() {
        };
    private final RestClient catalogueRestClient;

    public ProductLookupContractImpl(@Qualifier("catalogueRestClient") RestClient catalogueRestClient) {
        this.catalogueRestClient = catalogueRestClient;
    }

    @Override
    public Optional<ProductSnapshot> findProductByBarcode(String barcode) {
        if (barcode==null || barcode.isBlank()) {
            return Optional.empty();
        }
        return this.lookup(Map.of("barcode", barcode));
    }


    private Optional<ProductSnapshot> lookup(Map<String, String> filters) {
        return RemoteLookupSupport
            .findOne(catalogueRestClient, "Catalogue", "/data/products", filters, PAGE_OF_PRODUCTS)
            .map(product -> new ProductSnapshot(
                product.getBarcode(),
                product.getName(),
                product.getPrice()==null ? 0:product.getPrice()));
    }
}
