package sn.niirma.iam.internal;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import sn.niirma.shared.persistence.BaseEntity;

/**
 * Compte d'un membre du personnel. L'identifiant de connexion est le nom d'utilisateur ; l'email
 * est facultatif et sert à la réinitialisation du mot de passe.
 */
@Entity
@Table(name = "app_user")
public class User extends BaseEntity {

    public enum Status { ACTIVE, DISABLED }

    public static final int MAX_FAILED_LOGINS = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private UUID organizationId;

    private String username;

    private String firstName;

    private String lastName;

    private String email;

    private String phone;

    private String language;

    private String passwordHash;

    private boolean mustChangePassword;

    private boolean superAdmin;

    @Enumerated(EnumType.STRING)
    private Status status;

    private int failedLoginCount;

    private OffsetDateTime lockedUntil;

    private OffsetDateTime lastLoginAt;

    private OffsetDateTime passwordChangedAt;

    @ElementCollection
    @CollectionTable(name = "user_hotel_role", joinColumns = @JoinColumn(name = "user_id"))
    private Set<HotelRoleAssignment> assignments = new HashSet<>();

    protected User() {
    }

    public User(UUID organizationId, String username, String firstName, String lastName, String temporaryPasswordHash) {
        this.organizationId = organizationId;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.language = "fr";
        this.passwordHash = temporaryPasswordHash;
        this.mustChangePassword = true;
        this.status = Status.ACTIVE;
    }

    public boolean isLocked(OffsetDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Enregistre un échec ; renvoie vrai si le compte vient d'être bloqué. */
    public boolean registerFailedLogin(OffsetDateTime now) {
        failedLoginCount++;
        if (failedLoginCount >= MAX_FAILED_LOGINS) {
            failedLoginCount = 0;
            lockedUntil = now.plus(LOCK_DURATION);
            return true;
        }
        return false;
    }

    public void registerSuccessfulLogin(OffsetDateTime now) {
        failedLoginCount = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    public void changePassword(String newHash, OffsetDateTime now) {
        passwordHash = newHash;
        mustChangePassword = false;
        passwordChangedAt = now;
    }

    public void assignTemporaryPassword(String temporaryHash, OffsetDateTime now) {
        passwordHash = temporaryHash;
        mustChangePassword = true;
        passwordChangedAt = now;
        failedLoginCount = 0;
        lockedUntil = null;
    }

    public void updateProfile(String firstName, String lastName, String phone, String language) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.language = language;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setSuperAdmin(boolean superAdmin) {
        this.superAdmin = superAdmin;
    }

    public void replaceAssignments(Set<HotelRoleAssignment> newAssignments) {
        assignments.clear();
        assignments.addAll(newAssignments);
    }

    public void disable() {
        status = Status.DISABLED;
    }

    public void enable() {
        status = Status.ACTIVE;
        failedLoginCount = 0;
        lockedUntil = null;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getUsername() {
        return username;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getLanguage() {
        return language;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public boolean isSuperAdmin() {
        return superAdmin;
    }

    public Status getStatus() {
        return status;
    }

    public OffsetDateTime getLockedUntil() {
        return lockedUntil;
    }

    public OffsetDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public Set<HotelRoleAssignment> getAssignments() {
        return Set.copyOf(assignments);
    }
}
