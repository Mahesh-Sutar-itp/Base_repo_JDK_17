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

package org.trebol.api.services.impl;

import com.querydsl.core.types.Predicate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.trebol.api.models.PersonPojo;
import org.trebol.api.models.RegistrationPojo;
import org.trebol.jpa.entities.QPerson;
import org.trebol.jpa.entities.QUser;
import org.trebol.jpa.repositories.CustomersRepository;
import org.trebol.jpa.repositories.PeopleRepository;
import org.trebol.jpa.repositories.UsersRepository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Exit criterion X8 / ADR-001 section Decision.1: registration must be genuinely atomic.
 * <p>
 * This test exercises the transaction boundary rather than asserting that an annotation is
 * present. The third write - the {@code customers} row - is forced to fail, and the test then
 * asserts that neither the {@code people} row nor the {@code app_users} row survives. Before
 * Phase 2 added {@code @Transactional} to {@code register(...)}, the person and the user rows
 * both survived and the person row had no delete path anywhere in the system
 * (BR-PERSON-013, BR-USER-012).
 * <p>
 * It must be a {@code @SpringBootTest}: a {@code @DataJpaTest} slice cannot bootstrap this
 * project, because the generic {@code org.trebol.jpa.Repository} base interface is not annotated
 * {@code @NoRepositoryBean} - the same limitation recorded on
 * {@code UserRolePermissionUniquenessTest}. The test method is deliberately <b>not</b>
 * {@code @Transactional}, so the service manages its own transaction and the rollback is real.
 */
@SpringBootTest
class RegistrationServiceImplTransactionalTest {
    static final String USERNAME = "rollback-probe-user";
    static final String ID_NUMBER = "rollback-probe-id";
    static final String FAILED_CUSTOMER_WRITE = "forced failure on the customers write";
    @Autowired RegistrationServiceImpl instance;
    @Autowired PeopleRepository peopleRepository;
    @Autowired UsersRepository usersRepository;
    @MockBean CustomersRepository customersRepositoryMock;

    @Test
    void rolls_back_the_person_and_the_user_when_the_customer_write_fails() {
        when(customersRepositoryMock.saveAndFlush(any()))
            .thenThrow(new IllegalStateException(FAILED_CUSTOMER_WRITE));
        RegistrationPojo registration = RegistrationPojo.builder()
            .name(USERNAME)
            .password("rollback-probe-password")
            .profile(PersonPojo.builder()
                .idNumber(ID_NUMBER)
                .firstName("Rollback")
                .lastName("Probe")
                .email("rollback.probe@example.com")
                .phone1("000000000")
                .phone2("000000000")
                .build())
            .build();

        assertThrows(IllegalStateException.class, () -> instance.register(registration));

        Predicate theProbePerson = QPerson.person.idNumber.eq(ID_NUMBER);
        Predicate theProbeUser = QUser.user.name.eq(USERNAME);
        assertFalse(peopleRepository.exists(theProbePerson), "the person row must not survive a failed registration");
        assertFalse(usersRepository.exists(theProbeUser), "the user row must not survive a failed registration");
    }
}
