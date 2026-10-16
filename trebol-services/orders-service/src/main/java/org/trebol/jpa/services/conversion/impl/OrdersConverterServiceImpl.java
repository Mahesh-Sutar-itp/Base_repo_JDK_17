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

package org.trebol.jpa.services.conversion.impl;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.trebol.api.models.AddressPojo;
import org.trebol.api.models.BillingCompanyPojo;
import org.trebol.api.models.OrderDetailPojo;
import org.trebol.api.models.OrderPojo;
import org.trebol.api.models.PersonPojo;
import org.trebol.api.models.ProductPojo;
import org.trebol.common.exceptions.BadInputException;
import org.trebol.jpa.entities.Address;
import org.trebol.jpa.entities.BillingCompany;
import org.trebol.jpa.entities.BillingType;
import org.trebol.jpa.entities.Order;
import org.trebol.jpa.entities.OrderDetail;
import org.trebol.jpa.entities.Shipper;
import org.trebol.jpa.repositories.AddressesRepository;
import org.trebol.jpa.repositories.OrderStatusesRepository;
import org.trebol.jpa.services.contracts.BillingContract;
import org.trebol.jpa.services.contracts.CustomerProvisioningContract;
import org.trebol.jpa.services.contracts.ProductLookupContract;
import org.trebol.jpa.services.contracts.ProductSnapshot;
import org.trebol.jpa.services.contracts.SalespersonLookupContract;
import org.trebol.jpa.services.contracts.ShipperLookupContract;
import org.trebol.jpa.services.conversion.AddressesConverterService;
import org.trebol.jpa.services.conversion.BillingCompaniesConverterService;
import org.trebol.jpa.services.conversion.OrdersConverterService;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.trebol.config.Constants.BILLING_TYPE_ENTERPRISE;
import static org.trebol.config.Constants.ORDER_STATUS_PENDING;

@Transactional
@Service
public class OrdersConverterServiceImpl
    implements OrdersConverterService {
    private final CustomerProvisioningContract customerProvisioningContract;
    private final BillingContract billingContract;
    private final BillingCompaniesConverterService billingCompaniesConverterService;
    private final SalespersonLookupContract salespersonLookupContract;
    private final ProductLookupContract productLookupContract;
    private final ShipperLookupContract shipperLookupContract;
    private final AddressesRepository addressesRepository;
    private final OrderStatusesRepository orderStatusesRepository;
    private final AddressesConverterService addressesConverterService;
    static final double TAX_PERCENT = 0.19; // TODO refactor into a "tax service" of sorts
    static final String UNEXISTING_BILLING_TYPE = "Specified billing type does not exist";

    @Autowired
    public OrdersConverterServiceImpl(
        CustomerProvisioningContract customerProvisioningContract,
        BillingContract billingContract,
        BillingCompaniesConverterService billingCompaniesConverterService,
        SalespersonLookupContract salespersonLookupContract,
        ProductLookupContract productLookupContract,
        ShipperLookupContract shipperLookupContract,
        AddressesRepository addressesRepository,
        OrderStatusesRepository orderStatusesRepository,
        AddressesConverterService addressesConverterService
    ) {
        this.customerProvisioningContract = customerProvisioningContract;
        this.billingContract = billingContract;
        this.billingCompaniesConverterService = billingCompaniesConverterService;
        this.salespersonLookupContract = salespersonLookupContract;
        this.productLookupContract = productLookupContract;
        this.shipperLookupContract = shipperLookupContract;
        this.addressesRepository = addressesRepository;
        this.orderStatusesRepository = orderStatusesRepository;
        this.addressesConverterService = addressesConverterService;
    }

    @Override
    public OrderPojo convertToPojo(Order source) {
        OrderPojo target = OrderPojo.builder()
            .buyOrder(source.getId())
            .date(source.getDate())
            .netValue(source.getNetValue())
            .taxValue(source.getTaxesValue())
            .totalValue(source.getTotalValue())
            .totalItems(source.getTotalItems())
            .transportValue(source.getTransportValue())
            .token(source.getTransactionToken())
            .build();

        PersonPojo customer = customerProvisioningContract.describeCustomer(source.getCustomerIdNumber());
        target.setCustomer(customer);

        target.setStatus(source.getStatus().getName());
        target.setPaymentType(source.getPaymentType().getName());
        target.setBillingType(source.getBillingType().getName());

        if (target.getBillingType().equals(BILLING_TYPE_ENTERPRISE)) {
            BillingCompany sourceBillingCompany = source.getBillingCompany();
            BillingCompanyPojo targetBillingCompany = billingCompaniesConverterService.convertToPojo(sourceBillingCompany);
            target.setBillingCompany(targetBillingCompany);
        }

        if (source.getShipper()!=null) {
            target.setShipper(source.getShipper().getName());
        }

        if (source.getSalespersonIdNumber()!=null) {
            PersonPojo salesperson = salespersonLookupContract.describeSalesperson(source.getSalespersonIdNumber());
            target.setSalesperson(salesperson);
        }
        return target;
    }

    /**
     * Converts a brand-new sell input object into a fully-fledged entity equivalent.<br/>
     * It validates that every piece of information on it is valid and cohesive.
     * For that, it makes several database queries in the process.
     */
    @Override
    public Order convertToNewEntity(OrderPojo model) throws BadInputException {
        Order target = Order.builder().build();
        // databases usually can take care of null dates. besides, the field is annotated with @CreationTimeStamp
        if (model.getDate()!=null) {
            target.setDate(model.getDate());
        }
        orderStatusesRepository.findByName(ORDER_STATUS_PENDING)
            .ifPresent(target::setStatus);
        this.convertPaymentTypeInformationForEntity(model, target);
        this.convertCustomerInformationForEntity(model, target);
        this.convertBillingInformationForEntity(model, target);
        this.convertShippingInformationForEntity(model, target);
        this.convertDetailsForEntity(model, target);
        this.updateTotals(target);
        return target;
    }

    /**
     * Renders an order line, resolving its product through the published catalogue contract.
     * <p>
     * <b>Phase 3 cut C1.</b> This used to read {@code source.getProduct()} - an EAGER-loaded
     * {@code Product} entity - and hand it to {@code ProductsConverterService}. The line now stores
     * only an identifier, so the product is looked up remotely.
     * <p>
     * <b>A failed or empty lookup is not an error.</b> The order line already carries its own
     * {@code description} and {@code unitValue}, written at creation precisely so history stays
     * price-stable (BR-ORDER-011), so an order renders correctly even when the Catalogue is
     * unavailable or the product has since been deleted - it simply renders without live product
     * details. Failing the whole order read because an incidental lookup missed would be a worse
     * outcome than rendering what the order itself knows.
     * <p>
     * This is the single place product resolution happens for a rendered line. The four call sites
     * in {@code OrdersProcessServiceImpl} that used to build this pojo themselves now delegate here,
     * so there is one lookup policy rather than five.
     */
    @Override
    public OrderDetailPojo convertDetailToPojo(OrderDetail source) {
        // The barcode is stored on the line, so rendering it needs NO remote call at all. The
        // product's name is not stored - it is embedded in the line's own generated description
        // ("3x Widget", BR-ORDER-010) - so a rendered line carries the barcode and the description,
        // which is what BR-ORDER-047 says a receipt exposes.
        ProductPojo product = source.getProductBarcode()==null ? null:ProductPojo.builder()
            .barcode(source.getProductBarcode())
            .build();
        return OrderDetailPojo.builder()
            .unitValue(source.getUnitValue())
            .units(source.getUnits())
            .product(product)
            .description(source.getDescription())
            .build();
    }

    /**
     * Converts a detail of the sell into a new entity counterpart.<br/>
     * Its product is matched against the records via its barcode.
     * The value of the unit is set the same as the product's.
     */
    @Override
    public OrderDetail convertDetailToNewEntity(OrderDetailPojo detail) throws RuntimeException {
        try {
            String barcode = detail.getProduct().getBarcode();
            if (StringUtils.isBlank(barcode)) {
                throw new BadInputException("Product barcode must be valid");
            }
            // Phase 3 cut C1: resolves a four-field snapshot from the Catalogue service rather than
            // an entity. The two literals below, and the generated description's "x " with its
            // trailing space, are unchanged - they are client-observable (BR-ORDER-008, -010).
            Optional<ProductSnapshot> productByBarcode = productLookupContract.findProductByBarcode(barcode);
            if (productByBarcode.isEmpty()) {
                throw new BadInputException("Unexisting product in sell details");
            }
            ProductSnapshot product = productByBarcode.get();
            String description = detail.getUnits() + "x " + product.name();
            return OrderDetail.builder()
                .units(detail.getUnits())
                .productBarcode(product.barcode())
                .unitValue(product.price())
                .description(description)
                .build();
        } catch (BadInputException exc) {
            throw new RuntimeException(exc.getMessage(), exc);
        }
    }

    @Override
    public Order applyChangesToExistingEntity(OrderPojo source, Order target) {
        throw new UnsupportedOperationException("This method is deprecated");
    }

    private void convertPaymentTypeInformationForEntity(OrderPojo model, Order target) throws BadInputException {
        String paymentType = model.getPaymentType();
        if (!StringUtils.isBlank(paymentType)) {
            billingContract.findPaymentTypeByName(paymentType)
                .ifPresentOrElse(target::setPaymentType,
                    () -> {
                        throw new RuntimeException("The payment type does not exist");
                    });
        } else {
            throw new BadInputException("A payment type has to be included");
        }
    }

    /**
     * Tries to find a matching customer from the info placed in the order.<br/>
     * If it can't find any match, it will convert the data into a new customer.
     * Either way, the sell will have the customer set.
     * <p>
     * <b>Phase 3.</b> The customer is now recorded as their <b>id number</b> rather than by a
     * {@code @ManyToOne} association cascading {@code PERSIST}. The contract guarantees the customer
     * exists in the Identity service and hands back the business key - which, on both real paths, it
     * already resolved rather than created: a guest's customer row is provisioned by Identity at
     * guest-session time (register item D14) and a registered customer's at registration.
     */
    private void convertCustomerInformationForEntity(OrderPojo model, Order target) throws BadInputException {
        PersonPojo pojoCustomer = model.getCustomer();
        target.setCustomerIdNumber(customerProvisioningContract.resolveOrCreateCustomerIdNumber(pojoCustomer));
    }

    /**
     * Tries to deduce billing information.<br/>
     * If the customer requested an invoice for a business company, it will try to match said company
     * against the existing records, or create a new record for that company.<br/>
     * The same process goes for the billing address.
     */
    private void convertBillingInformationForEntity(OrderPojo model, Order target) throws BadInputException {
        String pojoBillingTypeName = model.getBillingType();
        Optional<BillingType> existingBillingType = billingContract.findBillingTypeByName(pojoBillingTypeName);
        if (existingBillingType.isEmpty()) {
            throw new BadInputException(UNEXISTING_BILLING_TYPE);
        }
        target.setBillingType(existingBillingType.get());

        if (pojoBillingTypeName.equals(BILLING_TYPE_ENTERPRISE)) {
            BillingCompanyPojo pojoBillingCompany = model.getBillingCompany();
            target.setBillingCompany(billingContract.resolveOrCreateBillingCompany(pojoBillingCompany));
            AddressPojo pojoBillingAddress = model.getBillingAddress();
            Optional<Address> existingBillingAddress = this.findAddress(pojoBillingAddress);
            if (existingBillingAddress.isEmpty()) {
                Address address = addressesConverterService.convertToNewEntity(pojoBillingAddress);
                target.setBillingAddress(address);
            } else {
                target.setBillingAddress(existingBillingAddress.get());
            }
        }
    }

    /**
     * Checks whether the order included shipping information, and if so, tries to deduce it.<br/>
     * Tries to find the requested shipper, and the shipping address.
     * In the case of the address only, it may create a new record.
     */
    private void convertShippingInformationForEntity(OrderPojo model, Order target) throws BadInputException {
        String pojoShipperName = model.getShipper();
        AddressPojo pojoShippingAddress = model.getShippingAddress();
        if (!StringUtils.isBlank(pojoShipperName)) {
            Optional<Shipper> existingShipper = shipperLookupContract.findShipperByName(pojoShipperName);
            if (existingShipper.isEmpty()) {
                throw new BadInputException("Specified shipper does not exist");
            }
            target.setShipper(existingShipper.get());
            Optional<Address> existingShippingAddress = this.findAddress(pojoShippingAddress);
            if (existingShippingAddress.isEmpty()) {
                Address address = addressesConverterService.convertToNewEntity(pojoShippingAddress);
                target.setShippingAddress(address);
            } else {
                target.setShippingAddress(existingShippingAddress.get());
            }
        }
    }

    /**
     * Straightly converts the details data.
     * Uses a stream function chain, see {@link #convertDetailToNewEntity}(SellDetailPojo d).
     */
    private void convertDetailsForEntity(OrderPojo model, Order target) {
        List<OrderDetail> detailEntities = model.getDetails().stream()
            .map(this::convertDetailToNewEntity)
            .collect(Collectors.toList());
        target.setDetails(detailEntities);
    }

    /**
     * Updates the net value, taxes, total units and total value of the sell.
     */
    private void updateTotals(Order entity) {
        int netValue = 0;
        int taxesValue = 0;
        int totalUnits = 0;
        for (OrderDetail sd : entity.getDetails()) {
            int unitValue = sd.getUnitValue();
            double unitTaxValue = unitValue * TAX_PERCENT;
            double unitNetValue = unitValue - unitTaxValue;
            taxesValue += (unitTaxValue * sd.getUnits());
            netValue += (unitNetValue * sd.getUnits());
            totalUnits += sd.getUnits();
        }
        entity.setTaxesValue(taxesValue);
        entity.setNetValue(netValue);
        entity.setTotalValue(taxesValue + netValue);
        entity.setTotalItems(totalUnits);
    }

    /**
     * Matches an address using all of its fields.
     */
    private Optional<Address> findAddress(@NotNull AddressPojo address) {
        return addressesRepository.findByFields(
            address.getCity(),
            address.getMunicipality(),
            address.getFirstLine(),
            address.getSecondLine(),
            address.getPostalCode(),
            address.getNotes()
        );
    }
}
