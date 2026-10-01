package sn.niirma.rooms.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomTypeRepository extends JpaRepository<RoomType, UUID> {

    List<RoomType> findByHotelIdOrderBySortOrderAscNameAsc(UUID hotelId);

    boolean existsByHotelIdAndCode(UUID hotelId, String code);
}
