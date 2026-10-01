package sn.niirma.hotel.configuration;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, UUID> {

    List<PaymentMethod> findByHotelIdOrderBySortOrderAscNameAsc(UUID hotelId);

    boolean existsByHotelIdAndCode(UUID hotelId, String code);

    boolean existsByHotelId(UUID hotelId);
}
