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

package org.trebol.jpa.services.contracts.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.trebol.jpa.entities.Shipper;
import org.trebol.jpa.repositories.ShippersRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Gate A: the <code>shipper</code> domain's side of the published contract.
 * <p>
 * This class is new. Gate A created three contract seams, and until now only two of them had a
 * behavioural pin - {@code BillingContractImplTest} has seven tests and
 * {@code CustomerProvisioningContractImplTest} four, while {@code ShipperLookupContractImpl} had
 * none at all. The gap was found while writing the domain-shipper porting specification and is
 * recorded in its sections 7.1 and 8.1.
 * <p>
 * The contract is deliberately a thin delegation, so what is worth pinning is narrow and
 * specific: that the lookup is by name, that it delegates rather than reimplementing a query,
 * that an empty result is passed through as an empty {@code Optional} rather than being
 * converted into an exception or a null, and that the contract reads only - resolving a shipper
 * must never create one. That last point is the real asymmetry in this seam and the reason it
 * needs a test: {@code BillingContract} deliberately does resolve-or-<b>create</b> for billing
 * companies, so a future change could reasonably assume shippers behave the same way. They do
 * not. An unknown shipper name is rejected upstream as bad input.
 * <p>
 * Unlike the product and customer contracts, this one stays local after the extraction: the
 * {@code shippers} table is owned by this service, so there is no network hop here. See
 * {@code OrderDomainCouplingArchitectureTest} for the assertion that keeps the remote and local
 * seams from being confused with each other.
 */
@ExtendWith(MockitoExtension.class)
class ShipperLookupContractImplTest {
    static final String ANY = "any";

    @InjectMocks ShipperLookupContractImpl instance;
    @Mock ShippersRepository shippersRepositoryMock;

    @Test
    void finds_shipper_by_name() {
        Shipper expected = Shipper.builder()
            .id(1L)
            .name(ANY)
            .build();
        when(shippersRepositoryMock.findByName(anyString())).thenReturn(Optional.of(expected));
        assertSame(expected, instance.findShipperByName(ANY).orElseThrow());
        verify(shippersRepositoryMock).findByName(ANY);
    }

    @Test
    void returns_empty_for_an_unregistered_shipper() {
        when(shippersRepositoryMock.findByName(anyString())).thenReturn(Optional.empty());
        assertTrue(instance.findShipperByName(ANY).isEmpty());
        verify(shippersRepositoryMock).findByName(ANY);
    }

    /**
     * A null name must reach the repository unchanged rather than being short-circuited here.
     * The contract adds no validation of its own; the caller
     * ({@code OrdersConverterServiceImpl}) is what rejects a missing shipper, and duplicating
     * that check in the seam would mean two places could disagree about it.
     */
    @Test
    void does_not_second_guess_a_null_name() {
        when(shippersRepositoryMock.findByName(isNull())).thenReturn(Optional.empty());
        assertTrue(instance.findShipperByName(null).isEmpty());
        verify(shippersRepositoryMock).findByName(null);
    }

    /**
     * Unlike {@code BillingContract}, this contract is read-only: resolving a shipper never
     * creates one. Pinned explicitly so the resolve-or-create pattern used next door cannot be
     * copied onto this seam without a test turning red.
     */
    @Test
    void never_creates_a_shipper_while_resolving_one() {
        when(shippersRepositoryMock.findByName(anyString())).thenReturn(Optional.empty());
        instance.findShipperByName(ANY);
        verify(shippersRepositoryMock).findByName(ANY);
        verifyNoMoreInteractions(shippersRepositoryMock);
    }
}
