package org.trebol;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards the contract the container platform relies on: the health probes answer without a token,
 * reveal no internal details, and no other Actuator endpoint is reachable over HTTP.
 * <p>
 * Uses a plain {@code @SpringBootTest} (same configuration as {@link BackendAppTest}) so Spring reuses
 * the cached application context instead of starting a second one on the same in-memory H2 database.
 * MockMvc is built by hand with the real security filter chain applied.
 */
@SpringBootTest
class ActuatorEndpointsTest {
    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void beforeEach() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"})
    void health_probes_answer_anonymously_with_status_up(String path) throws Exception {
        mockMvc.perform(get(path))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void health_does_not_reveal_component_details() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.components").doesNotExist())
            .andExpect(jsonPath("$.details").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"env", "beans", "configprops", "mappings", "loggers", "heapdump", "threaddump"})
    void other_actuator_endpoints_are_not_exposed(String endpoint) throws Exception {
        mockMvc.perform(get("/actuator/" + endpoint))
            .andExpect(status().isNotFound());
    }
}
