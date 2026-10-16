package org.trebol.jpa.entities;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QBillingCompany is a Querydsl query type for BillingCompany
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QBillingCompany extends EntityPathBase<BillingCompany> {

    private static final long serialVersionUID = -386139280L;

    public static final QBillingCompany billingCompany = new QBillingCompany("billingCompany");

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final StringPath idNumber = createString("idNumber");

    public final StringPath name = createString("name");

    public QBillingCompany(String variable) {
        super(BillingCompany.class, forVariable(variable));
    }

    public QBillingCompany(Path<? extends BillingCompany> path) {
        super(path.getType(), path.getMetadata());
    }

    public QBillingCompany(PathMetadata metadata) {
        super(BillingCompany.class, metadata);
    }

}

