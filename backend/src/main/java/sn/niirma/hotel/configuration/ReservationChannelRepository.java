package sn.niirma.hotel.configuration;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationChannelRepository extends JpaRepository<ReservationChannel, UUID> {

    List<ReservationChannel> findByHotelIdOrderBySortOrderAscNameAsc(UUID hotelId);

    boolean existsByHotelIdAndCode(UUID hotelId, String code);

    boolean existsByHotelId(UUID hotelId);
}
