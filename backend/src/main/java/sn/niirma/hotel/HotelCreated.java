package sn.niirma.hotel;

import java.util.UUID;

/**
 * Publié à la création d'un hôtel, pour que chaque module y installe son paramétrage par défaut.
 */
public record HotelCreated(UUID hotelId, UUID organizationId) {
}
