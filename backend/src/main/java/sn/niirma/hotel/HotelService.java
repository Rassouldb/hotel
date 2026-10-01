package sn.niirma.hotel;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.niirma.hotel.internal.Hotel;
import sn.niirma.hotel.internal.HotelRepository;
import sn.niirma.shared.error.BusinessException;
import sn.niirma.shared.security.AccessControl;
import sn.niirma.shared.security.CurrentUser;
import sn.niirma.shared.security.HotelScope;

/**
 * Accès en lecture aux hôtels pour l'utilisateur courant : uniquement les hôtels de son
 * organisation auxquels il est affecté (tous pour un super administrateur).
 */
@Service
@Transactional(readOnly = true)
public class HotelService {

    private final HotelRepository hotels;
    private final AccessControl accessControl;

    HotelService(HotelRepository hotels, AccessControl accessControl) {
        this.hotels = hotels;
        this.accessControl = accessControl;
    }

    public Page<HotelView> list(Pageable pageable) {
        UUID organizationId = CurrentUser.require().organizationId();
        HotelScope scope = accessControl.accessibleHotels();
        if (scope.isEmpty()) {
            return Page.empty(pageable);
        }
        Page<Hotel> page = scope.allHotels()
                ? hotels.findByOrganizationId(organizationId, pageable)
                : hotels.findByOrganizationIdAndIdIn(organizationId, scope.hotelIds(), pageable);
        return page.map(HotelService::toView);
    }

    public HotelView get(UUID hotelId) {
        UUID organizationId = CurrentUser.require().organizationId();
        return hotels.findById(hotelId)
                .filter(hotel -> hotel.getOrganizationId().equals(organizationId))
                .filter(hotel -> accessControl.canAccessHotel(hotel.getId()))
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
