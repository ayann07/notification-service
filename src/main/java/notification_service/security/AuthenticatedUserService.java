package notification_service.security;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import notification_service.exceptions.InvalidRequestException;

@Service
@RequiredArgsConstructor
public class AuthenticatedUserService {
    // This helper keeps all "who is the logged-in user?" logic in one place.
    // That way controllers do not all need to know how to read JWT claims.

    public UUID getAuthenticatedUserId(Authentication authentication) {
        // When Spring Security successfully validates a JWT, it stores the decoded
        // token inside Authentication.getPrincipal().
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AccessDeniedException("Authenticated JWT principal is required.");
        }

        // We decided to store the user UUID in the JWT "sub" (subject) claim.
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new InvalidRequestException("JWT subject is missing.");
        }

        try {
            // Convert the string from the token into a real UUID object so the rest
            // of the code can compare user IDs safely.
            return UUID.fromString(subject);
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("JWT subject must be a valid UUID.");
        }
    }

    public void ensureUserOwnsResource(Authentication authentication, UUID requestedUserId) {
        // This is the critical ownership check.
        // It prevents a logged-in user from sending someone else's userId in the
        // request and reading/updating that other person's data.
        UUID authenticatedUserId = getAuthenticatedUserId(authentication);
        if (!authenticatedUserId.equals(requestedUserId)) {
            throw new AccessDeniedException("You are not allowed to access another user's resources.");
        }
    }
}
