package sn.niirma.rooms.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, UUID> {

    List<Room> findByHotelId(UUID hotelId);

    boolean existsByHotelIdAndNumber(UUID hotelId, String number);

    List<Room> findByHotelIdAndNumberIn(UUID hotelId, Collection<String> numbers);

    long countByRoomTypeIdAndActiveTrue(UUID roomTypeId);
}
