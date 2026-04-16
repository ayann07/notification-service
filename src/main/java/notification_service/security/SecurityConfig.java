package notification_service.security;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import notification_service.exceptions.ApiErrorResponse;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {
    // This is the central Spring Security configuration for the whole service.
    // It answers questions like:
    // - Which endpoints are public?
    // - Which endpoints need a JWT?
    // - Which roles can call which APIs?
    // - What should a 401 or 403 response look like?

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter)
            throws Exception {
        http
                // We are building a token-based API, not a browser session app, so CSRF
                // protection is disabled here.
                .csrf(AbstractHttpConfigurer::disable)
                // Stateless means: Spring Security will not create login sessions on the
                // server. Every request must carry its own Bearer token.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Swagger/OpenAPI docs are left open so the API can still be
                        // explored easily.
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // Dev token issuing is open only because the controller itself is
                        // restricted to the dev/local profile.
                        .requestMatchers("/api/v1/dev-auth/**").permitAll()
                        // Template management is an admin operation.
                        .requestMatchers("/api/v1/templates/**").hasRole("ADMIN")
                        // Replay/recovery is an internal operational action.
                        .requestMatchers("/api/v1/internal/recovery/**").hasAnyRole("ADMIN", "INTERNAL")
                        // Test publish is treated like an internal/admin action.
                        .requestMatchers("/api/v1/test/**").hasAnyRole("ADMIN", "INTERNAL")
                        // User-facing notification APIs require a normal authenticated user
                        // or admin.
                        .requestMatchers("/api/v1/notifications/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/api/v1/notification/preferences/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/api/v1/devices/**").hasAnyRole("USER", "ADMIN")
                        // Everything else is protected by default.
                        .anyRequest().authenticated())
                // This tells Spring Security to expect Bearer JWT tokens and convert
                // them into an authenticated user context.
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            // 401 = the user is not authenticated (missing or invalid token).
                            response.setStatus(401);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(toJson(new ApiErrorResponse(
                                    LocalDateTime.now(),
                                    401,
                                    "Unauthorized",
                                    "A valid Bearer token is required.",
                                    request.getRequestURI(),
                                    null)));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            // 403 = the user is authenticated, but not allowed to do this.
                            response.setStatus(403);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(toJson(new ApiErrorResponse(
                                    LocalDateTime.now(),
                                    403,
                                    "Forbidden",
                                    "You are not allowed to perform this action.",
                                    request.getRequestURI(),
                                    null)));
                        }));

        return http.build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        // JwtAuthenticationConverter takes a validated JWT and turns it into Spring
        // Security "authorities" (roles/permissions).
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::extractAuthorities);
        return converter;
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties jwtProperties) {
        // Decoder = reads and verifies incoming JWTs from requests.
        return NimbusJwtDecoder.withSecretKey(secretKey(jwtProperties)).build();
    }

    @Bean
    JwtEncoder jwtEncoder(JwtProperties jwtProperties) {
        // Encoder = creates/signed JWTs for our dev bootstrap flow.
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(jwtProperties)));
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        // We store roles inside a custom "roles" claim as a list like:
        // ["ROLE_USER", "ROLE_ADMIN"]
        // Spring Security needs those turned into GrantedAuthority objects.
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null) {
            return List.of();
        }

        return roles.stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    private SecretKey secretKey(JwtProperties jwtProperties) {
        // The raw string from application.yml is converted into a SecretKey object
        // because the JWT library works with keys, not plain strings.
        return new SecretKeySpec(jwtProperties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private String toJson(ApiErrorResponse response) {
        // We build a tiny JSON response manually here so security errors still match
        // the same API-style response format as the rest of the application.
        String path = response.path() == null ? "null" : "\"" + escape(response.path()) + "\"";
        return """
                {"timestamp":"%s","status":%d,"error":"%s","message":"%s","path":%s,"validationErrors":null}
                """.formatted(
                response.timestamp(),
                response.status(),
                escape(response.error()),
                escape(response.message()),
                path).replace("\n", "");
    }

    private String escape(String value) {
        // Small helper so quotes and backslashes do not break the generated JSON.
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
