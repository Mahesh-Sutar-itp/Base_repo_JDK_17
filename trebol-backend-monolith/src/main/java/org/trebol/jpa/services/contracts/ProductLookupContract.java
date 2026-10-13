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

package org.trebol.jpa.services.contracts;

import org.trebol.jpa.entities.Product;

import java.util.Optional;

/**
 * Published lookup contract owned by the <code>product</code> domain (future Catalogue service).
 * <p>
 * Gate A of the microservices conversion: consumers outside the <code>product</code> domain -
 * notably the checkout path, which resolves a product <b>per order line</b> - must come through
 * this contract instead of reaching into {@code ProductsRepository} directly.
 * <p>
 * The contract deliberately returns the {@link Product} entity in this phase. {@code Order} holds
 * a real {@code @ManyToOne} association to it, so returning a detached reference would require
 * changing the ORM mapping and the schema - that is extraction work (Phase 1/3), not gate work.
 * What the gate buys is the seam: one owner, one call site to change.
 */
public interface ProductLookupContract {
    /**
     * @param barcode the product barcode, as submitted on an order line
     * @return the matching product, or empty if no product carries that barcode
     */
    Optional<Product> findProductByBarcode(String barcode);
}
