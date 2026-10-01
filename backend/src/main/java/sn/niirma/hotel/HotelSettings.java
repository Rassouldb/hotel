package sn.niirma.hotel;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Paramètres d'exploitation d'un hôtel (US-HOTEL-03). Stockés en JSON, toujours lus et écrits sous
 * cette forme typée et validée. Un paramètre absent prend sa valeur par défaut.
 */
public record HotelSettings(
        @NotNull Boolean pricesIncludeTax,
        @NotNull LocalTime checkInTime,
        @NotNull LocalTime checkOutTime,
        @NotNull Boolean inspectionRequired,
        @NotNull Boolean idDocumentRequired,
        @NotNull CheckInGuarantee checkInGuarantee,
        @PositiveOrZero BigDecimal guaranteeAmount,
        @NotNull Boolean walkInFirstNightPayment,
        @NotNull @Valid CancellationPolicy cancellationPolicy,
        @NotNull Map<String, @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal> discountLimits) {

    /** Garantie demandée au check-in (§16.2). */
    public enum CheckInGuarantee { NONE, FIRST_NIGHT, FULL_STAY, FIXED_DEPOSIT }

    /** Politique d'annulation par défaut (§12.5) ; un plan tarifaire pourra la surcharger. */
    public record CancellationPolicy(
            @NotNull @Min(0) @Max(720) Integer freeUntilHours,
            @NotNull @Min(0) @Max(30) Integer lateCancellationPenaltyNights,
            @NotNull @Min(0) @Max(30) Integer noShowPenaltyNights) {
    }

    public static HotelSettings defaults() {
        Map<String, BigDecimal> discountLimits = new LinkedHashMap<>();
        discountLimits.put("RECEPTIONIST", new BigDecimal("10"));
        discountLimits.put("DIRECTOR", new BigDecimal("30"));
        return new HotelSettings(true, LocalTime.of(14, 0), LocalTime.of(12, 0), false, true,
                CheckInGuarantee.FIRST_NIGHT, null, true, new CancellationPolicy(24, 1, 1), discountLimits);
    }

    /** Complète les paramètres absents (anciens enregistrements) avec les valeurs par défaut. */
    public HotelSettings withDefaults() {
        HotelSettings d = defaults();
        return new HotelSettings(
                pricesIncludeTax != null ? pricesIncludeTax : d.pricesIncludeTax(),
                checkInTime != null ? checkInTime : d.checkInTime(),
                checkOutTime != null ? checkOutTime : d.checkOutTime(),
                inspectionRequired != null ? inspectionRequired : d.inspectionRequired(),
                idDocumentRequired != null ? idDocumentRequired : d.idDocumentRequired(),
                checkInGuarantee != null ? checkInGuarantee : d.checkInGuarantee(),
                guaranteeAmount,
                walkInFirstNightPayment != null ? walkInFirstNightPayment : d.walkInFirstNightPayment(),
                cancellationPolicy != null ? cancellationPolicy : d.cancellationPolicy(),
                discountLimits != null ? discountLimits : d.discountLimits());
    }
}
