package notification_service.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import notification_service.exceptions.InvalidRequestException;

class AuthenticatedUserServiceTest {
    // This helper is a key part of ownership checks.
    // It reads the logged-in user's UUID from the JWT subject and blocks
    // cross-user access attempts.

    private final AuthenticatedUserService authenticatedUserService = new AuthenticatedUserService();

    @Test
    void getAuthenticatedUserIdReturnsUuidFromJwtSubject() {
        UUID userId = UUID.randomUUID();

        assertEquals(userId, authenticatedUserService.getAuthenticatedUserId(authentication(userId.toString())));
    }

    @Test
    void getAuthenticatedUserIdThrowsWhenJwtSubjectIsNotUuid() {
        assertThrows(InvalidRequestException.class,
                () -> authenticatedUserService.getAuthenticatedUserId(authentication("not-a-uuid")));
    }

    @Test
    void ensureUserOwnsResourceThrowsWhenAuthenticatedUserDiffersFromRequestedUser() {
        UUID tokenUserId = UUID.randomUUID();
        UUID requestedUserId = UUID.randomUUID();

        assertThrows(AccessDeniedException.class, () -> authenticatedUserService.ensureUserOwnsResource(
                authentication(tokenUserId.toString()),
                requestedUserId));
    }

    private JwtAuthenticationToken authentication(String subject) {
        Jwt jwt = new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "HS256"),
                Map.of("sub", subject, "roles", List.of("ROLE_USER")));
        return new JwtAuthenticationToken(jwt);
    }
}
