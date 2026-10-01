package sn.niirma.hotel;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.niirma.hotel.internal.Hotel;
import sn.niirma.hotel.internal.HotelRepository;
import sn.niirma.shared.error.BusinessException;

/**
 * Accès en lecture aux hôtels. Le filtrage par hôtels autorisés de l'utilisateur arrive avec
 * l'authentification (itération 1).
 */
@Service
@Transactional(readOnly = true)
public class HotelService {

    private final HotelRepository hotels;

    HotelService(HotelRepository hotels) {
        this.hotels = hotels;
    }

    public Page<HotelView> list(Pageable pageable) {
        return hotels.findAll(pageable).map(HotelService::toView);
    }

    public HotelView get(UUID hotelId) {
        return hotels.findById(hotelId)
                .map(HotelService::toView)
                .orElseThrow(() -> BusinessException.notFound("HOTEL_NOT_FOUND", "Hôtel introuvable."));
    }

    private static HotelView toView(Hotel hotel) {
        return new HotelView(hotel.getId(), hotel.getOrganizationId(), hotel.getCode(), hotel.getName(),
                hotel.getLegalName(), hotel.getAddress(), hotel.getPhone(), hotel.getEmail(),
                hotel.getCurrencyCode(), hotel.getTimezone(), hotel.getLanguage(), hotel.getBusinessDate(),
                hotel.getStatus().name());
    }
}
