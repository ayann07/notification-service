package notification_service.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

// This request DTO is used only by the dev bootstrap auth endpoint.
// It asks: "for which user and which roles should we mint a local test token?"
public record DevTokenRequest(
        // Stored as the JWT "sub" (subject) claim.
        @NotBlank(message = "userId is required") String userId,
        // Optional extra claim, useful for debugging or future UI integration.
        String email,
        // Example: ["ROLE_USER"] or ["ROLE_ADMIN"].
        @NotEmpty(message = "roles are required") List<String> roles) {
}
