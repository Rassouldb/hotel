package sn.niirma.hotel.configuration;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReasonCodeRepository extends JpaRepository<ReasonCode, UUID> {

    List<ReasonCode> findByHotelIdOrderByDomainAscSortOrderAscLabelAsc(UUID hotelId);

    boolean existsByHotelIdAndDomainAndCode(UUID hotelId, ReasonCode.Domain domain, String code);

    boolean existsByHotelId(UUID hotelId);
}
