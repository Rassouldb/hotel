package sn.niirma.audit;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import sn.niirma.shared.api.ApiResponse;
import sn.niirma.shared.api.PageMeta;
import sn.niirma.shared.security.AccessControl;
import sn.niirma.shared.security.CurrentUser;
import sn.niirma.shared.security.HotelScope;
import sn.niirma.shared.security.RequiresPermission;

/**
 * Consultation du journal d'audit (US-AUD-01). Un super administrateur voit toute son organisation ;
 * les autres voient les opérations des hôtels où ils détiennent {@code AUDIT_VIEW}.
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
class AuditLogController {

    private static final int MAX_PAGE_SIZE = 100;

    record AuditLogResponse(UUID id, OffsetDateTime occurredAt, UUID hotelId, UUID userId, String userName,
                            String action, String entityType, String entityId, JsonNode oldValue, JsonNode newValue,
                            String reason, String ip, String result, String errorCode, String traceId) {
    }

    private final JdbcClient jdbc;
    private final AccessControl accessControl;
    private final ObjectMapper objectMapper;

    AuditLogController(JdbcClient jdbc, AccessControl accessControl, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.accessControl = accessControl;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    @RequiresPermission("AUDIT_VIEW")
    ApiResponse<List<AuditLogResponse>> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) UUID hotelId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String result,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "25") int size) {
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        int pageNumber = Math.max(page, 1);

        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        conditions.add("organization_id = :organizationId");
        params.put("organizationId", CurrentUser.require().organizationId());

        HotelScope scope = accessControl.hotelsWithPermission("AUDIT_VIEW");
        if (!scope.allHotels()) {
            conditions.add("hotel_id IN (:scopeHotels)");
            params.put("scopeHotels", scope.hotelIds());
        }
        addIfPresent(conditions, params, "occurred_at >= :from", "from", from);
        addIfPresent(conditions, params, "occurred_at < :to", "to", to);
        addIfPresent(conditions, params, "hotel_id = :hotelId", "hotelId", hotelId);
        addIfPresent(conditions, params, "user_id = :userId", "userId", userId);
        addIfPresent(conditions, params, "action = :action", "action", blankToNull(action));
        addIfPresent(conditions, params, "entity_type = :entityType", "entityType", blankToNull(entityType));
        addIfPresent(conditions, params, "entity_id = :entityId", "entityId", blankToNull(entityId));
        addIfPresent(conditions, params, "result = :result", "result", blankToNull(result));
        String where = " WHERE " + String.join(" AND ", conditions);

        long total = jdbc.sql("SELECT count(*) FROM audit_log" + where)
                .paramSource(new MapSqlParameterSource(params))
                .query(Long.class)
                .single();
        params.put("limit", pageSize);
        params.put("offset", (long) (pageNumber - 1) * pageSize);
        List<AuditLogResponse> rows = jdbc.sql("""
                        SELECT id, occurred_at, hotel_id, user_id, user_name, action, entity_type, entity_id,
                               old_value::text AS old_value, new_value::text AS new_value, reason, ip, result,
                               error_code, trace_id
                        FROM audit_log""" + where + " ORDER BY occurred_at DESC, id DESC LIMIT :limit OFFSET :offset")
                .paramSource(new MapSqlParameterSource(params))
                .query((rs, rowNum) -> new AuditLogResponse(
                        rs.getObject("id", UUID.class),
                        rs.getObject("occurred_at", OffsetDateTime.class),
                        rs.getObject("hotel_id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("user_name"),
                        rs.getString("action"),
                        rs.getString("entity_type"),
                        rs.getString("entity_id"),
                        toJson(rs.getString("old_value")),
                        toJson(rs.getString("new_value")),
                        rs.getString("reason"),
                        rs.getString("ip"),
                        rs.getString("result"),
                        rs.getString("error_code"),
                        rs.getString("trace_id")))
                .list();
        return ApiResponse.of(rows, new PageMeta(pageNumber, pageSize, total));
    }

    private JsonNode toJson(String value) {
        return value == null ? null : objectMapper.readTree(value);
    }

    private static void addIfPresent(List<String> conditions, Map<String, Object> params, String condition,
                                     String name, Object value) {
        if (value != null) {
            conditions.add(condition);
            params.put(name, value);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
