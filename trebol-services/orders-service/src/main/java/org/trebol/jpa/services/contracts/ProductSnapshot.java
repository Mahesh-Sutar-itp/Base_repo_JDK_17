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

/**
 * The three product fields this service needs, and no others.
 * <p>
 * <b>Phase 3 cut C1/C2.</b> Replaces the {@code Product} entity that
 * {@link ProductLookupContract} used to return. A record rather than a class: it is a value, it is
 * immutable, and it crosses a service boundary - all three of which argue against a mutable
 * JavaBean.
 * <p>
 * <b>There is no {@code id} field, and that is a finding rather than an omission.</b> The
 * Catalogue's public API exposes {@code ProductPojo} keyed by {@code barcode}, with no surrogate
 * id at all - so an order line stores the barcode. Surrogate keys do not cross a service boundary.
 * <p>
 * <b>Deliberately not {@code ProductPojo}.</b> That model is the Catalogue service's <em>API</em>
 * shape, with its own validation annotations and its own {@code @JsonInclude} policy; coupling this
 * service to it would mean every catalogue API change became an Orders change. A snapshot of the
 * four fields actually used is a narrower and more honest dependency.
 *
  * @param barcode the barcode - the business key, and what the order line stores
 * @param name    the product name, used to generate the line description and shown on receipts
 * @param price   the current price, copied onto the line at creation so history stays price-stable
 */
public record ProductSnapshot(
    String barcode,
    String name,
    int price
) {
}
