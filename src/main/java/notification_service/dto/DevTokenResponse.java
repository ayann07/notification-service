package notification_service.dto;

// This is what the dev bootstrap auth endpoint returns after creating a token.
public record DevTokenResponse(
        // The actual JWT string clients should send as:
        // Authorization: Bearer <accessToken>
        String accessToken,
        // Usually "Bearer" for JWT-based APIs.
        String tokenType,
        // Token lifetime in seconds so the client knows when it expires.
        long expiresInSeconds) {
}
