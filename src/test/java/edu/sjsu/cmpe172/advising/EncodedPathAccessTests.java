package edu.sjsu.cmpe172.advising;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spring routes on the decoded path, so access checks must too. "%61" is "a" and
 * "%6D" is "m"; without decoding, these URLs reached advisor-only handlers.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EncodedPathAccessTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void studentCannotReachAdvisorApiThroughEncodedPath() throws Exception {
        MockHttpSession student = ApiSteps.login(mockMvc, "alex.kim@sjsu.edu");

        mockMvc.perform(get(URI.create("/api/%61dvisor/ping")).session(student))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotReachMetricsThroughEncodedPath() throws Exception {
        mockMvc.perform(get(URI.create("/%6Detrics")))
                .andExpect(status().isUnauthorized());
    }
}
