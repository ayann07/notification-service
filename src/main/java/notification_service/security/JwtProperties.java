package notification_service.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.jwt")
// This record is a typed view of the values we put in application.yml under
// security.jwt.*
// Spring Boot automatically fills these fields from config when the app starts.
public record JwtProperties(
        // The secret key used to sign and verify JWTs in our dev bootstrap mode.
        String secret,
        // "issuer" means: who created the token.
        // In real systems this is usually your auth service or external identity
        // provider.
        String issuer,
        // How long the access token remains valid, in minutes.
        long expirationMinutes) {
}
