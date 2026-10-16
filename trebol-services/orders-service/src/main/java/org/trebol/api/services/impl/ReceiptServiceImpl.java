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

package org.trebol.api.services.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Service;
import org.trebol.api.models.ProductPojo;
import org.trebol.api.models.ReceiptDetailPojo;
import org.trebol.api.models.ReceiptPojo;
import org.trebol.api.services.ReceiptService;
import org.trebol.jpa.services.contracts.ProductLookupContract;
import org.trebol.jpa.entities.Order;
import org.trebol.jpa.entities.OrderDetail;
import org.trebol.jpa.repositories.OrdersRepository;

import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ReceiptServiceImpl
    implements ReceiptService {
    private final OrdersRepository ordersRepository;
    private final ProductLookupContract productLookupContract;
    private final ConversionService conversionService;

    @Autowired
    public ReceiptServiceImpl(
        OrdersRepository ordersRepository,
        ConversionService conversionService,
        ProductLookupContract productLookupContract
    ) {
        this.ordersRepository = ordersRepository;
        this.productLookupContract = productLookupContract;
        this.conversionService = conversionService;
    }

    @Override
    public ReceiptPojo fetchReceiptByTransactionToken(String token)
        throws EntityNotFoundException {
        Optional<Order> match = ordersRepository.findByTransactionToken(token);
        if (match.isEmpty()) {
            throw new EntityNotFoundException("The transaction could not be found, no receipt can be created");
        }
        Order foundMatch = match.get();

        ReceiptPojo target = conversionService.convert(foundMatch, ReceiptPojo.class);

        if (target!=null) {
            List<ReceiptDetailPojo> targetDetails = new ArrayList<>();
            for (OrderDetail d : foundMatch.getDetails()) {
                ReceiptDetailPojo targetDetail = conversionService.convert(d, ReceiptDetailPojo.class);
                if (targetDetail!=null) {
                    // ====================================================================
                    // Phase 3 cut C2 - the coupling Gate A did not catch.
                    //
                    // This read `Product pd = d.getProduct();` and then pd.getName() and
                    // pd.getBarcode() - a direct dereference of a CATALOGUE ENTITY from inside an
                    // api/services class. Gate A's verification greps for
                    // `import org.trebol.jpa.repositories` within api/services/, so importing an
                    // ENTITY passed cleanly. The same blind spot hid coupling hotspot HS-02, which
                    // was the service-level variant; this was the entity-level one.
                    //
                    // Only two fields were ever used, which is what made the cut tractable: the
                    // published contract returns a four-field snapshot, and this needs two of them.
                    //
                    // A missing product yields a null product on the line rather than failing the
                    // receipt. BR-ORDER-047 says the receipt exposes only name and barcode per
                    // line, and a historic receipt must still render after a product is deleted -
                    // FK_ORDER_DETAIL_PROD_ID was ON DELETE SET NULL precisely so that can happen.
                    // ====================================================================
                    ProductPojo targetDetailProduct = productLookupContract.findProductById(d.getProductId())
                        .map(snapshot -> ProductPojo.builder()
                            .name(snapshot.name())
                            .barcode(snapshot.barcode())
                            .build())
                        .orElse(null);
                    targetDetail.setProduct(targetDetailProduct);
                    targetDetail.setUnitValue(d.getUnitValue());
                    targetDetails.add(targetDetail);
                }
            }
            target.setDetails(targetDetails);
            target.setStatus(foundMatch.getStatus().getName());
        }

        return target;
    }
}
