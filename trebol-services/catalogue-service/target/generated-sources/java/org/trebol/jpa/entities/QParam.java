package org.trebol.jpa.entities;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QParam is a Querydsl query type for Param
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QParam extends EntityPathBase<Param> {

    private static final long serialVersionUID = -1725523233L;

    public static final QParam param = new QParam("param");

    public final StringPath category = createString("category");

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final StringPath name = createString("name");

    public final StringPath value = createString("value");

    public QParam(String variable) {
        super(Param.class, forVariable(variable));
    }

    public QParam(Path<? extends Param> path) {
        super(path.getType(), path.getMetadata());
    }

    public QParam(PathMetadata metadata) {
        super(Param.class, metadata);
    }

}

