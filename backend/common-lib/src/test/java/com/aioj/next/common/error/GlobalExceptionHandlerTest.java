package com.aioj.next.common.error;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Set;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.TestController.class})
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void domain_exception_maps_status_and_code() throws Exception {
        mockMvc.perform(get("/test/domain"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CONFLICT.code()))
                .andExpect(jsonPath("$.message").value("duplicate"))
                .andExpect(jsonPath("$.details").value(nullValue()));
    }

    @Test
    void method_argument_not_valid_returns_field_errors() throws Exception {
        mockMvc.perform(post("/test/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()))
                .andExpect(jsonPath("$.message").value(ErrorCode.VALIDATION_FAILED.message()))
                .andExpect(jsonPath("$.details.name").exists());
    }

    @Test
    void bind_exception_returns_field_errors() throws Exception {
        mockMvc.perform(get("/test/bind").param("name", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()))
                .andExpect(jsonPath("$.message").value(ErrorCode.VALIDATION_FAILED.message()))
                .andExpect(jsonPath("$.details.name").exists());
    }

    @Test
    void constraint_violation_returns_field_errors() throws Exception {
        mockMvc.perform(get("/test/violate").param("id", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()))
                .andExpect(jsonPath("$.message").value(ErrorCode.VALIDATION_FAILED.message()))
                .andExpect(jsonPath("$.details.id").exists());
    }

    @Test
    void unreadable_body_returns_invalid_payload() throws Exception {
        mockMvc.perform(post("/test/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PAYLOAD.code()))
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_PAYLOAD.message()))
                .andExpect(jsonPath("$.details").value(nullValue()));
    }

    @Test
    void missing_parameter_returns_missing() throws Exception {
        mockMvc.perform(get("/test/required-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.MISSING_PARAMETER.code()))
                .andExpect(jsonPath("$.message").value(ErrorCode.MISSING_PARAMETER.message()))
                .andExpect(jsonPath("$.details.token").exists());
    }

    @Test
    void type_mismatch_returns_type_error() throws Exception {
        mockMvc.perform(get("/test/typed/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.TYPE_MISMATCH.code()))
                .andExpect(jsonPath("$.message").value(ErrorCode.TYPE_MISMATCH.message()))
                .andExpect(jsonPath("$.details.id").exists());
    }

    @Test
    void method_not_allowed_returns_405() throws Exception {
        mockMvc.perform(put("/test/validated"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(ErrorCode.METHOD_NOT_ALLOWED.code()))
                .andExpect(jsonPath("$.message").value(ErrorCode.METHOD_NOT_ALLOWED.message()));
    }

    @Test
    void media_type_not_supported_returns_415() throws Exception {
        mockMvc.perform(post("/test/validated")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("name=demo"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.code()))
                .andExpect(jsonPath("$.message").value("Unsupported media type"));
    }

    @Test
    void upload_too_large_returns_413() throws Exception {
        mockMvc.perform(get("/test/oversize"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value(ErrorCode.PAYLOAD_TOO_LARGE.code()))
                .andExpect(jsonPath("$.message").value(ErrorCode.PAYLOAD_TOO_LARGE.message()));
    }

    @Test
    void unknown_exception_does_not_leak_message() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCode.INTERNAL_ERROR.code()))
                .andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_ERROR.message()))
                .andExpect(jsonPath("$.message").value(not(stringContainsInOrder("secret leak attempt"))));
    }

    @SpringBootApplication
    static class TestApplication {
    }

    @RestController
    @Validated
    static class TestController {
        private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

        @PostMapping("/test/validated")
        public String validated(@RequestBody @Valid Payload payload) {
            return payload.name();
        }

        @GetMapping("/test/bind")
        public String bind(@Valid @ModelAttribute Payload payload) {
            return payload.name();
        }

        @GetMapping("/test/violate")
        public String violate(@RequestParam Long id) {
            Set<ConstraintViolation<ViolationTarget>> violations = VALIDATOR.validateValue(ViolationTarget.class, "id", id);
            if (!violations.isEmpty()) {
                throw new ConstraintViolationException(violations);
            }
            return id.toString();
        }

        @GetMapping("/test/required-param")
        public String required(@RequestParam String token) {
            return token;
        }

        @GetMapping("/test/typed/{id}")
        public String typed(@PathVariable Long id) {
            return id.toString();
        }

        @GetMapping("/test/domain")
        public String domain() {
            throw new DomainException(ErrorCode.CONFLICT, "duplicate");
        }

        @GetMapping("/test/oversize")
        public String oversize() {
            throw new MaxUploadSizeExceededException(1024);
        }

        @GetMapping("/test/boom")
        public String boom() {
            throw new RuntimeException("secret leak attempt");
        }
    }

    record Payload(@NotBlank String name) {
    }

    static class ViolationTarget {
        @Min(1)
        private Long id;
    }
}
