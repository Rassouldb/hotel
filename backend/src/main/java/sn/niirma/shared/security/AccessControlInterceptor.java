package sn.niirma.shared.security;

import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import sn.niirma.shared.error.BusinessException;

/**
 * Applique les règles d'accès déclarées sur chaque méthode de contrôleur du PMS : authentification,
 * mot de passe temporaire à remplacer, accès à l'hôtel ciblé par l'URL, permission requise.
 * Un hôtel non affecté donne 404 pour ne pas révéler son existence (§7, itération 1).
 */
class AccessControlInterceptor implements HandlerInterceptor {

    static final String APPLICATION_PACKAGE = "sn.niirma";
    static final String HOTEL_ID_VARIABLE = "hotelId";

    private final AccessControl accessControl;

    AccessControlInterceptor(AccessControl accessControl) {
        this.accessControl = accessControl;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method) || !isApplicationHandler(method)) {
            return true;
        }
        if (method.hasMethodAnnotation(PublicEndpoint.class)) {
            return true;
        }

        RequiresPermission permission = method.getMethodAnnotation(RequiresPermission.class);
        AuthenticatedOnly authenticatedOnly = method.getMethodAnnotation(AuthenticatedOnly.class);
        if (permission == null && authenticatedOnly == null) {
            throw new IllegalStateException("Règle d'accès manquante sur " + method.getShortLogMessage());
        }

        CurrentUser user = CurrentUser.current().orElseThrow(() ->
                new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentification requise."));

        boolean allowedDuringPasswordChange = authenticatedOnly != null && authenticatedOnly.allowedDuringPasswordChange();
        if (user.passwordChangeRequired() && !allowedDuringPasswordChange) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "PASSWORD_CHANGE_REQUIRED",
                    "Vous devez remplacer votre mot de passe temporaire avant de continuer.");
        }

        UUID hotelId = hotelIdFromPath(request);
        if (hotelId != null && !accessControl.canAccessHotel(hotelId)) {
            throw BusinessException.notFound("HOTEL_NOT_FOUND", "Hôtel introuvable.");
        }

        if (permission != null) {
            boolean granted = hotelId != null
                    ? accessControl.hasPermission(permission.value(), hotelId)
                    : accessControl.hasPermissionInAnyHotel(permission.value());
            if (!granted) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "PERMISSION_DENIED",
                        "Vous n'avez pas les droits nécessaires pour cette opération.");
            }
        }
        return true;
    }

    static boolean isApplicationHandler(HandlerMethod method) {
        return method.getBeanType().getPackageName().startsWith(APPLICATION_PACKAGE);
    }

    @SuppressWarnings("unchecked")
    private static UUID hotelIdFromPath(HttpServletRequest request) {
        Map<String, String> variables =
                (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        String value = variables == null ? null : variables.get(HOTEL_ID_VARIABLE);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("INVALID_PARAMETER", "Le paramètre hotelId est invalide.");
        }
    }
}
