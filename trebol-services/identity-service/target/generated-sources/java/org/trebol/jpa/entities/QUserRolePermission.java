package org.trebol.jpa.entities;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QUserRolePermission is a Querydsl query type for UserRolePermission
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QUserRolePermission extends EntityPathBase<UserRolePermission> {

    private static final long serialVersionUID = 328897150L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QUserRolePermission userRolePermission = new QUserRolePermission("userRolePermission");

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final QPermission permission;

    public final QUserRole userRole;

    public QUserRolePermission(String variable) {
        this(UserRolePermission.class, forVariable(variable), INITS);
    }

    public QUserRolePermission(Path<? extends UserRolePermission> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QUserRolePermission(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QUserRolePermission(PathMetadata metadata, PathInits inits) {
        this(UserRolePermission.class, metadata, inits);
    }

    public QUserRolePermission(Class<? extends UserRolePermission> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.permission = inits.isInitialized("permission") ? new QPermission(forProperty("permission")) : null;
        this.userRole = inits.isInitialized("userRole") ? new QUserRole(forProperty("userRole")) : null;
    }

}

