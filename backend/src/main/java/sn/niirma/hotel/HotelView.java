package sn.niirma.hotel;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Vue publique d'un hôtel, exposée par l'API et utilisable par les autres modules.
 *
 * @param legalIds identifiants légaux affichés sur les factures (libellé → valeur : RCCM, NINEA, IFU…)
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
        Map<String, String> legalIds,
        String currencyCode,
        String timezone,
        String language,
        LocalDate businessDate,
        String status,
        boolean hasLogo,
        Long version) {
}
