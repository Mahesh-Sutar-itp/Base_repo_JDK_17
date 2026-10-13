/*
 * Copyright (c) 2020-2024 The Trebol eCommerce Project
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished
 * to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package org.trebol.contracts.security;

import java.util.Set;

/**
 * The authority code catalogue - Gate B of the microservices conversion.
 * <p>
 * In the monolith, 53 of these 54 codes existed <i>only</i> as string literals duplicated
 * across eleven domains' controllers; only {@link #CHECKOUT} was declared as a constant.
 * No compiler checked them, so renaming a code silently disabled an access check.
 * <p>
 * Every service MUST reference these constants rather than literals. Values are copied
 * verbatim from {@code src/main/resources/data.sql} of the monolith and are the exact
 * strings checked at runtime - changing one is a breaking change to access control.
 * <p>
 * Note that enforcement remains <b>method-level</b> in every service. The monolith's
 * security configuration declared no request-matcher rules at all, which means any
 * endpoint without an authority annotation is publicly reachable. A gateway cannot
 * derive authorization from paths; see Gate D of the transformation definition.
 */
public final class AuthorityCodes {

    private AuthorityCodes() {
    }

    // ---------------------------------------------------------------------
    // Identity service
    // ---------------------------------------------------------------------

    public static final String PEOPLE_READ = "people:read";
    public static final String PEOPLE_CREATE = "people:create";
    public static final String PEOPLE_UPDATE = "people:update";
    public static final String PEOPLE_DELETE = "people:delete";

    public static final String USERS_READ = "users:read";
    public static final String USERS_CREATE = "users:create";
    public static final String USERS_UPDATE = "users:update";
    public static final String USERS_DELETE = "users:delete";

    public static final String USER_ROLES_READ = "user_roles:read";
    public static final String USER_ROLES_CREATE = "user_roles:create";
    public static final String USER_ROLES_UPDATE = "user_roles:update";
    public static final String USER_ROLES_DELETE = "user_roles:delete";

    public static final String CUSTOMERS_READ = "customers:read";
    public static final String CUSTOMERS_CREATE = "customers:create";
    public static final String CUSTOMERS_UPDATE = "customers:update";
    public static final String CUSTOMERS_DELETE = "customers:delete";

    public static final String SALESPEOPLE_READ = "salespeople:read";
    public static final String SALESPEOPLE_CREATE = "salespeople:create";
    public static final String SALESPEOPLE_UPDATE = "salespeople:update";
    public static final String SALESPEOPLE_DELETE = "salespeople:delete";

    // ---------------------------------------------------------------------
    // Catalogue service
    // ---------------------------------------------------------------------

    public static final String PRODUCTS_READ = "products:read";
    public static final String PRODUCTS_CREATE = "products:create";
    public static final String PRODUCTS_UPDATE = "products:update";
    public static final String PRODUCTS_DELETE = "products:delete";

    public static final String PRODUCT_CATEGORIES_READ = "product_categories:read";
    public static final String PRODUCT_CATEGORIES_CREATE = "product_categories:create";
    public static final String PRODUCT_CATEGORIES_UPDATE = "product_categories:update";
    public static final String PRODUCT_CATEGORIES_DELETE = "product_categories:delete";

    public static final String PRODUCT_LISTS_READ = "product_lists:read";
    public static final String PRODUCT_LISTS_CREATE = "product_lists:create";
    public static final String PRODUCT_LISTS_UPDATE = "product_lists:update";
    public static final String PRODUCT_LISTS_DELETE = "product_lists:delete";
    /** Note: an action outside the create/read/update/delete convention. */
    public static final String PRODUCT_LISTS_CONTENTS = "product_lists:contents";

    public static final String IMAGES_READ = "images:read";
    public static final String IMAGES_CREATE = "images:create";
    public static final String IMAGES_UPDATE = "images:update";
    public static final String IMAGES_DELETE = "images:delete";

    // ---------------------------------------------------------------------
    // Orders service
    // ---------------------------------------------------------------------

    public static final String ORDERS_READ = "orders:read";
    public static final String ORDERS_CREATE = "orders:create";
    public static final String ORDERS_UPDATE = "orders:update";
    public static final String ORDERS_DELETE = "orders:delete";

    public static final String ORDER_STATUSES_READ = "order_statuses:read";
    public static final String ORDER_STATUSES_CREATE = "order_statuses:create";
    public static final String ORDER_STATUSES_UPDATE = "order_statuses:update";
    public static final String ORDER_STATUSES_DELETE = "order_statuses:delete";

    public static final String ORDER_TYPES_READ = "order_types:read";
    public static final String ORDER_TYPES_CREATE = "order_types:create";
    public static final String ORDER_TYPES_UPDATE = "order_types:update";
    public static final String ORDER_TYPES_DELETE = "order_types:delete";

    public static final String SHIPPERS_READ = "shippers:read";
    public static final String SHIPPERS_CREATE = "shippers:create";
    public static final String SHIPPERS_UPDATE = "shippers:update";
    public static final String SHIPPERS_DELETE = "shippers:delete";

    /**
     * The only code outside the {@code resource:action} convention, and the only one that
     * was already a constant in the monolith.
     */
    public static final String CHECKOUT = "checkout";

    /**
     * Every code in the catalogue. Used to fail fast on an unknown authority rather than
     * letting a typo silently disable an access check.
     */
    public static final Set<String> ALL = Set.of(
        PEOPLE_READ, PEOPLE_CREATE, PEOPLE_UPDATE, PEOPLE_DELETE,
        USERS_READ, USERS_CREATE, USERS_UPDATE, USERS_DELETE,
        USER_ROLES_READ, USER_ROLES_CREATE, USER_ROLES_UPDATE, USER_ROLES_DELETE,
        CUSTOMERS_READ, CUSTOMERS_CREATE, CUSTOMERS_UPDATE, CUSTOMERS_DELETE,
        SALESPEOPLE_READ, SALESPEOPLE_CREATE, SALESPEOPLE_UPDATE, SALESPEOPLE_DELETE,
        PRODUCTS_READ, PRODUCTS_CREATE, PRODUCTS_UPDATE, PRODUCTS_DELETE,
        PRODUCT_CATEGORIES_READ, PRODUCT_CATEGORIES_CREATE, PRODUCT_CATEGORIES_UPDATE, PRODUCT_CATEGORIES_DELETE,
        PRODUCT_LISTS_READ, PRODUCT_LISTS_CREATE, PRODUCT_LISTS_UPDATE, PRODUCT_LISTS_DELETE,
        PRODUCT_LISTS_CONTENTS,
        IMAGES_READ, IMAGES_CREATE, IMAGES_UPDATE, IMAGES_DELETE,
        ORDERS_READ, ORDERS_CREATE, ORDERS_UPDATE, ORDERS_DELETE,
        ORDER_STATUSES_READ, ORDER_STATUSES_CREATE, ORDER_STATUSES_UPDATE, ORDER_STATUSES_DELETE,
        ORDER_TYPES_READ, ORDER_TYPES_CREATE, ORDER_TYPES_UPDATE, ORDER_TYPES_DELETE,
        SHIPPERS_READ, SHIPPERS_CREATE, SHIPPERS_UPDATE, SHIPPERS_DELETE,
        CHECKOUT
    );

    /**
     * @param code an authority code
     * @return whether the code exists in the catalogue
     */
    public static boolean isKnown(String code) {
        return ALL.contains(code);
    }
}
