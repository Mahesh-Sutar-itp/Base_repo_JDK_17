package org.trebol.jpa.entities;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QOrder is a Querydsl query type for Order
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QOrder extends EntityPathBase<Order> {

    private static final long serialVersionUID = -1725953632L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QOrder order = new QOrder("order1");

    public final QAddress billingAddress;

    public final QBillingCompany billingCompany;

    public final QBillingType billingType;

    public final StringPath callbackNonce = createString("callbackNonce");

    public final NumberPath<Long> customerId = createNumber("customerId", Long.class);

    public final DateTimePath<java.time.Instant> date = createDateTime("date", java.time.Instant.class);

    public final CollectionPath<OrderDetail, QOrderDetail> details = this.<OrderDetail, QOrderDetail>createCollection("details", OrderDetail.class, QOrderDetail.class, PathInits.DIRECT2);

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final NumberPath<Integer> netValue = createNumber("netValue", Integer.class);

    public final QPaymentType paymentType;

    public final NumberPath<Long> salespersonId = createNumber("salespersonId", Long.class);

    public final QShipper shipper;

    public final QAddress shippingAddress;

    public final QOrderStatus status;

    public final NumberPath<Integer> taxesValue = createNumber("taxesValue", Integer.class);

    public final NumberPath<Integer> totalItems = createNumber("totalItems", Integer.class);

    public final NumberPath<Integer> totalValue = createNumber("totalValue", Integer.class);

    public final StringPath transactionToken = createString("transactionToken");

    public final NumberPath<Integer> transportValue = createNumber("transportValue", Integer.class);

    public QOrder(String variable) {
        this(Order.class, forVariable(variable), INITS);
    }

    public QOrder(Path<? extends Order> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QOrder(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QOrder(PathMetadata metadata, PathInits inits) {
        this(Order.class, metadata, inits);
    }

    public QOrder(Class<? extends Order> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.billingAddress = inits.isInitialized("billingAddress") ? new QAddress(forProperty("billingAddress")) : null;
        this.billingCompany = inits.isInitialized("billingCompany") ? new QBillingCompany(forProperty("billingCompany")) : null;
        this.billingType = inits.isInitialized("billingType") ? new QBillingType(forProperty("billingType")) : null;
        this.paymentType = inits.isInitialized("paymentType") ? new QPaymentType(forProperty("paymentType")) : null;
        this.shipper = inits.isInitialized("shipper") ? new QShipper(forProperty("shipper")) : null;
        this.shippingAddress = inits.isInitialized("shippingAddress") ? new QAddress(forProperty("shippingAddress")) : null;
        this.status = inits.isInitialized("status") ? new QOrderStatus(forProperty("status")) : null;
    }

}

