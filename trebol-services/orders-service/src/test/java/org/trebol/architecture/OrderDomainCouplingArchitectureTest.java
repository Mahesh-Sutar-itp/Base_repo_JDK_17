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

    private static final Pattern ENTITY_IMPORT = Pattern.compile(
        "import\\s+org\\.trebol\\.jpa\\.entities\\.(\\w+)\\s*;");
    /**
     * The nine tables this service owns, per its own schema-mariadb.sql. Any other entity
     * belongs to a sibling service and must not be mapped here.
     */
    private static final Set<String> OWNED_ENTITIES = Set.of(
        "Order",
        "OrderDetail",
        "OrderStatus",
        "OrderMailOutboxEntry",
        "Address",
        "BillingCompany",
        "BillingType",
        "PaymentType",
        "Shipper");
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
     * <p>
     * Phase 3 changed what "owning" means, and this test was rewritten accordingly. In the
     * monolith all three contracts were backed by a local repository, so the assertion was
     * simply that each impl named its own. After the extraction the three seams split into two
     * kinds, and conflating them is exactly the mistake worth catching:
     * <ul>
     *   <li><b>remote</b> - product, customer and salesperson now live in the Catalogue and
     *       Identity services. Their impls must hold a {@code RestClient} and must <b>not</b>
     *       hold a repository, because no such table exists in this service's schema.</li>
     *   <li><b>local</b> - billing and shipper tables are owned here, so their impls must still
     *       delegate to a repository and must <b>not</b> make a network call.</li>
     * </ul>
     * The monolith's copy of this test is unchanged and still asserts the original form.
     */
    @Test
    void each_contract_implementation_exists_and_owns_the_right_side_of_the_boundary() throws IOException {
        Path contracts = MAIN_SOURCE_ROOT.resolve("org/trebol/jpa/services/contracts");
        assertThat(contracts.resolve("ProductLookupContract.java")).exists();
        assertThat(contracts.resolve("BillingContract.java")).exists();
        assertThat(contracts.resolve("ShipperLookupContract.java")).exists();
        assertThat(contracts.resolve("CustomerProvisioningContract.java")).exists();
        assertThat(contracts.resolve("SalespersonLookupContract.java")).exists();

        for (String remote : List.of(
            "impl/ProductLookupContractImpl.java",
            "impl/CustomerProvisioningContractImpl.java",
            "impl/SalespersonLookupContractImpl.java")) {
            String source = Files.readString(contracts.resolve(remote), StandardCharsets.UTF_8);
            assertThat(source)
                .as("%s resolves data this service does not own, so it must go over HTTP", remote)
                .contains("RestClient");
            assertThat(REPOSITORY_IMPORT.matcher(source).find())
                .as("%s must not import a repository - the table it reads lives in another "
                    + "service, so a repository here would mean the shared schema came back", remote)
                .isFalse();
        }

        String billing = Files.readString(contracts.resolve("impl/BillingContractImpl.java"), StandardCharsets.UTF_8);
        assertThat(billing)
            .contains("BillingTypesRepository")
            .contains("PaymentTypesRepository");
        assertThat(billing)
            .as("billing tables are owned by this service - resolving them must not cost a network call")
            .doesNotContain("RestClient");

        String shipper = Files.readString(contracts.resolve("impl/ShipperLookupContractImpl.java"), StandardCharsets.UTF_8);
        assertThat(shipper)
            .contains("ShippersRepository");
        assertThat(shipper)
            .as("the shippers table is owned by this service")
            .doesNotContain("RestClient");
    }

    /**
     * The entity-level variant of Gate A, and the blind spot that hid cut C2.
     * <p>
     * Gate A's published check, and the first test in this class, look for
     * {@code import org.trebol.jpa.repositories.*}. {@code ReceiptServiceImpl} imported
     * {@code org.trebol.jpa.entities.Product} and dereferenced it for the product name and
     * barcode while rendering a receipt - which passed the repository grep cleanly, because an
     * entity import is not a repository import. It is the same class of leak as HS-02, which was
     * a service-level reference that the repository grep also could not see. The general lesson
     * recorded in the domain-order specification: a grep that names one layer proves something
     * about that layer only.
     * <p>
     * After the extraction this is no longer a style question but a compile-and-run boundary -
     * {@code Product}, {@code Customer}, {@code Salesperson} and {@code Person} have no table in
     * this service's schema. The check is written as an allow-list of the nine entities this
     * service owns rather than a deny-list of foreign ones, so an entity added to a sibling
     * service in future is caught without anyone remembering to update this test.
     */
    @Test
    void no_class_imports_an_entity_this_service_does_not_own() throws IOException {
        Map<String, List<String>> offenders = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(MAIN_SOURCE_ROOT)) {
            for (Path source : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> foreign = new ArrayList<>();
                Matcher matcher = ENTITY_IMPORT.matcher(Files.readString(source, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    String entity = matcher.group(1);
                    // QueryDSL generates a Q-prefixed class per entity, in the same package.
                    String owned = entity.startsWith("Q") ? entity.substring(1) : entity;
                    if (!OWNED_ENTITIES.contains(owned)) {
                        foreign.add(entity);
                    }
                }
                if (!foreign.isEmpty()) {
                    offenders.put(source.getFileName().toString(), foreign);
                }
            }
        }
        assertThat(offenders)
            .as("this service may only map tables in its own schema; data owned by the Catalogue "
                + "or Identity service must arrive as a transport model through a published "
                + "contract, never as a JPA entity")
            .isEmpty();
    }

    /**
     * The positive half of cut C2: the receipt path must resolve the product barcode from the
     * order line it already holds, and must make no remote call at all.
     * <p>
     * This matters more than it looks. {@code /public/receipt/{token}} is anonymous and public,
     * so had cut C2 been implemented as the plan originally assumed - replacing the entity
     * dereference with a {@code RestClient} call to the Catalogue - the receipt would have become
     * unavailable whenever the Catalogue was down. Storing the barcode on the line removed the
     * dependency outright instead of relocating it.
     */
    @Test
    void the_receipt_path_resolves_the_product_locally_and_makes_no_remote_call() throws IOException {
        String receipt = Files.readString(
            MAIN_SOURCE_ROOT.resolve("org/trebol/api/services/impl/ReceiptServiceImpl.java"),
            StandardCharsets.UTF_8);
        assertThat(receipt)
            .as("an anonymous public endpoint must not depend on a sibling service's availability")
            .doesNotContain("RestClient")
            .doesNotContain("productLookupContract");
    }
}
