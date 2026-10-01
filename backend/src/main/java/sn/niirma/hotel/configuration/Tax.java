package sn.niirma.hotel.configuration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Converter;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import sn.niirma.hotel.ChargeCategory;
import sn.niirma.shared.persistence.BaseEntity;

/**
 * Taxe de l'hôtel (§8.3). Sa valeur (taux ou montant) est historisée par périodes : un changement
 * de taux clôt la période en cours et en ouvre une nouvelle, sans jamais modifier le passé.
 */
@Entity
@Table(name = "tax")
public class Tax extends BaseEntity {

    public enum Type { PERCENTAGE, FIXED_PER_PERSON_NIGHT, FIXED_PER_UNIT }

    /** Période de validité [validFrom, validTo[ ; validTo nul pour la période en cours. */
    @Embeddable
    public record RatePeriod(LocalDate validFrom, LocalDate validTo, BigDecimal rate, BigDecimal amount) {

        boolean covers(LocalDate date) {
            return !date.isBefore(validFrom) && (validTo == null || date.isBefore(validTo));
        }
    }

    @Converter
    static class CategoriesConverter implements AttributeConverter<Set<ChargeCategory>, String> {

        @Override
        public String convertToDatabaseColumn(Set<ChargeCategory> categories) {
            return categories.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
        }

        @Override
        public Set<ChargeCategory> convertToEntityAttribute(String value) {
            Set<ChargeCategory> categories = EnumSet.noneOf(ChargeCategory.class);
            if (value != null && !value.isBlank()) {
                for (String item : value.split(",")) {
                    categories.add(ChargeCategory.valueOf(item.trim()));
                }
            }
            return categories;
        }
    }

    private UUID hotelId;

    private String code;

    private String name;

    @Enumerated(EnumType.STRING)
    private Type type;

    private boolean vat;

    @Convert(converter = CategoriesConverter.class)
    private Set<ChargeCategory> appliesTo = EnumSet.noneOf(ChargeCategory.class);

    private Integer exemptChildrenUnderAge;

    private boolean exemptResidents;

    private boolean active;

    @ElementCollection
    @CollectionTable(name = "tax_rate_period", joinColumns = @JoinColumn(name = "tax_id"))
    private List<RatePeriod> periods = new ArrayList<>();

    protected Tax() {
    }

    public Tax(UUID hotelId, String code, String name, Type type, boolean vat) {
        this.hotelId = hotelId;
        this.code = code;
        this.name = name;
        this.type = type;
        this.vat = vat;
        this.active = true;
    }

    public void update(String name, Set<ChargeCategory> appliesTo, Integer exemptChildrenUnderAge, boolean exemptResidents,
                       boolean active) {
        this.name = name;
        this.appliesTo = EnumSet.copyOf(appliesTo);
        this.exemptChildrenUnderAge = exemptChildrenUnderAge;
        this.exemptResidents = exemptResidents;
        this.active = active;
    }

    /** Nouvelle valeur à partir de {@code validFrom} : la période ouverte se termine ce jour-là. */
    public void startPeriod(LocalDate validFrom, BigDecimal rate, BigDecimal amount) {
        List<RatePeriod> updated = new ArrayList<>();
        for (RatePeriod period : periods) {
            updated.add(period.validTo() == null
                    ? new RatePeriod(period.validFrom(), validFrom, period.rate(), period.amount())
                    : period);
        }
        updated.add(new RatePeriod(validFrom, null, rate, amount));
        periods.clear();
        periods.addAll(updated);
    }

    public Optional<RatePeriod> openPeriod() {
        return periods.stream().filter(period -> period.validTo() == null).findFirst();
    }

    public Optional<RatePeriod> periodAt(LocalDate date) {
        return periods.stream().filter(period -> period.covers(date)).findFirst();
    }

    public UUID getHotelId() {
        return hotelId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Type getType() {
        return type;
    }

    public boolean isVat() {
        return vat;
    }

    public Set<ChargeCategory> getAppliesTo() {
        return Set.copyOf(appliesTo);
    }

    public Integer getExemptChildrenUnderAge() {
        return exemptChildrenUnderAge;
    }

    public boolean isExemptResidents() {
        return exemptResidents;
    }

    public boolean isActive() {
        return active;
    }

    public List<RatePeriod> getPeriods() {
        return periods.stream().sorted(Comparator.comparing(RatePeriod::validFrom)).toList();
    }
}
