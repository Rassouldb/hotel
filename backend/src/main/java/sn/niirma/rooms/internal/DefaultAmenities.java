package sn.niirma.rooms.internal;

import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import sn.niirma.hotel.HotelCreated;
import sn.niirma.hotel.HotelDirectory;

/**
 * Équipements proposés par défaut à la création d'un hôtel (et au démarrage pour les hôtels qui n'en
 * ont aucun). La liste reste modifiable.
 */
@Component
@Order(20)
class DefaultAmenities implements ApplicationRunner {

    private static final String[][] DEFAULTS = {
            {"AIR_CONDITIONING", "Climatisation"}, {"TV", "Télévision"}, {"WIFI", "Wi-Fi"}, {"MINIBAR", "Minibar"},
            {"SAFE", "Coffre-fort"}, {"SHOWER", "Douche"}, {"BATHTUB", "Baignoire"}, {"BALCONY", "Balcon"},
            {"DESK", "Bureau"}, {"SEA_VIEW", "Vue mer"}};

    private final AmenityRepository amenities;
    private final HotelDirectory hotelDirectory;

    DefaultAmenities(AmenityRepository amenities, HotelDirectory hotelDirectory) {
        this.amenities = amenities;
        this.hotelDirectory = hotelDirectory;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        hotelDirectory.allHotelIds().forEach(this::install);
    }

    @EventListener
    void on(HotelCreated event) {
        install(event.hotelId());
    }

    private void install(UUID hotelId) {
        if (amenities.existsByHotelId(hotelId)) {
            return;
        }
        for (String[] amenity : DEFAULTS) {
            amenities.save(new Amenity(hotelId, amenity[0], amenity[1]));
        }
    }
}
