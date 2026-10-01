package sn.niirma.hotel.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HotelRepository extends JpaRepository<Hotel, UUID> {

    Page<Hotel> findByOrganizationId(UUID organizationId, Pageable pageable);

    List<Hotel> findByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);

    Page<Hotel> findByOrganizationIdAndIdIn(UUID organizationId, Collection<UUID> ids, Pageable pageable);
}
