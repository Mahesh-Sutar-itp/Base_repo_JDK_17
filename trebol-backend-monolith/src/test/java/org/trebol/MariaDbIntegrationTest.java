package org.trebol;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthEndpointGroups;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the whole application with the "prod" profile against a real MariaDB running in Docker,
 * so Flyway migrations, the MariaDB driver and the health endpoints are tested the same way they run on AWS.
 * Requires Docker (Docker Desktop) to be running.
 */
@Testcontainers
@ActiveProfiles("prod")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        // Values that ECS will inject from Secrets Manager; dummy values for the test
        "JWT_SECRET_KEY=integration-test-secret-key-that-is-long-enough-for-hs512-signing-0123456789",
        "DB_HOST=unused-because-ServiceConnection-overrides-the-datasource",
        "DB_USERNAME=unused",
        "DB_PASSWORD=unused"
    })
class MariaDbIntegrationTest {

    @Container
    @ServiceConnection
    static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:10.11");

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired HealthEndpointGroups healthGroups;
    @LocalServerPort int serverPort;
    // prod sets management.server.port=8081; in tests Spring Boot gives it a random port too
    @LocalManagementPort int managementPort;

    @Test
    void flyway_applies_schema_and_seed_data() {
        Integer migrations = jdbc.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class);
        assertThat(migrations).isEqualTo(2);
        Integer users = jdbc.queryForObject("SELECT COUNT(*) FROM app_users", Integer.class);
        assertThat(users).isEqualTo(4);
    }

    @Test
    void new_rows_do_not_collide_with_seeded_ids() {
        jdbc.update("INSERT INTO app_user_roles (user_role_name) VALUES ('Integration Test Role')");
        Long id = jdbc.queryForObject(
            "SELECT user_role_id FROM app_user_roles WHERE user_role_name = 'Integration Test Role'", Long.class);
        assertThat(id).isGreaterThanOrEqualTo(5L);
    }

    @Test
    void health_liveness_and_readiness_are_up_on_the_management_port() {
        assertThat(managementPort).isNotEqualTo(serverPort);
        for (String path : new String[]{"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"}) {
            ResponseEntity<String> response =
                http.getForEntity("http://localhost:" + managementPort + path, String.class);
            assertThat(response.getStatusCode()).as(path).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).as(path).contains("\"status\":\"UP\"");
        }
    }

    @Test
    void actuator_is_not_served_on_the_api_port() {
        ResponseEntity<String> response = http.getForEntity("/actuator/health/liveness", String.class);
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.OK);
    }

    @Test
    void liveness_does_not_depend_on_the_database_but_readiness_does() {
        // The ALB health check uses liveness: a busy database must never fail it
        assertThat(healthGroups.get("liveness").isMember("db")).isFalse();
        assertThat(healthGroups.get("liveness").isMember("livenessState")).isTrue();
        assertThat(healthGroups.get("readiness").isMember("db")).isTrue();
    }

    @Test
    void admin_can_log_in_and_read_store_info() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> login = http.postForEntity(
            "/public/login",
            new HttpEntity<>("{\"name\":\"admin\",\"password\":\"admin\"}", headers),
            String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).startsWith("Bearer ");

        ResponseEntity<String> about = http.getForEntity("/public/about", String.class);
        assertThat(about.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(about.getBody()).contains("Demo"); // company name seeded by V2
    }
}
