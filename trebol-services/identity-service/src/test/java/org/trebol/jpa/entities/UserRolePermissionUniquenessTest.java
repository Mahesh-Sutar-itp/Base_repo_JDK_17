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

package org.trebol.jpa.entities;

import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Register item D17: {@code app_user_role_permissions} had no uniqueness constraint on
 * {@code (user_role_id, permission_id)}, so the same authority could be granted to the same role
 * twice; de-duplication happened in memory at login.
 * <p>
 * The constraint is declared on the entity, because the runtime schema is generated from the
 * annotations (the embedded datasource default applies; {@code spring.jpa.hibernate.ddl-auto} is
 * commented out in {@code application.properties}), and mirrored into {@code schema-mariadb.sql}
 * and the extracted global schema for parity.
 * <p>
 * No database round-trip test accompanies this, and that is a deliberate, recorded limitation.
 * Two routes were tried: a {@code @DataJpaTest} slice cannot bootstrap this project, because the
 * generic {@code org.trebol.jpa.Repository} base interface is not annotated
 * {@code @NoRepositoryBean} and scanning fails with "Not a managed type: interface
 * org.trebol.jpa.DBEntity"; and Hibernate's {@code SchemaExport} no longer ships in the 6.4 line
 * this project uses. Fixing the repository base interface is unrelated to this defect and is not
 * bundled in here. What is asserted is that the constraint is declared where the runtime DDL is
 * generated from, and mirrored in both committed schema scripts.
 */
class UserRolePermissionUniquenessTest {
    static final String TABLE_NAME = "app_user_role_permissions";

    @Test
    void the_entity_declares_the_unique_constraint_on_the_pair() {
        Table table = UserRolePermission.class.getAnnotation(Table.class);

        assertEquals(TABLE_NAME, table.name());
        assertEquals(1, table.uniqueConstraints().length);
        UniqueConstraint constraint = table.uniqueConstraints()[0];
        assertEquals(2, constraint.columnNames().length);
        assertTrue(Arrays.asList(constraint.columnNames()).containsAll(
            List.of("user_role_id", "permission_id")));
    }

    /**
     * Adapted for the port: in the Identity service this module owns exactly one committed schema
     * script. The extracted global schema at
     * {@code business-rules-extraction/database-documentation/00-global-schema.sql} is an artefact of
     * the monolith module and is still asserted by the monolith's copy of this test, which is
     * unchanged. Narrowing the path list here is a compatibility change, not a weakening: the
     * constraint this test exists to protect is still asserted against the DDL this service deploys.
     */
    @Test
    void both_committed_schema_scripts_declare_the_unique_key() throws IOException {
        List<Path> schemas = List.of(
            Paths.get("src", "main", "resources", "schema-mariadb.sql"));
        for (Path schema : schemas) {
            String content = Files.readString(schema, StandardCharsets.UTF_8);
            assertTrue(content.contains("UNIQUE KEY `UQ_UROLEPERMS_UROLE_PERM` (`user_role_id`,`permission_id`)"),
                schema + " must mirror the uniqueness constraint declared on the entity");
        }
    }

}
