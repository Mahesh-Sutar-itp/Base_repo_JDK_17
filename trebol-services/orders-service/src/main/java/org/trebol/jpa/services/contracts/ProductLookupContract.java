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

import java.util.Optional;

/**
 * Resolves a product from the Catalogue service.
 * <p>
 * <b>Phase 3 cut C1 and C2.</b> In the monolith this contract returned the
 * {@code org.trebol.jpa.entities.Product} <b>entity</b>, and its javadoc said so explicitly:
 * returning a detached reference would have required changing the ORM mapping and the schema,
 * which was extraction work rather than gate work. This <em>is</em> that extraction work, so the
 * entity is gone and a snapshot takes its place.
 * <p>
 * <b>One method, reached from one place.</b> Only the checkout path needs the Catalogue at all: it
 * resolves a submitted barcode to get the price and name it copies onto the order line. The
 * <b>receipt path makes no call</b> - the line stores the barcode itself, which is what makes cut
 * C2 a removal rather than a replacement.
 * <p>
 * Nothing in this service needs a product's images, categories, stock or description.
 *
 * @see ProductSnapshot
 */
public interface ProductLookupContract {
    /**
     * @param barcode the product barcode, as submitted on an order line
     * @return a snapshot of the matching product, or empty if no product carries that barcode
     */
    Optional<ProductSnapshot> findProductByBarcode(String barcode);
}
