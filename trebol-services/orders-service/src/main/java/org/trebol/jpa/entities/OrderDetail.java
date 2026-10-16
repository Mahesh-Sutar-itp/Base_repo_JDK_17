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
import org.trebol.jpa.DBEntity;

import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "order_details")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class OrderDetail
    implements DBEntity {
    private static final long serialVersionUID = 15L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_detail_id", nullable = false)
    private Long id;
    @Column(name = "order_detail_units", nullable = false)
    private int units;
    @Column(name = "order_detail_unit_value", nullable = false)
    private Integer unitValue;
    @Column(name = "order_detail_description", nullable = false)
    @Size(max = 260)
    private String description;
    /**
     * The product this line is for, as its <b>barcode</b> - the business key.
     * <p>
     * <b>Phase 3 cut C1.</b> This was {@code @ManyToOne(optional = false)} with <b>no {@code fetch}
     * attribute, so it defaulted to EAGER</b> - every order line loaded a catalogue entity. The
     * {@code products} table belongs to the Catalogue service, so the association cannot survive
     * and {@code FK_ORDER_DETAIL_PROD_ID} is cut.
     * <p>
     * <b>Nothing of value is lost, because the line already carries its own copy of what it needs.</b>
     * {@code description} and {@code unitValue} are written at creation from the product
     * (BR-ORDER-009, BR-ORDER-010) precisely so that order history is price-stable (BR-ORDER-011) -
     * a later reprice or deletion cannot change a past order. The identifier is kept for the one
     * case that genuinely needs live product data: the receipt, which reads name and barcode through
     * the published catalogue contract (cut C2).
     * <p>
     * <b>The barcode rather than the surrogate id</b>, because the Catalogue's public API exposes
     * {@code ProductPojo} with no {@code id} field - it is keyed by barcode - so {@code product_id}
     * could be neither populated nor resolved across the boundary.
     * <p>
     * <b>This makes the line completely self-describing</b>, and that is a real improvement rather
     * than a workaround: barcode, description and unit value are now all local, so the receipt path
     * needs <b>no remote call at all</b>. Cut C2 therefore removes a coupling instead of replacing
     * it with a network call on an anonymous public endpoint.
     * <p>
     * The legacy {@code product_id} column is retained in the schema, nullable and unmapped, so no
     * existing data migrates. It already had {@code ConstraintMode.NO_CONSTRAINT} at the ORM level
     * and {@code ON DELETE SET NULL} in the DDL, so that edge was a soft reference even before this
     * phase.
     */
    @Column(name = "order_detail_product_barcode", length = 300)
    private String productBarcode;
    @JoinColumn(name = "order_id", referencedColumnName = "order_id", insertable = false, updatable = false,
        foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Order order;

    /**
     * Please note: this copy-constructor does not include a OrderDetail's relationships
     *
     * @param source The original OrderDetail
     */
    public OrderDetail(OrderDetail source) {
        this.id = source.id;
        this.units = source.units;
        this.unitValue = source.unitValue;
        this.description = source.description;
        this.productBarcode = null;
        this.order = null;
    }
}
