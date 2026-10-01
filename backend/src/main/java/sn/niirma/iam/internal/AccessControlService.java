package sn.niirma.iam.internal;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import sn.niirma.shared.security.AccessControl;
import sn.niirma.shared.security.CurrentUser;
import sn.niirma.shared.security.HotelScope;

/**
 * Droits de l'utilisateur courant, lus en base à chaque requête (une seule fois par requête) : un
 * changement de rôle, une désactivation ou une rétrogradation s'applique donc immédiatement.
 */
@Service
class AccessControlService implements AccessControl {

    private static final String REQUEST_CACHE_KEY = AccessControlService.class.getName() + ".grants";

    record Grants(boolean superAdmin, Set<UUID> hotels, Map<String, Set<UUID>> hotelsByPermission) {

        static final Grants NONE = new Grants(false, Set.of(), Map.of());
    }

    private final JdbcClient jdbc;

    AccessControlService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public HotelScope accessibleHotels() {
        Grants grants = grants();
        return grants.superAdmin() ? HotelScope.all() : HotelScope.of(grants.hotels());
    }

    @Override
    public HotelScope hotelsWithPermission(String permission) {
        Grants grants = grants();
        return grants.superAdmin()
                ? HotelScope.all()
                : HotelScope.of(grants.hotelsByPermission().getOrDefault(permission, Set.of()));
    }

    Grants grants() {
        CurrentUser user = CurrentUser.current().orElse(null);
        if (user == null) {
            return Grants.NONE;
        }
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        if (request != null && request.getAttribute(REQUEST_CACHE_KEY, RequestAttributes.SCOPE_REQUEST) instanceof Grants cached) {
            return cached;
        }
        Grants grants = load(user.userId());
        if (request != null) {
            request.setAttribute(REQUEST_CACHE_KEY, grants, RequestAttributes.SCOPE_REQUEST);
        }
        return grants;
    }

    private Grants load(UUID userId) {
        Boolean superAdmin = jdbc.sql("SELECT super_admin FROM app_user WHERE id = :id AND status = 'ACTIVE'")
                .param("id", userId)
                .query(Boolean.class)
                .optional()
                .orElse(null);
        if (superAdmin == null) {
            return Grants.NONE;
        }
        if (superAdmin) {
            return new Grants(true, Set.of(), Map.of());
        }

        Set<UUID> hotels = new HashSet<>();
        Map<String, Set<UUID>> byPermission = new HashMap<>();
        jdbc.sql("""
                        SELECT uhr.hotel_id, rp.permission_code
                        FROM user_hotel_role uhr
                        LEFT JOIN role_permission rp ON rp.role_id = uhr.role_id
                        WHERE uhr.user_id = :id
                        """)
                .param("id", userId)
                .query(rs -> {
                    UUID hotelId = rs.getObject("hotel_id", UUID.class);
                    hotels.add(hotelId);
                    String permission = rs.getString("permission_code");
                    if (permission != null) {
                        byPermission.computeIfAbsent(permission, key -> new HashSet<>()).add(hotelId);
                    }
                });
        return new Grants(false, Set.copyOf(hotels), byPermission);
    }
}
