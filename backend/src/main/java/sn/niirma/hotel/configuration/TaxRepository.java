package sn.niirma.hotel.configuration;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxRepository extends JpaRepository<Tax, UUID> {

    List<Tax> findByHotelIdOrderByCodeAsc(UUID hotelId);

    boolean existsByHotelIdAndCode(UUID hotelId, String code);
}
