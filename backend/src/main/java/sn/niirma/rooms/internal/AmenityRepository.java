package sn.niirma.rooms.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AmenityRepository extends JpaRepository<Amenity, UUID> {

    List<Amenity> findByHotelIdOrderByNameAsc(UUID hotelId);

    boolean existsByHotelIdAndCode(UUID hotelId, String code);

    boolean existsByHotelId(UUID hotelId);
}
