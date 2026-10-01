package sn.niirma.hotel.internal;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import sn.niirma.shared.persistence.BaseEntity;

/**
 * Organisation propriétaire d'un ou plusieurs hôtels (racine du multi-hôtel, §42).
 */
@Entity
@Table(name = "organization")
public class Organization extends BaseEntity {

    public enum Status { ACTIVE, SUSPENDED }

    private String name;

    private String legalName;

    private String country;

    @Enumerated(EnumType.STRING)
    private Status status;

    protected Organization() {
    }

    public Organization(String name, String country) {
        this.name = name;
        this.country = country;
        this.status = Status.ACTIVE;
    }

    public String getName() {
        return name;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getCountry() {
        return country;
    }

    public Status getStatus() {
        return status;
    }
}
