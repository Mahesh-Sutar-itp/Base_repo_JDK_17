package org.trebol.jpa.entities;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QSalesperson is a Querydsl query type for Salesperson
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QSalesperson extends EntityPathBase<Salesperson> {

    private static final long serialVersionUID = 532277299L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QSalesperson salesperson = new QSalesperson("salesperson");

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final QPerson person;

    public QSalesperson(String variable) {
        this(Salesperson.class, forVariable(variable), INITS);
    }

    public QSalesperson(Path<? extends Salesperson> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QSalesperson(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QSalesperson(PathMetadata metadata, PathInits inits) {
        this(Salesperson.class, metadata, inits);
    }

    public QSalesperson(Class<? extends Salesperson> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.person = inits.isInitialized("person") ? new QPerson(forProperty("person")) : null;
    }

}

