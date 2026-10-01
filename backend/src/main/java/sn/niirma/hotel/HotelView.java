package sn.niirma.hotel;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Vue publique d'un hôtel, exposée par l'API et utilisable par les autres modules.
 */
public record HotelView(
        UUID id,
        UUID organizationId,
        String code,
        String name,
        String legalName,
        String address,
        String phone,
        String email,
        String currencyCode,
        String timezone,
        String language,
        LocalDate businessDate,
        String status) {
}
