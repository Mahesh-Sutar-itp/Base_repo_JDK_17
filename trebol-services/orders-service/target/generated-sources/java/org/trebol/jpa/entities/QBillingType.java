package org.trebol.jpa.entities;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QBillingType is a Querydsl query type for BillingType
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QBillingType extends EntityPathBase<BillingType> {

    private static final long serialVersionUID = -1583059673L;

    public static final QBillingType billingType = new QBillingType("billingType");

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final StringPath name = createString("name");

    public QBillingType(String variable) {
        super(BillingType.class, forVariable(variable));
    }

    public QBillingType(Path<? extends BillingType> path) {
        super(path.getType(), path.getMetadata());
    }

    public QBillingType(PathMetadata metadata) {
        super(BillingType.class, metadata);
    }

}

