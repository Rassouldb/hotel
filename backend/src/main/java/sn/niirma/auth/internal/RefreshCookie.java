package sn.niirma.auth.internal;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Cookie du jeton de rafraîchissement : inaccessible au JavaScript, envoyé uniquement aux chemins
 * d'authentification et jamais depuis un autre site (§7.2).
 */
@Component
class RefreshCookie {

    static final String NAME = "pms_refresh";
    static final String PATH = "/api/v1/auth";

    private final AuthProperties properties;

    RefreshCookie(AuthProperties properties) {
        this.properties = properties;
    }

    String create(String refreshToken) {
        return build(refreshToken, properties.sessionTtl());
    }

    String clear() {
        return build("", Duration.ZERO);
    }

    Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> !value.isBlank())
                .findFirst();
    }

    private String build(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
