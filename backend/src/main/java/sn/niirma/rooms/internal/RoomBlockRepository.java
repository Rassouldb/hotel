package sn.niirma.rooms.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RoomBlockRepository extends JpaRepository<RoomBlock, UUID> {

    /** Blocages de l'hôtel qui chevauchent la période [from, to[. */
    @Query("""
            select b from RoomBlock b
            where b.hotelId = ?1 and b.startDate < ?3 and b.endDate > ?2
            order by b.startDate
            """)
    List<RoomBlock> findOverlapping(UUID hotelId, LocalDate from, LocalDate to);
}
