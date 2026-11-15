package com.chubb.claims.api;

import com.chubb.claims.domain.ClaimType;
import com.chubb.claims.dto.ClaimResponse;
import com.chubb.claims.dto.CreateClaimRequest;
import com.chubb.claims.service.ClaimNotFoundException;
import com.chubb.claims.service.ClaimService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClaimController.class)
class ClaimControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean ClaimService service;

    @Test
    void validSubmissionReturnsCreated() throws Exception {
        var request = new CreateClaimRequest("Jane Doe", "jane@example.com", ClaimType.MOTOR,
                "SG", "Collision", new BigDecimal("500"));
        var response = new ClaimResponse(null, "CHB-1", "Jane Doe", "jane@example.com",
                ClaimType.MOTOR, "SG", "Collision", new BigDecimal("500"),
                null, null, null, null, null);
        when(service.create(any())).thenReturn(response);

        mvc.perform(post("/api/v1/claims").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.claimNumber").value("CHB-1"));
    }

    @Test
    void invalidEmailReturnsFieldValidationError() throws Exception {
        mvc.perform(post("/api/v1/claims").contentType(MediaType.APPLICATION_JSON)
                        .content(validBody().replace("jane@example.com", "invalid-email")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message", containsString("claimantEmail")))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/v1/claims"));
    }

    @Test
    void invalidMarketReturnsFieldValidationError() throws Exception {
        mvc.perform(post("/api/v1/claims").contentType(MediaType.APPLICATION_JSON)
                        .content(validBody().replace("\"SG\"", "\"123\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message", containsString("market")));
    }

    @Test
    void invalidEnumReturnsAllowedValuesInsteadOfServerError() throws Exception {
        mvc.perform(post("/api/v1/claims").contentType(MediaType.APPLICATION_JSON)
                        .content(validBody().replace("\"MOTOR\"", "\"UNKNOWN\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_JSON"))
                .andExpect(jsonPath("$.message", containsString("MOTOR")));
    }

    @Test
    void malformedJsonReturnsBadRequest() throws Exception {
        mvc.perform(post("/api/v1/claims").contentType(MediaType.APPLICATION_JSON)
                        .content("{not-valid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_JSON"));
    }

    @Test
    void missingRequestBodyReturnsBadRequest() throws Exception {
        mvc.perform(post("/api/v1/claims").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_JSON"));
    }

    @Test
    void missingQueryParameterReturnsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/claims/track").param("claimNumber", "CHB-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"))
                .andExpect(jsonPath("$.message", containsString("email")));
    }

    @Test
    void blankTrackingQueryReturnsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/claims/track")
                        .param("claimNumber", "  ").param("email", "jane@example.com"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void wrongClaimIdReturnsBadRequest() throws Exception {
        mvc.perform(post("/api/v1/claims/not-a-uuid/review"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.message", containsString("UUID")));
    }

    @Test
    void unknownStatusReturnsAllowedValues() throws Exception {
        mvc.perform(get("/api/v1/claims").param("status", "INVALID").param("market", "SG"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.message", containsString("SUBMITTED")));
    }

    @Test
    void statusFilterWithoutMarketReturnsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/claims").param("status", "SUBMITTED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void unknownSortPropertyReturnsBadRequestInsteadOfJpaError() throws Exception {
        mvc.perform(get("/api/v1/claims").param("sort", "notAField,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message", containsString("sort field")));
    }

    @Test
    void malformedPageNumberReturnsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/claims").param("page", "hello"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message", containsString("page")));
    }

    @Test
    void negativePageSizeReturnsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/claims").param("size", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void excessivePageSizeReturnsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/claims").param("size", "9999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void missingClaimReturnsNotFound() throws Exception {
        when(service.track(eq("CHB-1"), eq("jane@example.com")))
                .thenThrow(new ClaimNotFoundException("Claim not found for supplied claim number and email"));

        mvc.perform(get("/api/v1/claims/track")
                        .param("claimNumber", "CHB-1").param("email", "jane@example.com"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLAIM_NOT_FOUND"));
    }

    @Test
    void invalidLifecycleTransitionReturnsConflict() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.startReview(eq(id)))
                .thenThrow(new IllegalStateException("Operation not allowed when claim status is SUBMITTED"));

        mvc.perform(post("/api/v1/claims/{id}/review", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_CLAIM_STATE"))
                .andExpect(jsonPath("$.message", containsString("SUBMITTED")));
    }

    @Test
    void invalidApprovedAmountReturnsBadRequest() throws Exception {
        mvc.perform(post("/api/v1/claims/{id}/approve", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"approvedAmount\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void wrongMethodReturnsMethodNotAllowed() throws Exception {
        mvc.perform(put("/api/v1/claims/{id}/review", UUID.randomUUID()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void wrongContentTypeReturnsUnsupportedMediaType() throws Exception {
        mvc.perform(post("/api/v1/claims").contentType(MediaType.TEXT_PLAIN).content(validBody()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void unexpectedErrorHasSafeMessageWithoutInternalDetails() throws Exception {
        when(service.create(any())).thenThrow(new RuntimeException("internal connection secret"));
        mvc.perform(post("/api/v1/claims").contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message", not(containsString("secret"))));
    }

    private String validBody() {
        return """
                {"claimantName":"Jane","claimantEmail":"jane@example.com","type":"MOTOR",
                 "market":"SG","incidentDescription":"Collision","estimatedAmount":500}
                """;
    }
}
