package notification_service.security;

import java.time.Instant;
import java.util.List;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    // This service is only responsible for creating JWT strings.
    // Think of it as the "token minting machine" used by the dev bootstrap auth
    // endpoint.

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public JwtTokenService(JwtEncoder jwtEncoder, JwtProperties jwtProperties) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
    }

    public String generateToken(String subject, List<String> roles, String email) {
        // "now" is the current time. We use it for the issued-at and expiry claims.
        Instant now = Instant.now();

        // JwtClaimsSet is the payload inside the token.
        // These claims are what the notification service will later read back when
        // the user sends the token in the Authorization header.
        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(jwtProperties.expirationMinutes() * 60))
                .subject(subject)
                .claim("roles", roles);

        if (email != null && !email.isBlank()) {
            claimsBuilder.claim("email", email);
        }

        // HS256 means we are signing the token with a shared secret (HMAC).
        // This is fine for local bootstrap mode. In long-term production, a real auth
        // service or IdP would usually issue these tokens for us.
        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claimsBuilder.build())).getTokenValue();
    }
}
