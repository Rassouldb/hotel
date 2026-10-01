package sn.niirma.rooms.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import sn.niirma.shared.persistence.UuidV7;
import sn.niirma.shared.security.CurrentUser;

/**
 * Historique des statuts de chambre (§9.3) : chaque changement d'une des trois dimensions, avec son
 * auteur, sa date d'exploitation et son motif.
 */
@Component
public class RoomStatusHistory {

    public enum Dimension { OCCUPANCY, HOUSEKEEPING, TECHNICAL }

    public record Entry(UUID id, Dimension dimension, String oldValue, String newValue, String reason,
                        LocalDate businessDate, OffsetDateTime occurredAt, String userName) {
    }

    private final JdbcClient jdbc;
    private final Clock clock;

    RoomStatusHistory(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public void record(UUID hotelId, UUID roomId, Dimension dimension, String oldValue, String newValue, String reason,
                       LocalDate businessDate) {
        jdbc.sql("""
                        INSERT INTO room_status_history (id, hotel_id, room_id, dimension, old_value, new_value, reason,
                                                         business_date, occurred_at, user_id, user_name)
                        VALUES (:id, :hotelId, :roomId, :dimension, :oldValue, :newValue, :reason,
                                :businessDate, :occurredAt, :userId, :userName)
                        """)
                .param("id", UuidV7.generate())
                .param("hotelId", hotelId)
                .param("roomId", roomId)
                .param("dimension", dimension.name())
                .param("oldValue", oldValue)
                .param("newValue", newValue)
                .param("reason", reason)
                .param("businessDate", businessDate)
                .param("occurredAt", OffsetDateTime.now(clock))
                .param("userId", CurrentUser.current().map(CurrentUser::userId).orElse(null))
                .param("userName", CurrentUser.current().map(CurrentUser::username).orElse(null))
                .update();
    }

    public List<Entry> of(UUID roomId, int limit) {
        return jdbc.sql("""
                        SELECT id, dimension, old_value, new_value, reason, business_date, occurred_at, user_name
                        FROM room_status_history WHERE room_id = :roomId
                        ORDER BY occurred_at DESC, id DESC LIMIT :limit
                        """)
                .param("roomId", roomId)
                .param("limit", limit)
                .query((rs, rowNum) -> new Entry(
                        rs.getObject("id", UUID.class),
                        Dimension.valueOf(rs.getString("dimension")),
                        rs.getString("old_value"),
                        rs.getString("new_value"),
                        rs.getString("reason"),
                        rs.getObject("business_date", LocalDate.class),
                        rs.getObject("occurred_at", OffsetDateTime.class),
                        rs.getString("user_name")))
                .list();
    }
}
