package sn.niirma.shared.security;

import java.util.UUID;

/**
 * Droits de l'utilisateur courant (§7.3) : permissions par hôtel, issues de ses affectations
 * (utilisateur, hôtel, rôle). Le super administrateur a toutes les permissions sur tous les hôtels
 * de son organisation. Implémenté par le module iam.
 */
public interface AccessControl {

    /** Hôtels auxquels l'utilisateur est affecté, quel que soit le rôle. */
    HotelScope accessibleHotels();

    /** Hôtels sur lesquels l'utilisateur détient la permission. */
    HotelScope hotelsWithPermission(String permission);

    default boolean canAccessHotel(UUID hotelId) {
        return accessibleHotels().includes(hotelId);
    }

    default boolean hasPermission(String permission, UUID hotelId) {
        return hotelsWithPermission(permission).includes(hotelId);
    }

    default boolean hasPermissionInAnyHotel(String permission) {
        return !hotelsWithPermission(permission).isEmpty();
    }
}
