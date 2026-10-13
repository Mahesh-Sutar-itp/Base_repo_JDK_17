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

package org.trebol.contracts.catalogue;

import java.util.Optional;

/**
 * Cross-service lookup contract: resolve a product by its barcode.
 * <p>
 * This is one of the two genuinely cross-service lookups required by Gate A of the
 * microservices conversion. It is <b>owned and implemented by catalogue-service</b> and
 * <b>consumed by orders-service</b> during checkout.
 * <p>
 * In the monolith, {@code OrdersConverterServiceImpl} called
 * {@code ProductsRepository.findByBarcode} directly - a compile-time dependency on another
 * domain's persistence interface, exercised once per order line in the checkout critical
 * path. That is what this contract replaces.
 *
 * <h2>Behaviour that must be preserved</h2>
 * <ul>
 *   <li>Resolution is by barcode, matched exactly.</li>
 *   <li>A blank barcode is a client error: the monolith raised
 *       {@code "Product barcode must be valid"}.</li>
 *   <li>An unresolvable barcode is a client error: the monolith raised
 *       {@code "Unexisting product in sell details"}. Callers must not silently skip
 *       the line.</li>
 *   <li>The caller <b>overwrites</b> the submitted unit value with {@link ProductRef#unitValue()}
 *       - the catalogue price is authoritative, never the price the client sent.</li>
 *   <li>No stock check occurs. {@link ProductRef#currentStock()} is informational only;
 *       the monolith performed no stock validation at any point during checkout, and
 *       introducing one is a behavioural change requiring a ruling.</li>
 * </ul>
 */
public interface ProductLookup {

    /**
     * Resolve a single product by barcode.
     *
     * @param barcode the product barcode, matched exactly. Must not be blank.
     * @return the product if it exists, otherwise empty. Never null.
     * @throws IllegalArgumentException if {@code barcode} is null or blank - the caller is
     *                                  expected to translate this into the client-facing
     *                                  {@code "Product barcode must be valid"} error.
     */
    Optional<ProductRef> findByBarcode(String barcode);
}
