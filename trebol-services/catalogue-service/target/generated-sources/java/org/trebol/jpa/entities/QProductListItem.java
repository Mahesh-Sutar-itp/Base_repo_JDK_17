package org.trebol.jpa.entities;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QProductListItem is a Querydsl query type for ProductListItem
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QProductListItem extends EntityPathBase<ProductListItem> {

    private static final long serialVersionUID = 373634738L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QProductListItem productListItem = new QProductListItem("productListItem");

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final QProductList list;

    public final QProduct product;

    public QProductListItem(String variable) {
        this(ProductListItem.class, forVariable(variable), INITS);
    }

    public QProductListItem(Path<? extends ProductListItem> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QProductListItem(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QProductListItem(PathMetadata metadata, PathInits inits) {
        this(ProductListItem.class, metadata, inits);
    }

    public QProductListItem(Class<? extends ProductListItem> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.list = inits.isInitialized("list") ? new QProductList(forProperty("list")) : null;
        this.product = inits.isInitialized("product") ? new QProduct(forProperty("product"), inits.get("product")) : null;
    }

}

