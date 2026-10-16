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

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.persister.entity.EntityPersister;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.persistence.EntityManagerFactory;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Empirical probe for register item <b>D5</b>, written <em>before</em> any D5 code change.
 * <p>
 * The register records D5 as "a {@code PATCH} that bypasses the order state machine entirely".
 * Establishing what actually happens was a precondition of fixing it, because
 * {@code Order.status} is mapped
 * {@code @JoinColumn(name = "order_status_id", updatable = false, nullable = false)} while the
 * {@code markAs*} path uses an explicit {@code @Modifying} JPQL update
 * ({@code OrdersRepository.setStatus}). Whether a <em>patched</em> status reaches the database
 * through the generic {@code saveAndFlush} path was therefore unverified, and writing a fix
 * against an assumption would have been writing it against a phantom.
 * <p>
 * This asks <b>Hibernate itself</b> rather than reasoning from the annotation: an entity
 * attribute marked non-updatable is excluded from generated {@code UPDATE} statements, and
 * {@link EntityPersister#getPropertyUpdateability()} is the authority on that.
 * <p>
 * <b>Result, recorded in the D5 ruling:</b> the status attribute is <b>not</b> updatable, so a
 * patched status is <b>silently discarded</b> by the generic save path. D5 is consequently a
 * different - and in one respect worse - defect than recorded: not a state-machine bypass that
 * corrupts the status, but a write that <b>reports success while changing nothing</b>. The
 * transition table added alongside this probe closes the bypass for every path that <em>can</em>
 * write a status, and this test pins the mapping that makes the patch path a no-op, so that a
 * future change to {@code updatable} cannot silently turn it into a real bypass.
 */
@SpringBootTest
class OrderStatusPatchPersistenceProbeTest {
    @Autowired EntityManagerFactory entityManagerFactory;

    private EntityPersister orderPersister() {
        return entityManagerFactory.unwrap(SessionFactoryImplementor.class)
            .getMappingMetamodel()
            .getEntityDescriptor(Order.class.getName());
    }

    /**
     * The load-bearing assertion. If this ever fails, a patched status has become capable of
     * reaching the database through the generic save path, and the D5 ruling must be revisited.
     */
    @Test
    void the_status_attribute_is_excluded_from_generated_update_statements() {
        EntityPersister persister = orderPersister();
        List<String> names = Arrays.asList(persister.getPropertyNames());
        int statusIndex = names.indexOf("status");

        assertTrue(statusIndex >= 0, "Order must still map a 'status' attribute");
        assertFalse(persister.getPropertyUpdateability()[statusIndex],
            "Order.status is mapped updatable = false, so a patched status is silently discarded "
                + "by the generic save path. See register item D5.");
    }

    /**
     * The contrast that explains why {@code markAs*} works at all: these attributes <em>are</em>
     * updatable, which is why patching a date or a billing company does take effect while patching
     * a status does not.
     */
    @Test
    void the_date_attribute_is_updatable_which_is_why_other_patches_do_take_effect() {
        EntityPersister persister = orderPersister();
        List<String> names = Arrays.asList(persister.getPropertyNames());
        int dateIndex = names.indexOf("date");

        assertTrue(dateIndex >= 0, "Order must still map a 'date' attribute");
        assertTrue(persister.getPropertyUpdateability()[dateIndex],
            "Order.date is updatable; a patched date reaches the database");
    }

    /**
     * {@code paymentType} and {@code shipper} are the other two keys {@code OrdersPatchServiceImpl}
     * honours. Both are mapped {@code updatable = false} as well, so two more of the four patchable
     * fields are silent no-ops. Pinned here because it is the same defect as the status case and
     * belongs in the same record.
     */
    @Test
    void payment_type_and_shipper_patches_are_also_silently_discarded() {
        EntityPersister persister = orderPersister();
        List<String> names = Arrays.asList(persister.getPropertyNames());
        boolean[] updateability = persister.getPropertyUpdateability();

        int paymentTypeIndex = names.indexOf("paymentType");
        int shipperIndex = names.indexOf("shipper");

        assertTrue(paymentTypeIndex >= 0 && shipperIndex >= 0);
        assertFalse(updateability[paymentTypeIndex],
            "Order.paymentType is mapped updatable = false - patching it is a silent no-op");
        assertFalse(updateability[shipperIndex],
            "Order.shipper is mapped updatable = false - patching it is a silent no-op");
    }
}
