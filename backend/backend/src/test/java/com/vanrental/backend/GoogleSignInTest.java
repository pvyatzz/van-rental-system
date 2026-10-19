package com.vanrental.backend;

import com.jayway.jsonpath.JsonPath;
import com.vanrental.backend.security.GoogleIdentity;
import com.vanrental.backend.security.GoogleTokenVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/auth/google end to end, with Google's part replaced by a mock
 * (GoogleTokenVerifierTest covers the real verification rules).
 */
@SpringBootTest
@AutoConfigureMockMvc
class GoogleSignInTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean // replaces the real verifier bean in this test's Spring context
    GoogleTokenVerifier googleTokenVerifier;

    @Test
    void googleSignInGivesACustomerToken() throws Exception {
        when(googleTokenVerifier.verify("google-id-token"))
                .thenReturn(new GoogleIdentity("google-123", "maria@gmail.com", "Maria Santos"));

        String body = mockMvc.perform(post("/api/auth/google").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credential\":\"google-id-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Maria Santos"))
                .andExpect(jsonPath("$.email").value("maria@gmail.com"))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
                .andReturn().getResponse().getContentAsString();

        String token = JsonPath.read(body, "$.token");
        mockMvc.perform(get("/api/bookings/mine").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
