package com.chubb.claims.api;

import com.chubb.claims.service.ClaimNotFoundException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Converts predictable client errors into 4xx responses and hides internal exception details. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ClaimNotFoundException.class)
    public ResponseEntity<ApiError> notFound(ClaimNotFoundException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "CLAIM_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> unknownPath(NoResourceFoundException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "No endpoint exists at this path.", request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> invalidTransition(IllegalStateException ex, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "INVALID_CLAIM_STATE", ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> invalidArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(field -> field.getField() + ": " + field.getDefaultMessage())
                .distinct().sorted().collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", details, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> missingParameter(MissingServletRequestParameterException ex,
                                                       HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
                "Required query parameter '" + ex.getParameterName() + "' is missing.", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> invalidPathOrQuery(MethodArgumentTypeMismatchException ex,
                                                        HttpServletRequest request) {
        Class<?> expected = ex.getRequiredType();
        String message = "Invalid value for '" + ex.getName() + "'.";
        if (expected != null && expected.isEnum()) {
            message += " Allowed values: " + allowedEnumValues(expected) + ".";
        } else if (expected == java.util.UUID.class) {
            message += " Expected a UUID (for example: 123e4567-e89b-12d3-a456-426614174000).";
        }
        return error(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> invalidJson(HttpMessageNotReadableException ex, HttpServletRequest request) {
        String message = "Request body is missing or contains invalid JSON or field types.";
        if (ex.getCause() instanceof InvalidFormatException invalid) {
            String field = invalid.getPath().stream().map(JsonMappingException.Reference::getFieldName)
                    .filter(name -> name != null && !name.isBlank())
                    .collect(Collectors.joining("."));
            if (!field.isBlank()) {
                message = "Invalid value for field '" + field + "'.";
            }
            if (invalid.getTargetType() != null && invalid.getTargetType().isEnum()) {
                message += " Allowed values: " + allowedEnumValues(invalid.getTargetType()) + ".";
            }
        }
        return error(HttpStatus.BAD_REQUEST, "INVALID_JSON", message, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> invalidMethod(HttpRequestMethodNotSupportedException ex,
                                                   HttpServletRequest request) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "This HTTP method is not supported for the requested endpoint.", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> invalidContentType(HttpMediaTypeNotSupportedException ex,
                                                        HttpServletRequest request) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "Send the request body with Content-Type: application/json.", request);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiError> invalidAcceptHeader(HttpMediaTypeNotAcceptableException ex,
                                                         HttpServletRequest request) {
        return error(HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE",
                "The requested response format is not supported. Use Accept: application/json.", request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> concurrentlyModified(ObjectOptimisticLockingFailureException ex,
                                                           HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "CONCURRENT_UPDATE",
                "This claim was updated by another request. Refresh it and try again.", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> conflictingData(DataIntegrityViolationException ex,
                                                     HttpServletRequest request) {
        log.warn("Database constraint conflict for {} {}", request.getMethod(), request.getRequestURI());
        return error(HttpStatus.CONFLICT, "DATA_CONFLICT",
                "The request conflicts with existing data or a database constraint.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest request) {
        // Only genuine server-side failures return 500; never disclose stack traces or internal details.
        log.error("Unexpected failure for {} {}", request.getMethod(), request.getRequestURI(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again later.", request);
    }

    private static String allowedEnumValues(Class<?> enumClass) {
        return Arrays.stream(enumClass.getEnumConstants()).map(Object::toString)
                .collect(Collectors.joining(", "));
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message,
                                           HttpServletRequest request) {
        return ResponseEntity.status(status).body(
                new ApiError(Instant.now(), status.value(), code, message, request.getRequestURI()));
    }
}
