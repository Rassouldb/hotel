package sn.niirma.rooms.internal;

import java.math.BigDecimal;
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

/** Catégorie de chambre (§9.1) : capacité, prix de base (valeur de repli de la tarification), équipements. */
@Entity
@Table(name = "room_type")
public class RoomType extends BaseEntity {

    private UUID hotelId;

    private String code;

    private String name;

    private String description;

    private int baseOccupancy;

    private int maxOccupancy;

    private int maxAdults;

    private int maxChildren;

    private BigDecimal basePrice;

    private int sortOrder;

    private boolean active;

    @ElementCollection
    @CollectionTable(name = "room_type_amenity", joinColumns = @JoinColumn(name = "room_type_id"))
    @Column(name = "amenity_id")
    private Set<UUID> amenityIds = new HashSet<>();

    protected RoomType() {
    }

    public RoomType(UUID hotelId, String code) {
        this.hotelId = hotelId;
        this.code = code;
        this.active = true;
    }

    public void update(String name, String description, int baseOccupancy, int maxOccupancy, int maxAdults, int maxChildren,
                       BigDecimal basePrice, int sortOrder, boolean active, Set<UUID> amenityIds) {
        this.name = name;
        this.description = description;
        this.baseOccupancy = baseOccupancy;
        this.maxOccupancy = maxOccupancy;
        this.maxAdults = maxAdults;
        this.maxChildren = maxChildren;
        this.basePrice = basePrice;
        this.sortOrder = sortOrder;
        this.active = active;
        this.amenityIds.clear();
        this.amenityIds.addAll(amenityIds);
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

    public String getDescription() {
        return description;
    }

    public int getBaseOccupancy() {
        return baseOccupancy;
    }

    public int getMaxOccupancy() {
        return maxOccupancy;
    }

    public int getMaxAdults() {
        return maxAdults;
    }

    public int getMaxChildren() {
        return maxChildren;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isActive() {
        return active;
    }

    public Set<UUID> getAmenityIds() {
        return Set.copyOf(amenityIds);
    }
}
