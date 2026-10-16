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

package org.trebol.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gate A enforcement.
 * <p>
 * Exit criterion, verbatim from the transformation definition: <em>"no class in the
 * <code>order</code> domain imports another domain's repository"</em>. Before Gate A,
 * {@code OrdersConverterServiceImpl} resolved products (by barcode, <b>per order line</b>),
 * billing types, billing companies (read <b>and create</b>) and shippers through four other
 * domains' repository interfaces inside one checkout call, and {@code OrdersPatchServiceImpl}
 * added two more.
 * <p>
 * All 22 repository interfaces live in one flat package, so there is no package boundary to
 * lean on - the ownership map below is the boundary, taken from the domain manifest.
 * <p>
 * Note on the published grep check: the command printed in the transformation definition
 * ({@code grep "import org.trebol.jpa.repositories" api/services/ | grep -iE "Orders|Checkout"})
 * also matches order-domain classes importing their <b>own</b> repositories, which is correct
 * code. This test asserts the stated criterion instead of the over-broad command.
 */
class OrderDomainCouplingArchitectureTest {
    private static final Path MAIN_SOURCE_ROOT = Paths.get("src", "main", "java");
    private static final Pattern REPOSITORY_IMPORT = Pattern.compile(
        "import\\s+org\\.trebol\\.jpa\\.repositories\\.(\\w+)\\s*;");

    /** Repositories owned by the order domain itself. Importing these is legitimate. */
    private static final Set<String> ORDER_DOMAIN_REPOSITORIES = Set.of(
        "OrdersRepository",
        "OrderDetailsRepository",
        "OrderStatusesRepository",
        "AddressesRepository");

    /**
     * Classes that constitute the order domain. Matched on simple class name so that a new
     * order-domain class cannot quietly reintroduce the coupling.
     */
    private static final List<String> ORDER_DOMAIN_CLASS_PREFIXES = List.of(
        "Orders", "OrderStatuses", "OrderDetails", "Checkout", "Receipt");

    private static boolean isOrderDomainClass(Path source) {
        String simpleName = source.getFileName().toString().replace(".java", "");
        return ORDER_DOMAIN_CLASS_PREFIXES.stream().anyMatch(simpleName::startsWith);
    }

    @Test
    void no_order_domain_class_imports_another_domains_repository() throws IOException {
        Map<String, List<String>> offenders = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(MAIN_SOURCE_ROOT)) {
            for (Path source : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (!isOrderDomainClass(source)) {
                    continue;
                }
                List<String> foreign = new ArrayList<>();
                Matcher matcher = REPOSITORY_IMPORT.matcher(Files.readString(source, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    String repository = matcher.group(1);
                    if (!ORDER_DOMAIN_REPOSITORIES.contains(repository)) {
                        foreign.add(repository);
                    }
                }
                if (!foreign.isEmpty()) {
                    offenders.put(source.getFileName().toString(), foreign);
                }
            }
        }
        assertThat(offenders)
            .as("order-domain classes must reach other domains through a published contract in "
                + "org.trebol.jpa.services.contracts, never through their repositories")
            .isEmpty();
    }

    @Test
    void the_cross_domain_resolutions_in_checkout_go_through_contracts() throws IOException {
        String converter = Files.readString(
            MAIN_SOURCE_ROOT.resolve("org/trebol/jpa/services/conversion/impl/OrdersConverterServiceImpl.java"),
            StandardCharsets.UTF_8);
        assertThat(converter)
            .contains("productLookupContract.findProductByBarcode(")
            .contains("billingContract.findBillingTypeByName(")
            .contains("billingContract.findPaymentTypeByName(")
            .contains("billingContract.resolveOrCreateBillingCompany(")
            .contains("shipperLookupContract.findShipperByName(");
        assertThat(converter)
            .doesNotContain("productsRepository")
            .doesNotContain("billingTypesRepository")
            .doesNotContain("shippersRepository")
            .doesNotContain("paymentTypesRepository")
            .doesNotContain("billingCompaniesCrudService");

        String patch = Files.readString(
            MAIN_SOURCE_ROOT.resolve("org/trebol/jpa/services/patch/impl/OrdersPatchServiceImpl.java"),
            StandardCharsets.UTF_8);
        assertThat(patch)
            .contains("billingContract.findPaymentTypeByName(")
            .contains("shipperLookupContract.findShipperByName(");
        assertThat(patch)
            .doesNotContain("paymentTypesRepository")
            .doesNotContain("shippersRepository");
    }

    /**
     * The owning side of each contract is the only place its repository may be reached for
     * cross-domain resolution. If a second holder appears, the seam has leaked.
     */
    @Test
    void each_contract_implementation_exists_and_owns_its_repository() throws IOException {
        Path contracts = MAIN_SOURCE_ROOT.resolve("org/trebol/jpa/services/contracts");
        assertThat(contracts.resolve("ProductLookupContract.java")).exists();
        assertThat(contracts.resolve("BillingContract.java")).exists();
        assertThat(contracts.resolve("ShipperLookupContract.java")).exists();
        assertThat(Files.readString(contracts.resolve("impl/ProductLookupContractImpl.java"), StandardCharsets.UTF_8))
            .contains("ProductsRepository");
        assertThat(Files.readString(contracts.resolve("impl/BillingContractImpl.java"), StandardCharsets.UTF_8))
            .contains("BillingTypesRepository")
            .contains("PaymentTypesRepository");
        assertThat(Files.readString(contracts.resolve("impl/ShipperLookupContractImpl.java"), StandardCharsets.UTF_8))
            .contains("ShippersRepository");
    }
}
