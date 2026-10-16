package org.trebol.jpa.entities;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QOrderMailOutboxEntry is a Querydsl query type for OrderMailOutboxEntry
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QOrderMailOutboxEntry extends EntityPathBase<OrderMailOutboxEntry> {

    private static final long serialVersionUID = 427947710L;

    public static final QOrderMailOutboxEntry orderMailOutboxEntry = new QOrderMailOutboxEntry("orderMailOutboxEntry");

    public final NumberPath<Integer> attempts = createNumber("attempts", Integer.class);

    public final DateTimePath<java.time.Instant> createdAt = createDateTime("createdAt", java.time.Instant.class);

    public final DateTimePath<java.time.Instant> dispatchedAt = createDateTime("dispatchedAt", java.time.Instant.class);

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final StringPath lastError = createString("lastError");

    public final NumberPath<Long> orderId = createNumber("orderId", Long.class);

    public final StringPath orderStatusName = createString("orderStatusName");

    public final StringPath recipientKind = createString("recipientKind");

    public QOrderMailOutboxEntry(String variable) {
        super(OrderMailOutboxEntry.class, forVariable(variable));
    }

    public QOrderMailOutboxEntry(Path<? extends OrderMailOutboxEntry> path) {
        super(path.getType(), path.getMetadata());
    }

    public QOrderMailOutboxEntry(PathMetadata metadata) {
        super(OrderMailOutboxEntry.class, metadata);
    }

}

