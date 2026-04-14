package notification_service.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import notification_service.dto.DevTokenRequest;
import notification_service.dto.DevTokenResponse;
import notification_service.security.JwtProperties;
import notification_service.security.JwtTokenService;

@RestController
@Profile({ "dev", "local" })
// This controller only exists in dev/local so we can generate test tokens while
// we do not yet have a separate auth service.
@RequestMapping("/dev-auth")
@RequiredArgsConstructor
public class DevAuthController {

    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;

    @PostMapping("/token")
    public ResponseEntity<DevTokenResponse> createDevToken(@Valid @RequestBody DevTokenRequest request) {
        // Generate a signed JWT containing the requested subject + roles.
        String token = jwtTokenService.generateToken(request.userId(), request.roles(), request.email());
        long expiresInSeconds = jwtProperties.expirationMinutes() * 60;

        // Return the token in a familiar OAuth-style shape.
        return ResponseEntity.ok(new DevTokenResponse(token, "Bearer", expiresInSeconds));
    }
}
