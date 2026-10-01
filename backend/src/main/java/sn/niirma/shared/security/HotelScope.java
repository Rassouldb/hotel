package sn.niirma.shared.security;

import java.util.Set;
import java.util.UUID;

/**
 * Hôtels sur lesquels porte un droit : tous ceux de l'organisation (super administrateur) ou une liste.
 */
public record HotelScope(boolean allHotels, Set<UUID> hotelIds) {

    public static HotelScope all() {
        return new HotelScope(true, Set.of());
    }

    public static HotelScope of(Set<UUID> hotelIds) {
        return new HotelScope(false, Set.copyOf(hotelIds));
    }

    public boolean includes(UUID hotelId) {
        return allHotels || hotelIds.contains(hotelId);
    }

    public boolean isEmpty() {
        return !allHotels && hotelIds.isEmpty();
    }
}
