package com.urlshortener.link;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the ambiguous-requirement resolution: "let people take their link back down" ->
 * management-token-gated soft deactivation. See docs/ai-log.md for the requirement analysis.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LinkDeactivationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void ownerCanDeactivateWithCorrectToken() throws Exception {
        JsonNode created = createLink();
        String code = created.get("code").asText();
        String token = created.get("managementToken").asText();
        assertThat(token).isNotBlank();

        mockMvc.perform(delete("/api/v1/links/" + code).header("X-Management-Token", token))
                .andExpect(status().isNoContent());

        // The redirect must be gone immediately — not just eventually (cache eviction).
        mockMvc.perform(get("/" + code))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error").value("link_deactivated"));
    }

    @Test
    void deactivateWithWrongTokenIsForbidden() throws Exception {
        JsonNode created = createLink();
        String code = created.get("code").asText();

        mockMvc.perform(delete("/api/v1/links/" + code).header("X-Management-Token", "not-the-real-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("access_denied"));

        // Link must still be live — a rejected delete attempt has no side effect.
        mockMvc.perform(get("/" + code)).andExpect(status().isFound());
    }

    @Test
    void deactivateWithMissingTokenIsForbidden() throws Exception {
        JsonNode created = createLink();
        String code = created.get("code").asText();

        mockMvc.perform(delete("/api/v1/links/" + code))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("access_denied"));
    }

    @Test
    void deactivatingUnknownCodeReturns404() throws Exception {
        mockMvc.perform(delete("/api/v1/links/does-not-exist").header("X-Management-Token", "anything"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deactivateIsIdempotent() throws Exception {
        JsonNode created = createLink();
        String code = created.get("code").asText();
        String token = created.get("managementToken").asText();

        mockMvc.perform(delete("/api/v1/links/" + code).header("X-Management-Token", token))
                .andExpect(status().isNoContent());
        // Second delete with the same (still-correct) token must not error.
        mockMvc.perform(delete("/api/v1/links/" + code).header("X-Management-Token", token))
                .andExpect(status().isNoContent());
    }

    private JsonNode createLink() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longUrl\":\"https://example.com/deactivation-test\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
