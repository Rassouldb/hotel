package sn.niirma.iam.internal;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import sn.niirma.shared.persistence.BaseEntity;

/**
 * Ensemble de permissions défini au niveau de l'organisation. Les rôles système (créés par défaut)
 * ne peuvent pas être supprimés mais leurs permissions sont modifiables.
 */
@Entity
@Table(name = "role")
public class Role extends BaseEntity {

    private UUID organizationId;

    private String code;

    private String name;

    private String description;

    private boolean system;

    @ElementCollection
    @CollectionTable(name = "role_permission", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission_code")
    private Set<String> permissions = new HashSet<>();

    protected Role() {
    }

    public Role(UUID organizationId, String code, String name, String description, boolean system, Set<String> permissions) {
        this.organizationId = organizationId;
        this.code = code;
        this.name = name;
        this.description = description;
        this.system = system;
        this.permissions.addAll(permissions);
    }

    public void update(String name, String description, Set<String> permissions) {
        this.name = name;
        this.description = description;
        this.permissions.clear();
        this.permissions.addAll(permissions);
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSystem() {
        return system;
    }

    public Set<String> getPermissions() {
        return Set.copyOf(permissions);
    }
}
