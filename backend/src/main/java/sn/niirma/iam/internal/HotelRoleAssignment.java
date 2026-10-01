package sn.niirma.iam.internal;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Affectation d'un rôle à l'utilisateur dans un hôtel.
 */
@Embeddable
public record HotelRoleAssignment(@Column(name = "hotel_id") UUID hotelId, @Column(name = "role_id") UUID roleId) {
}
