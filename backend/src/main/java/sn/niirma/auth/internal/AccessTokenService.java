package sn.niirma.auth.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import sn.niirma.iam.IamUser;

/**
 * Émet les jetons d'accès JWT (HS256, 15 minutes). Ils ne contiennent pas les permissions, relues
 * en base à chaque requête.
 */
@Service
class AccessTokenService {

    static final String ISSUER = "pms";
    static final String CLAIM_SESSION = "sid";
    static final String CLAIM_ORGANIZATION = "org";
    static final String CLAIM_USERNAME = "usr";
    static final String CLAIM_SUPER_ADMIN = "sa";
    static final String CLAIM_PASSWORD_CHANGE = "pcr";

    record AccessToken(String value, long expiresInSeconds) {
    }

    private final JwtEncoder encoder;
    private final AuthProperties properties;
    private final Clock clock;

    AccessTokenService(JwtEncoder encoder, AuthProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    AccessToken issue(IamUser user, UUID sessionId) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.id().toString())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .claim(CLAIM_SESSION, sessionId.toString())
                .claim(CLAIM_ORGANIZATION, user.organizationId().toString())
                .claim(CLAIM_USERNAME, user.username())
                .claim(CLAIM_SUPER_ADMIN, user.superAdmin())
                .claim(CLAIM_PASSWORD_CHANGE, user.mustChangePassword())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(value, properties.accessTokenTtl().toSeconds());
    }
}
