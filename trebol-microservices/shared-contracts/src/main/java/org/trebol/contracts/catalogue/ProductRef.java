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

/**
 * A product as seen across a service boundary - the minimum orders-service needs to build
 * an order line.
 * <p>
 * Deliberately narrower than the catalogue's own product model: it carries no category, no
 * images, no description beyond the name, and no identifier. Barcode is the cross-service
 * key, mirroring the monolith, where the order converter resolved products by barcode and
 * never by surrogate key.
 *
 * @param barcode      the product barcode. The cross-service identity key.
 * @param name         the product name. The caller uses this to generate the order line
 *                     description.
 * @param unitValue    the catalogue price. <b>Authoritative</b> - the caller overwrites any
 *                     client-submitted unit value with this. Tax-inclusive: the order
 *                     totals calculation extracts tax from it rather than adding tax to it.
 * @param currentStock informational only. The monolith performed <b>no</b> stock check
 *                     during checkout; adding one is a behavioural change requiring a
 *                     ruling.
 */
public record ProductRef(
    String barcode,
    String name,
    int unitValue,
    int currentStock
) {
}
