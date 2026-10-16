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

package org.trebol.jpa.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.trebol.jpa.DBEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Collection;

@Entity
@Table(
    name = "orders",
    indexes = {
        @Index(columnList = "order_date"),
        @Index(columnList = "order_transaction_token"),
    })
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class Order
    implements DBEntity {
    private static final long serialVersionUID = 14L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id", nullable = false)
    private Long id;
    @Column(name = "order_date", nullable = false)
    @CreationTimestamp
    private Instant date;
    @Column(name = "order_total_items", nullable = false)
    private int totalItems;
    @Column(name = "order_net_value", nullable = false)
    private int netValue;
    @Column(name = "order_transport_value", nullable = false)
    private int transportValue;
    @Column(name = "order_taxes_value", nullable = false)
    private int taxesValue;
    @Column(name = "order_total_value", nullable = false)
    private int totalValue;
    @Size(min = 64, max = 64)
    @Column(name = "order_transaction_token")
    private String transactionToken;
    /**
     * Register item D4: the per-order payment-callback nonce.
     * <p>
     * Transbank sends no callback signature, so there is nothing to verify cryptographically from
     * the provider. Instead the callback URL itself is made unguessable and single-purpose: this
     * value is generated when the order moves to {@code Payment Started}, embedded in the
     * {@code returnUrl} handed to Webpay, and required on the callback. Only the party we handed the
     * URL to can call back, which is genuine origin control.
     * <p>
     * <b>Nullable and additive.</b> Orders created before this column existed, and orders that never
     * begin a checkout, simply have no nonce - and a callback for them is rejected, which is the
     * correct outcome.
     */
    @Column(name = "order_callback_nonce")
    private String callbackNonce;
    /**
     * The customer who placed this order, as an <b>identifier</b>.
     * <p>
     * <b>Phase 3 cut.</b> This was a {@code @ManyToOne} association to the {@code Customer} entity,
     * cascading {@code PERSIST} - which is how one checkout call created a customer and a person row
     * as a side effect. {@code customers} and {@code people} belong to the Identity service, so the
     * association cannot survive here and {@code FK_ORDER_CUSTOM_ID} is cut.
     * <p>
     * The column name and type are unchanged, so no data migrates. What changes is that creating a
     * customer is now an explicit call through {@link org.trebol.jpa.services.contracts.CustomerProvisioningContract}
     * rather than an implicit cascade - the write is the same write, but it is now visible.
     */
    @Column(name = "customer_id", nullable = false)
    private Long customerId;
    @JoinColumn(name = "payment_type_id", updatable = false, nullable = false)
    @ManyToOne(optional = false, cascade = CascadeType.REFRESH, fetch = FetchType.LAZY)
    private PaymentType paymentType;
    @JoinColumn(name = "order_status_id", updatable = false, nullable = false)
    @ManyToOne(optional = false, cascade = CascadeType.REFRESH, fetch = FetchType.LAZY)
    private OrderStatus status;
    @JoinColumn(name = "billing_type_id", updatable = false, nullable = false)
    @ManyToOne(optional = false, cascade = CascadeType.REFRESH, fetch = FetchType.LAZY)
    private BillingType billingType;
    @JoinColumn(name = "billing_company_id")
    @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.REFRESH, CascadeType.MERGE}, fetch = FetchType.LAZY)
    private BillingCompany billingCompany;
    @JoinColumn(name = "billing_address_id", updatable = false)
    @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.REFRESH, CascadeType.MERGE}, fetch = FetchType.LAZY)
    private Address billingAddress;
    @JoinColumn(name = "shipper_id", updatable = false)
    @ManyToOne(cascade = CascadeType.REFRESH, fetch = FetchType.LAZY)
    private Shipper shipper;
    @JoinColumn(name = "shipping_address_id", updatable = false)
    @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.REFRESH, CascadeType.MERGE}, fetch = FetchType.LAZY)
    private Address shippingAddress;
    /**
     * The salesperson credited with this order, as an <b>identifier</b>. Nullable.
     * <p>
     * <b>Phase 3 cut</b>, for the same reason as {@link #customerId}: {@code salespeople} belongs to
     * the Identity service and {@code FK_ORDER_SPERSON} is cut. Read-only from this service's point
     * of view - nothing here ever creates a salesperson, so the former {@code PERSIST} and
     * {@code MERGE} cascades had nothing to do.
     */
    @Column(name = "salesperson_id")
    private Long salespersonId;
    @JoinColumn(name = "order_id", nullable = false)
    @OneToMany(cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    private Collection<OrderDetail> details;

    /**
     * Please note that this copy-constructor only preserves the following relationships.
     * <ul>
     *   <li>OrderStatus</li>
     *   <li>PaymentType</li>
     *   <li>BillingType</li>
     * </ul>
     *
     * @param source The original Order
     */
    public Order(Order source) {
        this.id = source.id;
        this.date = Instant.from(source.date);
        this.totalItems = source.totalItems;
        this.netValue = source.netValue;
        this.transportValue = source.transportValue;
        this.taxesValue = source.taxesValue;
        this.totalValue = source.totalValue;
        this.transactionToken = source.transactionToken;
        this.paymentType = source.paymentType;
        this.status = source.status;
        this.billingType = source.billingType;
        this.billingAddress = null;
        this.customerId = null;
        this.details = null;
        this.billingCompany = null;
        this.shipper = null;
        this.shippingAddress = null;
        this.salespersonId = null;
    }
}
