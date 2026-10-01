package sn.niirma.hotel.configuration;

import java.math.BigDecimal;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import sn.niirma.hotel.HotelCreated;
import sn.niirma.hotel.HotelDirectory;
import sn.niirma.hotel.configuration.PaymentMethod.Type;
import sn.niirma.hotel.configuration.ReasonCode.Domain;

/**
 * Paramétrage par défaut d'un hôtel : moyens de paiement, canaux et motifs. Installé à la création
 * de l'hôtel, et au démarrage pour les hôtels qui n'en ont pas encore. Une liste déjà présente n'est
 * jamais modifiée. Aucune taxe n'est créée : elles dépendent du pays et se paramètrent à la main.
 */
@Component
@Order(10)
class DefaultConfiguration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultConfiguration.class);

    private final PaymentMethodRepository paymentMethods;
    private final ReservationChannelRepository channels;
    private final ReasonCodeRepository reasons;
    private final HotelDirectory hotelDirectory;

    DefaultConfiguration(PaymentMethodRepository paymentMethods, ReservationChannelRepository channels,
                         ReasonCodeRepository reasons, HotelDirectory hotelDirectory) {
        this.paymentMethods = paymentMethods;
        this.channels = channels;
        this.reasons = reasons;
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

    void install(UUID hotelId) {
        if (!paymentMethods.existsByHotelId(hotelId)) {
            int order = 0;
            paymentMethods.save(new PaymentMethod(hotelId, "CASH", "Espèces", Type.CASH, true, false, true, order++));
            paymentMethods.save(new PaymentMethod(hotelId, "CARD", "Carte bancaire", Type.CARD, false, true, true, order++));
            paymentMethods.save(new PaymentMethod(hotelId, "TRANSFER", "Virement", Type.TRANSFER, false, true, true, order++));
            paymentMethods.save(new PaymentMethod(hotelId, "CHEQUE", "Chèque", Type.CHEQUE, false, true, true, order++));
            paymentMethods.save(new PaymentMethod(hotelId, "ORANGE_MONEY", "Orange Money", Type.MOBILE_MONEY, false, true, true, order++));
            paymentMethods.save(new PaymentMethod(hotelId, "WAVE", "Wave", Type.MOBILE_MONEY, false, true, true, order++));
            paymentMethods.save(new PaymentMethod(hotelId, "MTN_MOMO", "MTN MoMo", Type.MOBILE_MONEY, false, true, false, order++));
            paymentMethods.save(new PaymentMethod(hotelId, "MOOV_MONEY", "Moov Money", Type.MOBILE_MONEY, false, true, false, order));
            log.info("Moyens de paiement par défaut installés pour l'hôtel {}", hotelId);
        }
        if (!channels.existsByHotelId(hotelId)) {
            int order = 0;
            channels.save(new ReservationChannel(hotelId, "WALK_IN", "Réception (sans réservation)", null, order++));
            channels.save(new ReservationChannel(hotelId, "PHONE", "Téléphone", null, order++));
            channels.save(new ReservationChannel(hotelId, "EMAIL", "Email", null, order++));
            channels.save(new ReservationChannel(hotelId, "WEBSITE", "Site web", null, order++));
            channels.save(new ReservationChannel(hotelId, "BOOKING", "Booking.com", new BigDecimal("15"), order++));
            channels.save(new ReservationChannel(hotelId, "EXPEDIA", "Expedia", new BigDecimal("18"), order++));
            channels.save(new ReservationChannel(hotelId, "AGENCY", "Agence de voyage", new BigDecimal("10"), order++));
            channels.save(new ReservationChannel(hotelId, "COMPANY", "Entreprise", null, order));
        }
        if (!reasons.existsByHotelId(hotelId)) {
            reason(hotelId, Domain.CANCELLATION, "CUSTOMER_REQUEST", "Demande du client", 0);
            reason(hotelId, Domain.CANCELLATION, "TRAVEL_CANCELLED", "Voyage annulé", 1);
            reason(hotelId, Domain.CANCELLATION, "DUPLICATE", "Réservation en double", 2);
            reason(hotelId, Domain.CANCELLATION, "OPTION_EXPIRED", "Option expirée", 3);
            reason(hotelId, Domain.DISCOUNT, "LOYAL_CUSTOMER", "Client fidèle", 0);
            reason(hotelId, Domain.DISCOUNT, "SERVICE_ISSUE", "Geste commercial (incident)", 1);
            reason(hotelId, Domain.DISCOUNT, "LONG_STAY", "Long séjour", 2);
            reason(hotelId, Domain.ROOM_CHANGE, "BREAKDOWN", "Panne dans la chambre", 0);
            reason(hotelId, Domain.ROOM_CHANGE, "CUSTOMER_REQUEST", "Demande du client", 1);
            reason(hotelId, Domain.ROOM_CHANGE, "UPGRADE", "Surclassement", 2);
            reason(hotelId, Domain.ROOM_BLOCK, "PLUMBING", "Plomberie", 0);
            reason(hotelId, Domain.ROOM_BLOCK, "ELECTRICITY", "Électricité", 1);
            reason(hotelId, Domain.ROOM_BLOCK, "AIR_CONDITIONING", "Climatisation", 2);
            reason(hotelId, Domain.ROOM_BLOCK, "RENOVATION", "Travaux de rénovation", 3);
            reason(hotelId, Domain.ROOM_BLOCK, "HOUSE_USE", "Usage interne", 4);
            reason(hotelId, Domain.ADJUSTMENT, "ENTRY_ERROR", "Erreur de saisie", 0);
            reason(hotelId, Domain.HOUSEKEEPING, "NOT_CLEAN", "Chambre mal nettoyée", 0);
            reason(hotelId, Domain.HOUSEKEEPING, "USED_AFTER_CLEANING", "Utilisée après nettoyage", 1);
            reason(hotelId, Domain.HOUSEKEEPING, "DAMAGE", "Dégât constaté", 2);
        }
    }

    private void reason(UUID hotelId, Domain domain, String code, String label, int order) {
        reasons.save(new ReasonCode(hotelId, domain, code, label, order));
    }
}
