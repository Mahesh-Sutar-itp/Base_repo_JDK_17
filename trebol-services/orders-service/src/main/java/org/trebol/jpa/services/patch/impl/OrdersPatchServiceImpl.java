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

package org.trebol.jpa.services.patch.impl;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.trebol.api.models.OrderPojo;
import org.trebol.common.exceptions.BadInputException;
import org.trebol.jpa.entities.Order;
import org.trebol.jpa.repositories.OrderStatusesRepository;
import org.trebol.jpa.services.contracts.BillingContract;
import org.trebol.jpa.services.contracts.ShipperLookupContract;
import org.trebol.jpa.services.OrderStatusTransitions;
import org.trebol.jpa.services.patch.OrdersPatchService;

import java.time.Instant;
import java.util.Map;

@Transactional
@Service
public class OrdersPatchServiceImpl
    implements OrdersPatchService {
    static final String STATUS_IS_NOT_PATCHABLE =
        "An order's status cannot be changed through this route; use the confirmation, rejection or completion routes instead";
    static final String ILLEGAL_STATUS_TRANSITION =
        "The order cannot move to the requested status from its current one";
    static final String NON_TEXTUAL_PATCH_VALUE =
        "A patched field was not textual where text was expected";
    private final OrderStatusesRepository statusesRepository;
    private final BillingContract billingContract;
    private final ShipperLookupContract shipperLookupContract;

    @Autowired
    public OrdersPatchServiceImpl(
        OrderStatusesRepository statusesRepository,
        BillingContract billingContract,
        ShipperLookupContract shipperLookupContract
    ) {
        this.statusesRepository = statusesRepository;
        this.billingContract = billingContract;
        this.shipperLookupContract = shipperLookupContract;
    }

    @Transactional
    @Override
    public Order patchExistingEntity(Map<String, Object> changes, Order existing) throws BadInputException {
        Order target = new Order(existing);

        try {
            if (changes.containsKey("date")) {
                String rawDate = (String) changes.get("date");
                if (rawDate!=null) {
                    Instant date = Instant.parse(rawDate);
                    target.setDate(date);
                }
            }

            if (changes.containsKey("status")) {
                // Register items D5 and D10. This branch used to resolve the status by name and set
                // it on the copy, with only a blank check and an ifPresent that silently ignored an
                // unknown name. It looked like a state-machine bypass, and the register recorded it
                // as one.
                //
                // An empirical probe written before this change - OrderStatusPatchPersistenceProbeTest
                // - established that it is not. Order.status is mapped
                // @JoinColumn(updatable = false), so Hibernate excludes order_status_id from every
                // generated UPDATE: the patched status was SILENTLY DISCARDED and the request still
                // reported success. The defect is a write that changes nothing, not a write that
                // corrupts the status.
                //
                // Silently accepting an instruction that cannot be carried out is the part worth
                // fixing, so it is now rejected outright, naming the routes that do work. The
                // transition table is consulted first so that an ILLEGAL target is reported as such
                // even though no target can be applied here - if Order.status is ever made updatable,
                // this guard is already the right one and the probe test will fail loudly to say so.
                String statusName = (String) changes.get("status");
                if (!StringUtils.isBlank(statusName)) {
                    String currentStatus = existing.getStatus()==null ? null:existing.getStatus().getName();
                    if (!OrderStatusTransitions.isLegal(currentStatus, statusName)) {
                        throw new BadInputException(ILLEGAL_STATUS_TRANSITION);
                    }
                    throw new BadInputException(STATUS_IS_NOT_PATCHABLE);
                }
            }

            if (changes.containsKey("paymentType")) {
                String paymentTypeName = (String) changes.get("paymentType");
                if (!StringUtils.isBlank(paymentTypeName)) {
                    billingContract.findPaymentTypeByName(paymentTypeName).ifPresent(target::setPaymentType);
                }
            }

            if (changes.containsKey("shipper")) {
                String shipperName = (String) changes.get("shipper");
                if (!StringUtils.isBlank(shipperName)) {
                    shipperLookupContract.findShipperByName(shipperName).ifPresent(target::setShipper);
                }
            }
        } catch (ClassCastException ex) {
            // Register item D5: this literal was "The " - a truncated string carrying no
            // information at all, which a client could do nothing with. Replaced with a message in
            // the style of the domain's others. It is a client-observable change and is recorded.
            throw new BadInputException(NON_TEXTUAL_PATCH_VALUE);
        }

        return target;
    }

    @Override
    public Order patchExistingEntity(OrderPojo changes, Order existing) throws BadInputException {
        throw new UnsupportedOperationException("This method signature has been deprecated");
    }
}
