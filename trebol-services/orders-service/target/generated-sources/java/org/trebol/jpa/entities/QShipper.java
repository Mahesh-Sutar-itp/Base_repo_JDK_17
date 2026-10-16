package org.trebol.jpa.entities;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QShipper is a Querydsl query type for Shipper
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QShipper extends EntityPathBase<Shipper> {

    private static final long serialVersionUID = -1810361549L;

    public static final QShipper shipper = new QShipper("shipper");

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final StringPath name = createString("name");

    public QShipper(String variable) {
        super(Shipper.class, forVariable(variable));
    }

    public QShipper(Path<? extends Shipper> path) {
        super(path.getType(), path.getMetadata());
    }

    public QShipper(PathMetadata metadata) {
        super(Shipper.class, metadata);
    }

}

