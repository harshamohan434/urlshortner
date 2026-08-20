package com.urlshortener.link;

import com.urlshortener.ratelimit.RateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage across layers: create -> redirect -> async analytics -> expiry ->
 * rate limiting. Unit-level edge cases for the encoder/validation/rate-limiter logic itself
 * live in their own focused test classes, not duplicated here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "urlshortener.rate-limit.capacity=3",
        "urlshortener.rate-limit.refill-per-minute=3"
})
class UrlShortenerFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RateLimiterService rateLimiterService;

    @BeforeEach
    void resetRateLimiter() {
        // The Spring test context (and this singleton bean) is cached and reused across all
        // test methods in this class — without resetting, one test's create-link calls would
        // silently exhaust the bucket for the next.
        rateLimiterService.reset();
    }

    @Test
    void createRedirectAndAnalyticsFlow() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longUrl\":\"https://example.com/some/long/path\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").exists())
                .andReturn();

        LinkResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), LinkResponse.class);
        String code = created.code();
        assertThat(code).hasSize(7);

        mockMvc.perform(get("/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/some/long/path"));
        mockMvc.perform(get("/" + code))
                .andExpect(status().isFound());

        // Analytics writes are async — poll rather than assert immediate consistency.
        awaitClickCount(code, 2);
    }

    @Test
    void expiredLinkReturns410AndIsExcludedFromAnalytics() throws Exception {
        String body = "{\"longUrl\":\"https://example.com/expired\",\"expiresAt\":\""
                + Instant.now().minusSeconds(60) + "\"}";
        MvcResult createResult = mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        LinkResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), LinkResponse.class);

        mockMvc.perform(get("/" + created.code()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error").value("link_expired"));

        Thread.sleep(200); // give any (incorrect) async write a chance to land before asserting it didn't
        mockMvc.perform(get("/api/v1/links/" + created.code() + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clickCount").value(0));
    }

    @Test
    void unknownCodeReturns404() throws Exception {
        mockMvc.perform(get("/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }

    @Test
    void duplicateCustomAliasReturns409() throws Exception {
        String body = "{\"longUrl\":\"https://example.com/a\",\"customAlias\":\"my-alias-1\"}";
        mockMvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("alias_conflict"));
    }

    @Test
    void invalidSchemeIsRejected() throws Exception {
        String body = "{\"longUrl\":\"javascript:alert(1)\"}";
        mockMvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    void createEndpointIsRateLimitedButRedirectIsNot() throws Exception {
        for (int i = 0; i < 3; i++) {
            String body = "{\"longUrl\":\"https://example.com/rl" + i + "\"}";
            mockMvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longUrl\":\"https://example.com/rl-over\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error").value("rate_limit_exceeded"));

        // Redirect (read) traffic must be unaffected by an exhausted create-endpoint bucket.
        mockMvc.perform(get("/does-not-exist-either")).andExpect(status().isNotFound());
    }

    private void awaitClickCount(String code, int expected) throws Exception {
        long deadline = System.currentTimeMillis() + 2000;
        int last = -1;
        while (System.currentTimeMillis() < deadline) {
            MvcResult result = mockMvc.perform(get("/api/v1/links/" + code + "/stats"))
                    .andExpect(status().isOk())
                    .andReturn();
            last = objectMapper.readTree(result.getResponse().getContentAsString()).get("clickCount").asInt();
            if (last == expected) {
                return;
            }
            Thread.sleep(50);
        }
        assertThat(last).isEqualTo(expected);
    }
}
