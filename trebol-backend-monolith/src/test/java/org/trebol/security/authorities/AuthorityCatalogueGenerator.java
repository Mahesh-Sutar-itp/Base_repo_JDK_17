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

package org.trebol.security.authorities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Generator for the authority code catalogue (Gate B of the microservices conversion).
 * <p>
 * Reads the single source of truth -
 * {@code business-rules-extraction/database-documentation/00-seed-data.json},
 * path {@code $.app_permissions.rows[*].permission_code} - and emits
 * {@code org.trebol.security.authorities.AuthorityCodes}.
 * <p>
 * The emitted file is committed to source control. {@code AuthorityCatalogueIntegrityTest}
 * re-runs this generator on every build and fails if the committed file has drifted from
 * the seed data, which is what makes an unknown, renamed or removed authority code a
 * build failure rather than a silently disabled access check.
 * <p>
 * To regenerate after a seed-data change, run {@link #main(String[])} from the project
 * base directory, or simply copy the expected content reported by the failing test.
 */
public final class AuthorityCatalogueGenerator {
    public static final Path SEED_DATA_PATH =
        Paths.get("business-rules-extraction", "database-documentation", "00-seed-data.json");
    public static final Path GENERATED_FILE_PATH =
        Paths.get("..", "trebol-services", "shared-contracts",
            "src", "main", "java", "org", "trebol", "security", "authorities", "AuthorityCodes.java");
    private static final String PACKAGE_NAME = "org.trebol.security.authorities";
    private static final String CLASS_NAME = "AuthorityCodes";
    private static final String CATALOGUE_VERSION = "1.0.0";
    private static final String LICENSE_HEADER =
        "/*\n" +
            " * Copyright (c) 2020-2024 The Trebol eCommerce Project\n" +
            " *\n" +
            " * Permission is hereby granted, free of charge, to any person obtaining a copy of this software\n" +
            " * and associated documentation files (the \"Software\"), to deal in the Software without restriction,\n" +
            " * including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,\n" +
            " * and/or sell copies of the Software, and to permit persons to whom the Software is furnished\n" +
            " * to do so, subject to the following conditions:\n" +
            " *\n" +
            " * The above copyright notice and this permission notice shall be included in all copies or substantial\n" +
            " * portions of the Software.\n" +
            " *\n" +
            " * THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,\n" +
            " * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A\n" +
            " * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT\n" +
            " * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION\n" +
            " * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE\n" +
            " * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.\n" +
            " */\n";

    private AuthorityCatalogueGenerator() {
    }

    /**
     * Reads every authority code from the seed data, in seed order.
     *
     * @param seedDataFile the location of {@code 00-seed-data.json}
     * @return the authority codes, in {@code permission_id} order as seeded
     * @throws IOException if the seed data cannot be read
     */
    public static List<String> readSeededAuthorityCodes(Path seedDataFile) throws IOException {
        JsonNode root = new ObjectMapper().readTree(Files.readString(seedDataFile, StandardCharsets.UTF_8));
        JsonNode rows = root.path("app_permissions").path("rows");
        if (!rows.isArray() || rows.isEmpty()) {
            throw new IllegalStateException(
                "No authority codes found at $.app_permissions.rows in " + seedDataFile);
        }
        List<String> codes = new ArrayList<>();
        for (JsonNode row : rows) {
            String code = row.path("permission_code").asText(null);
            if (code == null || code.isBlank()) {
                throw new IllegalStateException("Blank permission_code in " + seedDataFile);
            }
            if (codes.contains(code)) {
                throw new IllegalStateException("Duplicate permission_code '" + code + "' in " + seedDataFile);
            }
            codes.add(code);
        }
        return codes;
    }

    /**
     * Derives the Java constant name for an authority code.
     * {@code product_categories:delete} becomes {@code PRODUCT_CATEGORIES_DELETE}.
     */
    public static String constantNameFor(String authorityCode) {
        return authorityCode.toUpperCase().replace(':', '_').replace('-', '_').replace('.', '_');
    }

    /**
     * @return the full source text of the catalogue class, with {@code \n} line endings
     */
    public static String generateSource(List<String> codes) {
        StringBuilder out = new StringBuilder(LICENSE_HEADER);
        out.append('\n')
            .append("package ").append(PACKAGE_NAME).append(";\n")
            .append('\n')
            .append("import java.util.Set;\n")
            .append('\n')
            .append("/**\n")
            .append(" * The authority code catalogue - GENERATED, DO NOT EDIT BY HAND.\n")
            .append(" * <p>\n")
            .append(" * Generated by {@code org.trebol.security.authorities.AuthorityCatalogueGenerator} from\n")
            .append(" * {@code business-rules-extraction/database-documentation/00-seed-data.json}\n")
            .append(" * ({@code $.app_permissions.rows[*].permission_code}), which is the single source of truth\n")
            .append(" * shared with the database seed.\n")
            .append(" * <p>\n")
            .append(" * These ").append(codes.size()).append(" strings are a client-observable contract: they are the\n")
            .append(" * granted-authority values embedded in every issued token. Renaming one silently disables an\n")
            .append(" * access check, which is why they are generated and verified rather than written by hand.\n")
            .append(" * {@code AuthorityCatalogueIntegrityTest} fails the build on any drift between this file and\n")
            .append(" * the seed data, and on any unknown code.\n")
            .append(" */\n")
            .append("public final class ").append(CLASS_NAME).append(" {\n")
            .append("    /** Version of the catalogue contract itself, not of the codes. */\n")
            .append("    public static final String CATALOGUE_VERSION = \"").append(CATALOGUE_VERSION).append("\";\n")
            .append("    /** SHA-256 over the newline-joined codes in seed order; changes whenever the catalogue changes. */\n")
            .append("    public static final String CATALOGUE_CHECKSUM = \"").append(checksumOf(codes)).append("\";\n")
            .append("    /** Number of authority codes in the catalogue. */\n")
            .append("    public static final int CATALOGUE_SIZE = ").append(codes.size()).append(";\n")
            .append('\n');
        for (String code : codes) {
            out.append("    public static final String ").append(constantNameFor(code))
                .append(" = \"").append(code).append("\";\n");
        }
        out.append('\n')
            .append("    /** Every code in the catalogue, in seed order. Immutable. */\n")
            .append("    public static final Set<String> ALL_CODES = Set.of(\n");
        for (int i = 0; i < codes.size(); i++) {
            out.append("        ").append(constantNameFor(codes.get(i)));
            out.append(i < codes.size() - 1 ? ",\n" : "\n");
        }
        out.append("    );\n")
            .append('\n')
            .append("    private ").append(CLASS_NAME).append("() {\n")
            .append("    }\n")
            .append('\n')
            .append("    /**\n")
            .append("     * @param authorityCode any candidate authority code\n")
            .append("     * @return whether the code exists in the catalogue\n")
            .append("     */\n")
            .append("    public static boolean exists(String authorityCode) {\n")
            .append("        return ALL_CODES.contains(authorityCode);\n")
            .append("    }\n")
            .append("}\n");
        return out.toString();
    }

    private static String checksumOf(List<String> codes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(String.join("\n", codes).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exc) {
            throw new IllegalStateException("SHA-256 unavailable", exc);
        }
    }

    public static void main(String[] args) throws IOException {
        List<String> codes = readSeededAuthorityCodes(SEED_DATA_PATH);
        Files.writeString(GENERATED_FILE_PATH, generateSource(codes), StandardCharsets.UTF_8);
        System.out.printf("Wrote %d authority codes to %s%n", codes.size(), GENERATED_FILE_PATH);
    }
}
