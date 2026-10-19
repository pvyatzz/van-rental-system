package com.vanrental.backend;

import com.jayway.jsonpath.JsonPath;
import com.vanrental.backend.security.GoogleIdentity;
import com.vanrental.backend.security.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
@TestPropertySource(properties = "app.google.client-id=") // Google sign-in "not set up" in this test
class SecurityTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    TokenService tokenService;

    private String customerToken() {
        return tokenService.issueCustomer(new GoogleIdentity("test-google-id", "test@example.com", "Test Customer")).token();
    }

    private String adminToken() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    @Test
    void publicEndpointsWorkWithoutLogin() throws Exception {
        mockMvc.perform(get("/api/vehicles")).andExpect(status().isOk());
        mockMvc.perform(get("/api/bookings/availability")
                        .param("vehicleId", "1").param("startDate", "2030-01-01").param("endDate", "2030-01-02"))
                .andExpect(status().isOk());
        // public, so a wrong reference is "not found" (404), not "please log in" (401)
        mockMvc.perform(get("/api/bookings/ref/VR-DOESNOTEXIST")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/bookings/payment-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.downPayment").value(500));
    }

    @Test
    void paymentProofImagesAreAdminOnly() throws Exception {
        mockMvc.perform(get("/api/bookings/1/payment-proof")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/bookings/1/payment-proof").header("Authorization", "Bearer " + customerToken()))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/bookings/1/payment").header("Authorization", "Bearer " + customerToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"VERIFIED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpointsNeedLogin() throws Exception {
        mockMvc.perform(get("/api/bookings")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/bookings/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/customers")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
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
        String token = adminToken();
        mockMvc.perform(get("/api/bookings").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Change one character of the signature: the token must no longer be accepted
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");
        mockMvc.perform(get("/api/bookings").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bookingNeedsACustomerSignIn() throws Exception {
        // not signed in → 401
        mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/bookings/mine")).andExpect(status().isUnauthorized());
        // signed in as admin, but booking is for customers → 403 Forbidden
        mockMvc.perform(get("/api/bookings/mine").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCanSeeOwnBookingsButNotAdminPages() throws Exception {
        String token = customerToken();
        mockMvc.perform(get("/api/bookings/mine").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/bookings").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reports/yearly").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void googleSignInSaysNotSetUpWithoutClientId() throws Exception {
        mockMvc.perform(post("/api/auth/google").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credential\":\"anything\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Google sign-in is not set up yet (GOOGLE_CLIENT_ID is missing)"));
    }
}
