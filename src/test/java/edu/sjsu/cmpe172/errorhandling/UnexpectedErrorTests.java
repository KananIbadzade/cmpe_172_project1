package edu.sjsu.cmpe172.errorhandling;

import edu.sjsu.cmpe172.advising.controller.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lives outside the app's package so component scanning never registers the
 * failing controller in the other test contexts.
 */
@ExtendWith(OutputCaptureExtension.class)
class UnexpectedErrorTests {

    @Test
    void unexpectedErrorReturnsGeneric500AndLogsError(CapturedOutput output) throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        mockMvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Unexpected server error"))
                .andExpect(content().string(not(containsString("secret-detail"))));

        assertThat(output).contains("unexpected error type=IllegalStateException");
    }

    @RestController
    static class FailingController {

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("secret-detail");
        }
    }
}
