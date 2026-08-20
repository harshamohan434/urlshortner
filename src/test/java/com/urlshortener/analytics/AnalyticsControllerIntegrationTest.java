package com.urlshortener.analytics;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level coverage for the brownfield /stats/daily endpoint. Unit-level bucketing logic is
 * covered in AnalyticsServiceTest with a fixed Clock; this only checks the endpoint's request
 * validation and response shape end-to-end, so it deliberately avoids asserting on exact bucket
 * dates (real system time), which would make the test flaky/timezone-fragile.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AnalyticsControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void unknownCodeReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/links/does-not-exist/stats/daily"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }

    @Test
    void daysParamOutOfRangeReturns400() throws Exception {
        String code = createLink();

        mockMvc.perform(get("/api/v1/links/" + code + "/stats/daily").param("days", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));

        mockMvc.perform(get("/api/v1/links/" + code + "/stats/daily").param("days", "91"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    void defaultsToSevenDaysAndZeroFillsWithNoClicks() throws Exception {
        String code = createLink();

        MvcResult result = mockMvc.perform(get("/api/v1/links/" + code + "/stats/daily"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andReturn();

        DailyStatsResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), DailyStatsResponse.class);
        assertThat(response.days()).hasSize(7);
        assertThat(response.days()).allSatisfy(d -> assertThat(d.count()).isZero());
    }

    @Test
    void explicitDaysParamIsRespected() throws Exception {
        String code = createLink();

        mockMvc.perform(get("/api/v1/links/" + code + "/stats/daily").param("days", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(3));
    }

    private String createLink() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longUrl\":\"https://example.com/daily-stats-test\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asText();
    }
}
