package org.trebol.jpa.entities;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QProductList is a Querydsl query type for ProductList
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QProductList extends EntityPathBase<ProductList> {

    private static final long serialVersionUID = 1127735039L;

    public static final QProductList productList = new QProductList("productList");

    public final StringPath code = createString("code");

    public final BooleanPath disabled = createBoolean("disabled");

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final ListPath<ProductListItem, QProductListItem> items = this.<ProductListItem, QProductListItem>createList("items", ProductListItem.class, QProductListItem.class, PathInits.DIRECT2);

    public final StringPath name = createString("name");

    public QProductList(String variable) {
        super(ProductList.class, forVariable(variable));
    }

    public QProductList(Path<? extends ProductList> path) {
        super(path.getType(), path.getMetadata());
    }

    public QProductList(PathMetadata metadata) {
        super(ProductList.class, metadata);
    }

}

