package com.aioj.next.common.error;

import com.aioj.next.common.api.ApiResponse;
import com.aioj.next.common.api.TraceIds;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomain(DomainException ex) {
        log.warn("DomainException code={} msg={}", ex.errorCode().code(), ex.getMessage());
        return ResponseEntity.status(toStatus(ex.errorCode()))
                .body(ApiResponse.fail(ex.errorCode().code(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleArgumentNotValid(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err ->
                fieldErrors.putIfAbsent(err.getField(), err.getDefaultMessage() != null ? err.getDefaultMessage() : "Invalid"));
        ex.getBindingResult().getGlobalErrors().forEach(err ->
                fieldErrors.putIfAbsent(err.getObjectName(), err.getDefaultMessage() != null ? err.getDefaultMessage() : "Invalid"));
        log.warn("Validation failed: fields={}", fieldErrors.keySet());
        return ResponseEntity.badRequest().body(
                ApiResponse.failWithDetails(ErrorCode.VALIDATION_FAILED.code(), ErrorCode.VALIDATION_FAILED.message(), fieldErrors));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBind(BindException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getFieldErrors().forEach(err ->
                fieldErrors.putIfAbsent(err.getField(), err.getDefaultMessage() != null ? err.getDefaultMessage() : "Invalid"));
        log.warn("Bind failed: fields={}", fieldErrors.keySet());
        return ResponseEntity.badRequest().body(
                ApiResponse.failWithDetails(ErrorCode.VALIDATION_FAILED.code(), ErrorCode.VALIDATION_FAILED.message(), fieldErrors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(violation -> {
            String path = violation.getPropertyPath().toString();
            fieldErrors.putIfAbsent(path, violation.getMessage());
        });
        log.warn("Constraint violation: fields={}", fieldErrors.keySet());
        return ResponseEntity.badRequest().body(
                ApiResponse.failWithDetails(ErrorCode.VALIDATION_FAILED.code(), ErrorCode.VALIDATION_FAILED.message(), fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMostSpecificCause().getClass().getSimpleName());
        return ResponseEntity.badRequest().body(
                ApiResponse.fail(ErrorCode.INVALID_PAYLOAD.code(), ErrorCode.INVALID_PAYLOAD.message()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        Map<String, String> details = Map.of(ex.getParameterName(), "required");
        return ResponseEntity.badRequest().body(
                ApiResponse.failWithDetails(ErrorCode.MISSING_PARAMETER.code(), ErrorCode.MISSING_PARAMETER.message(), details));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        Map<String, String> details = Map.of(
                ex.getName(),
                "expected " + (ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "valid"));
        return ResponseEntity.badRequest().body(
                ApiResponse.failWithDetails(ErrorCode.TYPE_MISMATCH.code(), ErrorCode.TYPE_MISMATCH.message(), details));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(
                ApiResponse.fail(ErrorCode.METHOD_NOT_ALLOWED.code(), ErrorCode.METHOD_NOT_ALLOWED.message()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(
                ApiResponse.fail(ErrorCode.BAD_REQUEST.code(), "Unsupported media type"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
                ApiResponse.fail(ErrorCode.PAYLOAD_TOO_LARGE.code(), ErrorCode.PAYLOAD_TOO_LARGE.message()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception ex) {
        log.error("Unhandled exception traceId={}", TraceIds.current(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiResponse.fail(ErrorCode.INTERNAL_ERROR.code(), ErrorCode.INTERNAL_ERROR.message()));
    }

    private HttpStatus toStatus(ErrorCode code) {
        return switch (code) {
            case BAD_REQUEST, VALIDATION_FAILED, INVALID_PAYLOAD, MISSING_PARAMETER, TYPE_MISMATCH -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case METHOD_NOT_ALLOWED -> HttpStatus.METHOD_NOT_ALLOWED;
            case CONFLICT -> HttpStatus.CONFLICT;
            case PAYLOAD_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case TOO_MANY_REQUESTS -> HttpStatus.TOO_MANY_REQUESTS;
            case SERVICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
