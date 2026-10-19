package com.vanrental.backend;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sends real HTTP requests through the whole Spring Security filter chain (no server needed: MockMvc).
 * Uses the default dev admin account from application.properties. Only reads data.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void publicEndpointsWorkWithoutLogin() throws Exception {
        mockMvc.perform(get("/api/vehicles")).andExpect(status().isOk());
        mockMvc.perform(get("/api/bookings/availability")
                        .param("vehicleId", "1").param("startDate", "2030-01-01").param("endDate", "2030-01-02"))
                .andExpect(status().isOk());
        // public, so a wrong reference is "not found" (404), not "please log in" (401)
        mockMvc.perform(get("/api/bookings/ref/VR-DOESNOTEXIST")).andExpect(status().isNotFound());
    }

    @Test
    void adminEndpointsNeedLogin() throws Exception {
        mockMvc.perform(get("/api/bookings")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/bookings/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/customers")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/vehicles/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
    }

    @Test
    void loginTokenOpensAdminEndpoints() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.token");

        mockMvc.perform(get("/api/bookings").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Change one character of the signature: the token must no longer be accepted
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");
        mockMvc.perform(get("/api/bookings").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }
}
