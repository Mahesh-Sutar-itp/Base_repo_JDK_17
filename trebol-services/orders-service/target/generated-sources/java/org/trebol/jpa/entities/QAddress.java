package org.trebol.jpa.entities;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QAddress is a Querydsl query type for Address
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QAddress extends EntityPathBase<Address> {

    private static final long serialVersionUID = -724643386L;

    public static final QAddress address = new QAddress("address");

    public final StringPath city = createString("city");

    public final StringPath firstLine = createString("firstLine");

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final StringPath municipality = createString("municipality");

    public final StringPath notes = createString("notes");

    public final StringPath postalCode = createString("postalCode");

    public final StringPath secondLine = createString("secondLine");

    public QAddress(String variable) {
        super(Address.class, forVariable(variable));
    }

    public QAddress(Path<? extends Address> path) {
        super(path.getType(), path.getMetadata());
    }

    public QAddress(PathMetadata metadata) {
        super(Address.class, metadata);
    }

}

