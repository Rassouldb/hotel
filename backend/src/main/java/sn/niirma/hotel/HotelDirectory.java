package sn.niirma.hotel;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.niirma.hotel.internal.Hotel;
import sn.niirma.hotel.internal.HotelRepository;
import sn.niirma.hotel.internal.Organization;
import sn.niirma.hotel.internal.OrganizationRepository;

/**
 * Accès interne aux organisations et hôtels pour les autres modules, sans contrôle des droits de
 * l'utilisateur courant (ce contrôle est fait par l'appelant).
 */
@Service
@Transactional(readOnly = true)
public class HotelDirectory {

    private final HotelRepository hotels;
    private final OrganizationRepository organizations;

    HotelDirectory(HotelRepository hotels, OrganizationRepository organizations) {
        this.hotels = hotels;
        this.organizations = organizations;
    }

    /** Organisation propriétaire de l'hôtel, vide si l'hôtel n'existe pas. */
    public Optional<UUID> organizationOf(UUID hotelId) {
        return hotels.findById(hotelId).map(Hotel::getOrganizationId);
    }

    /** Hôtels de l'organisation, dans l'ordre de création. */
    public List<UUID> hotelIdsOf(UUID organizationId) {
        return hotels.findByOrganizationIdOrderByCreatedAtAsc(organizationId).stream().map(Hotel::getId).toList();
    }

    public List<UUID> organizationIds() {
        return organizations.findAll().stream().map(Organization::getId).toList();
    }

    /** Date d'exploitation courante de l'hôtel. */
    public LocalDate businessDate(UUID hotelId) {
        return hotels.findById(hotelId).map(Hotel::getBusinessDate)
                .orElseThrow(() -> new IllegalArgumentException("Hôtel inconnu : " + hotelId));
    }

    /** Tous les hôtels, toutes organisations confondues (initialisation au démarrage). */
    public List<UUID> allHotelIds() {
        return hotels.findAll().stream().map(Hotel::getId).toList();
    }

    public Optional<UUID> findFirstOrganizationId() {
        return organizations.findFirstByOrderByCreatedAtAsc().map(Organization::getId);
    }

    @Transactional
    public UUID createOrganization(String name, String country) {
        return organizations.save(new Organization(name, country)).getId();
    }
}
