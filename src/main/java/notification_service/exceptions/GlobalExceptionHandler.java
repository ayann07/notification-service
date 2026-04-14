package notification_service.exceptions;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // This class is the single centralized place where Spring routes exceptions
    // thrown from controllers/services while serving REST API requests.
    // Instead of letting each controller manually build error responses, we convert
    // exceptions into a consistent JSON structure with the right HTTP status code.

    // Handles bean-validation failures from @Valid request bodies.
    // This happens when DTO fields violate validation annotations such as
    // @NotBlank, @NotNull, @Size, etc.
    // Example: a template creation request with a blank eventType or short body.
    // Response: 400 Bad Request + field-level validationErrors map.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            WebRequest request) {

        Map<String, String> validationErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            validationErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                request,
                validationErrors);
    }

    // Handles path/query/body type conversion problems.
    // This happens when Spring cannot convert an incoming value to the expected Java
    // type.
    // Example: passing an invalid UUID in a path variable or a non-enum value for an
    // enum field.
    // Response: 400 Bad Request.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatchException(
            MethodArgumentTypeMismatchException ex,
            WebRequest request) {

        String message = "Invalid value for parameter '" + ex.getName() + "'";
        return buildResponse(HttpStatus.BAD_REQUEST, message, request, null);
    }

    // Handles custom request-shape/business-input errors that are not simple DTO
    // annotation failures.
    // Example: recipientType is GUEST but guestUserDetails is missing, or
    // recipientType is REGISTERED_USER but userId is null.
    // Response: 400 Bad Request.
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidRequestException(
            InvalidRequestException ex,
            WebRequest request) {

        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }

    // Handles generic illegal-argument scenarios.
    // This is useful as a fallback for service-layer checks that reject bad client
    // input but do not yet use a custom exception type.
    // Example: unknown enum-like values or invalid method arguments passed deeper in
    // the application.
    // Response: 400 Bad Request.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex,
            WebRequest request) {

        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }

    // Handles "resource does not exist" cases.
    // Example: template not found, user not found, or any requested entity missing
    // from the database.
    // Response: 404 Not Found.
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFoundException(
            ResourceNotFoundException ex,
            WebRequest request) {

        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }

    // Handles "resource already exists / state conflict" cases.
    // Example: creating a template for an eventType + deliveryChannel combination
    // that already exists.
    // Response: 409 Conflict.
    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflictException(
            ResourceConflictException ex,
            WebRequest request) {

        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex,
            WebRequest request) {

        // This is especially important now that we added ownership checks and role
        // checks. When a user is authenticated but not allowed to do something, they
        // should get a clean 403 JSON response.
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage(), request, null);
    }

    // Final safety net for anything we did not explicitly handle above.
    // This prevents raw stack traces or framework default HTML errors from leaking
    // to API clients.
    // Example: unexpected null pointer, SDK failure bubbling up, database issue, or
    // any unanticipated runtime exception.
    // Response: 500 Internal Server Error.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(
            Exception ex,
            WebRequest request) {

        log.error("Unhandled exception while processing request", ex);
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected internal error occurred.",
                request,
                null);
    }

    // Builds the shared JSON error body used by all handlers so every API error
    // follows the same structure.
    private ResponseEntity<ApiErrorResponse> buildResponse(
            HttpStatus status,
            String message,
            WebRequest request,
            Map<String, String> validationErrors) {

        ApiErrorResponse body = new ApiErrorResponse(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                extractPath(request),
                validationErrors);

        return ResponseEntity.status(status).body(body);
    }

    // Extracts the request path so the client can see which API endpoint produced
    // the error response.
    private String extractPath(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            return servletWebRequest.getRequest().getRequestURI();
        }
        return null;
    }
}
