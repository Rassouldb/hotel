package sn.niirma.hotel.internal;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import sn.niirma.shared.persistence.BaseEntity;

/**
 * Établissement hôtelier. Porte sa devise, son fuseau horaire et sa date d'exploitation, qui
 * n'avance qu'à la clôture journalière (§17).
 */
@Entity
@Table(name = "hotel")
public class Hotel extends BaseEntity {

    public enum Status { ACTIVE, INACTIVE }

    private UUID organizationId;

    private String code;

    private String name;

    private String legalName;

    private String address;

    private String phone;

    private String email;

    private String logoUrl;

    /** Identifiants légaux (RCCM, NINEA, IFU…) au format JSON, libellés selon le pays. */
    @JdbcTypeCode(SqlTypes.JSON)
    private String legalIds;

    private String currencyCode;

    private String timezone;

    private String language;

    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    private Status status;

    protected Hotel() {
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

    public String getLegalName() {
        return legalName;
    }

    public String getAddress() {
        return address;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public String getLegalIds() {
        return legalIds;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getLanguage() {
        return language;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public Status getStatus() {
        return status;
    }
}
