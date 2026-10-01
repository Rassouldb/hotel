package sn.niirma.auth.internal;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.util.matcher.RequestMatcher;
import tools.jackson.databind.ObjectMapper;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import sn.niirma.shared.error.ApiError;
import sn.niirma.shared.security.CurrentUser;
import sn.niirma.shared.web.TraceContext;

/**
 * Chaîne de sécurité : API sans état, authentification par jeton Bearer (JWT HS256), chemins publics
 * limités à la connexion et au mot de passe oublié. Les règles fines (permission, hôtel) sont
 * appliquées par l'intercepteur d'accès du module shared.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
class SecurityConfiguration {

    /** Chemins accessibles sans jeton. Le jeton éventuellement présent y est ignoré. */
    static final List<RequestMatcher> PUBLIC_PATHS = List.of(
            pathPattern(HttpMethod.POST, "/api/v1/auth/login"),
            pathPattern(HttpMethod.POST, "/api/v1/auth/refresh"),
            pathPattern(HttpMethod.POST, "/api/v1/auth/logout"),
            pathPattern(HttpMethod.POST, "/api/v1/auth/password/forgot"),
            pathPattern(HttpMethod.POST, "/api/v1/auth/password/reset"),
            pathPattern("/actuator/health/**"),
            pathPattern("/actuator/info"),
            pathPattern("/v3/api-docs/**"),
            pathPattern("/swagger-ui/**"),
            pathPattern("/swagger-ui.html"),
            pathPattern("/error"));

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder, SessionService sessions,
                                            ObjectMapper objectMapper, Clock clock) throws Exception {
        AuthenticationEntryPoint unauthenticated = (request, response, exception) ->
                writeError(response, objectMapper, clock, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                        "Authentification requise.");
        AccessDeniedHandler denied = (request, response, exception) ->
                writeError(response, objectMapper, clock, HttpStatus.FORBIDDEN, "PERMISSION_DENIED",
                        "Vous n'avez pas les droits nécessaires pour cette opération.");

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_PATHS.toArray(RequestMatcher[]::new)).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(bearerTokenResolver())
                        .authenticationEntryPoint(unauthenticated)
                        .accessDeniedHandler(denied)
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(authenticationConverter(sessions))))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(unauthenticated)
                        .accessDeniedHandler(denied))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER)));
        return http.build();
    }

    @Bean
    JwtEncoder jwtEncoder(AuthProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties)));
    }

    @Bean
    JwtDecoder jwtDecoder(AuthProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(), new JwtIssuerValidator(AccessTokenService.ISSUER));
        decoder.setJwtValidator(validator);
        return decoder;
    }

    private static SecretKey secretKey(AuthProperties properties) {
        return new SecretKeySpec(properties.jwtSecretBytes(), "HmacSHA256");
    }

    /** Le jeton d'accès n'est lu que sur les chemins protégés. */
    private static BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> isPublic(request) ? null : delegate.resolve(request);
    }

    private static boolean isPublic(HttpServletRequest request) {
        return PUBLIC_PATHS.stream().anyMatch(matcher -> matcher.matches(request));
    }

    /** Transforme un JWT valide en utilisateur courant, si sa session n'a pas été fermée entre-temps. */
    private static Converter<Jwt, AbstractAuthenticationToken> authenticationConverter(SessionService sessions) {
        return jwt -> {
            UUID sessionId = UUID.fromString(jwt.getClaimAsString(AccessTokenService.CLAIM_SESSION));
            if (!sessions.isActive(sessionId)) {
                throw new InvalidBearerTokenException("Session fermée");
            }
            CurrentUser user = new CurrentUser(
                    UUID.fromString(jwt.getSubject()),
                    sessionId,
                    UUID.fromString(jwt.getClaimAsString(AccessTokenService.CLAIM_ORGANIZATION)),
                    jwt.getClaimAsString(AccessTokenService.CLAIM_USERNAME),
                    Boolean.TRUE.equals(jwt.getClaimAsBoolean(AccessTokenService.CLAIM_SUPER_ADMIN)),
                    Boolean.TRUE.equals(jwt.getClaimAsBoolean(AccessTokenService.CLAIM_PASSWORD_CHANGE)));
            return new PmsAuthentication(user, jwt);
        };
    }

    private static void writeError(HttpServletResponse response, ObjectMapper objectMapper, Clock clock,
                                   HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiError body = ApiError.of(code, message, List.of(), Instant.now(clock), TraceContext.currentTraceId());
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    static final class PmsAuthentication extends AbstractAuthenticationToken {

        private final CurrentUser principal;
        private final Jwt token;

        PmsAuthentication(CurrentUser principal, Jwt token) {
            super(AuthorityUtils.NO_AUTHORITIES);
            this.principal = principal;
            this.token = token;
            setAuthenticated(true);
        }

        @Override
        public Object getCredentials() {
            return token;
        }

        @Override
        public Object getPrincipal() {
            return principal;
        }
    }
}
