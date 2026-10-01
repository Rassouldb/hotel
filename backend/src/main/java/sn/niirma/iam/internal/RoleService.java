package sn.niirma.iam.internal;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.niirma.audit.AuditEntry;
import sn.niirma.audit.AuditService;
import sn.niirma.iam.Permission;
import sn.niirma.shared.error.BusinessException;
import sn.niirma.shared.security.CurrentUser;

/**
 * Rôles de l'organisation et catalogue des permissions (US-IAM-02). Les modifications s'appliquent
 * dès la requête suivante des utilisateurs concernés, les droits étant relus à chaque requête.
 */
@Service
@Transactional
class RoleService {

    record RoleRequest(@NotBlank @Size(max = 100) String name, @Size(max = 255) String description,
                       @NotNull Set<String> permissions, Long version) {
    }

    record RoleResponse(UUID id, String code, String name, String description, boolean system,
                        List<String> permissions, long userCount, Long version) {
    }

    record PermissionResponse(String code, String module, String moduleLabel, String description) {
    }

    private final RoleRepository roles;
    private final UserRepository users;
    private final AuditService audit;

    RoleService(RoleRepository roles, UserRepository users, AuditService audit) {
        this.roles = roles;
        this.users = users;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> list() {
        return roles.findByOrganizationIdOrderByNameAsc(organizationId()).stream().map(this::toResponse).toList();
    }

    public List<PermissionResponse> permissions() {
        return Arrays.stream(Permission.values())
                .map(p -> new PermissionResponse(p.name(), p.module().name(), p.module().label(), p.description()))
                .toList();
    }

    public RoleResponse create(RoleRequest request) {
        UUID organizationId = organizationId();
        ensureNameAvailable(organizationId, request.name(), null);
        Set<String> permissions = validatePermissions(request.permissions());
        Role role = new Role(organizationId, uniqueCode(organizationId, request.name()), request.name().trim(),
                blankToNull(request.description()), false, permissions);
        roles.saveAndFlush(role);
        audit.record(AuditEntry.of("ROLE_CREATE", "Role", role.getId())
                .withChange(null, Map.of("name", role.getName(), "permissions", new TreeSet<>(permissions))));
        return toResponse(role);
    }

    public RoleResponse update(UUID roleId, RoleRequest request) {
        Role role = load(roleId);
        if (request.version() != null && !Objects.equals(request.version(), role.getVersion())) {
            throw BusinessException.conflict("VERSION_CONFLICT", "Ce rôle a été modifié entre-temps. Rechargez puis réessayez.");
        }
        ensureNameAvailable(role.getOrganizationId(), request.name(), role.getId());
        Set<String> permissions = validatePermissions(request.permissions());

        Set<String> added = new TreeSet<>(permissions);
        added.removeAll(role.getPermissions());
        Set<String> removed = new TreeSet<>(role.getPermissions());
        removed.removeAll(permissions);
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        if (!role.getName().equals(request.name().trim())) {
            before.put("name", role.getName());
            after.put("name", request.name().trim());
        }
        if (!removed.isEmpty()) {
            before.put("permissions", removed);
        }
        if (!added.isEmpty()) {
            after.put("permissions", added);
        }

        role.update(request.name().trim(), blankToNull(request.description()), permissions);
        roles.saveAndFlush(role);
        audit.record(AuditEntry.of("ROLE_UPDATE", "Role", role.getId()).withChange(before, after));
        return toResponse(role);
    }

    public void delete(UUID roleId) {
        Role role = load(roleId);
        if (role.isSystem()) {
            throw BusinessException.conflict("SYSTEM_ROLE", "Un rôle par défaut ne peut pas être supprimé.");
        }
        if (users.countAssignmentsOfRole(role.getId()) > 0) {
            throw BusinessException.conflict("ROLE_IN_USE", "Ce rôle est encore attribué à des utilisateurs.");
        }
        roles.delete(role);
        audit.record(AuditEntry.of("ROLE_DELETE", "Role", role.getId())
                .withChange(Map.of("name", role.getName(), "permissions", new TreeSet<>(role.getPermissions())), null));
    }

    private Role load(UUID roleId) {
        return roles.findById(roleId)
                .filter(role -> role.getOrganizationId().equals(organizationId()))
                .orElseThrow(() -> BusinessException.notFound("ROLE_NOT_FOUND", "Rôle introuvable."));
    }

    private void ensureNameAvailable(UUID organizationId, String name, UUID excludedRoleId) {
        boolean taken = roles.findByOrganizationIdOrderByNameAsc(organizationId).stream()
                .anyMatch(r -> !r.getId().equals(excludedRoleId) && r.getName().equalsIgnoreCase(name.trim()));
        if (taken) {
            throw BusinessException.conflict("ROLE_NAME_EXISTS", "Un rôle porte déjà ce nom.");
        }
    }

    private static Set<String> validatePermissions(Set<String> requested) {
        Set<String> known = Arrays.stream(Permission.values()).map(Enum::name).collect(Collectors.toSet());
        Set<String> unknown = new TreeSet<>(requested);
        unknown.removeAll(known);
        if (!unknown.isEmpty()) {
            throw BusinessException.unprocessable("UNKNOWN_PERMISSION", "Permissions inconnues : " + unknown);
        }
        return new HashSet<>(requested);
    }

    private String uniqueCode(UUID organizationId, String name) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_|_$", "");
        base = base.isEmpty() ? "ROLE" : base.substring(0, Math.min(base.length(), 34));
        String code = base;
        for (int i = 2; roles.existsByOrganizationIdAndCode(organizationId, code); i++) {
            code = base + "_" + i;
        }
        return code;
    }

    private RoleResponse toResponse(Role role) {
        return new RoleResponse(role.getId(), role.getCode(), role.getName(), role.getDescription(), role.isSystem(),
                role.getPermissions().stream().sorted().toList(), users.countAssignmentsOfRole(role.getId()),
                role.getVersion());
    }

    private static UUID organizationId() {
        return CurrentUser.require().organizationId();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
