package sn.niirma.hotel.internal;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import org.hibernate.annotations.Formula;
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

    /** Paramètres d'exploitation (JSON), voir {@code HotelSettings}. */
    @JdbcTypeCode(SqlTypes.JSON)
    private String settings;

    /** Le logo lui-même n'est jamais chargé avec l'hôtel : seule sa présence l'est. */
    @Formula("(logo IS NOT NULL)")
    private boolean hasLogo;

    protected Hotel() {
    }

    public Hotel(UUID organizationId, String code, String name, String currencyCode, String timezone, String language,
                 LocalDate businessDate, String settings) {
        this.organizationId = organizationId;
        this.code = code;
        this.name = name;
        this.currencyCode = currencyCode;
        this.timezone = timezone;
        this.language = language;
        this.businessDate = businessDate;
        this.settings = settings;
        this.status = Status.ACTIVE;
    }

    public void updateInformation(String name, String legalName, String address, String phone, String email,
                                  String legalIds, String language) {
        this.name = name;
        this.legalName = legalName;
        this.address = address;
        this.phone = phone;
        this.email = email;
        this.legalIds = legalIds;
        this.language = language;
    }

    public void updateSettings(String settings) {
        this.settings = settings;
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

    public String getSettings() {
        return settings;
    }

    public boolean hasLogo() {
        return hasLogo;
    }
}
