package sn.niirma.hotel.configuration;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import sn.niirma.hotel.configuration.ReferenceDataService.ChannelRequest;
import sn.niirma.hotel.configuration.ReferenceDataService.ChannelResponse;
import sn.niirma.hotel.configuration.ReferenceDataService.PaymentMethodRequest;
import sn.niirma.hotel.configuration.ReferenceDataService.PaymentMethodResponse;
import sn.niirma.hotel.configuration.ReferenceDataService.ReasonRequest;
import sn.niirma.hotel.configuration.ReferenceDataService.ReasonResponse;
import sn.niirma.hotel.configuration.TaxService.CreateTaxRequest;
import sn.niirma.hotel.configuration.TaxService.NewRateRequest;
import sn.niirma.hotel.configuration.TaxService.TaxResponse;
import sn.niirma.hotel.configuration.TaxService.UpdateTaxRequest;
import sn.niirma.shared.api.ApiResponse;
import sn.niirma.shared.security.RequiresPermission;

/**
 * Paramétrage de l'hôtel : taxes, moyens de paiement, canaux de réservation, motifs.
 * Consultable par tout membre de l'hôtel (ces listes alimentent les écrans de la réception).
 */
@RestController
@RequestMapping("/api/v1/hotels/{hotelId}")
class ConfigurationController {

    private final TaxService taxes;
    private final ReferenceDataService referenceData;

    ConfigurationController(TaxService taxes, ReferenceDataService referenceData) {
        this.taxes = taxes;
        this.referenceData = referenceData;
    }

    // --- Taxes ----------------------------------------------------------------------------------------------

    @GetMapping("/taxes")
    @RequiresPermission("HOTEL_VIEW")
    ApiResponse<List<TaxResponse>> taxes(@PathVariable UUID hotelId) {
        return ApiResponse.of(taxes.list(hotelId));
    }

    @PostMapping("/taxes")
    @RequiresPermission("TAX_MANAGE")
    ResponseEntity<ApiResponse<TaxResponse>> createTax(@PathVariable UUID hotelId, @Valid @RequestBody CreateTaxRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(taxes.create(hotelId, request)));
    }

    @PutMapping("/taxes/{taxId}")
    @RequiresPermission("TAX_MANAGE")
    ApiResponse<TaxResponse> updateTax(@PathVariable UUID hotelId, @PathVariable UUID taxId,
                                       @Valid @RequestBody UpdateTaxRequest request) {
        return ApiResponse.of(taxes.update(hotelId, taxId, request));
    }

    @PostMapping("/taxes/{taxId}/rates")
    @RequiresPermission("TAX_MANAGE")
    ApiResponse<TaxResponse> changeRate(@PathVariable UUID hotelId, @PathVariable UUID taxId,
                                        @Valid @RequestBody NewRateRequest request) {
        return ApiResponse.of(taxes.changeRate(hotelId, taxId, request));
    }

    // --- Moyens de paiement ---------------------------------------------------------------------------------

    @GetMapping("/payment-methods")
    @RequiresPermission("HOTEL_VIEW")
    ApiResponse<List<PaymentMethodResponse>> paymentMethods(@PathVariable UUID hotelId) {
        return ApiResponse.of(referenceData.paymentMethods(hotelId));
    }

    @PostMapping("/payment-methods")
    @RequiresPermission("PAYMENT_METHOD_MANAGE")
    ResponseEntity<ApiResponse<PaymentMethodResponse>> createPaymentMethod(@PathVariable UUID hotelId,
                                                                           @Valid @RequestBody PaymentMethodRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(referenceData.createPaymentMethod(hotelId, request)));
    }

    @PutMapping("/payment-methods/{id}")
    @RequiresPermission("PAYMENT_METHOD_MANAGE")
    ApiResponse<PaymentMethodResponse> updatePaymentMethod(@PathVariable UUID hotelId, @PathVariable UUID id,
                                                           @Valid @RequestBody PaymentMethodRequest request) {
        return ApiResponse.of(referenceData.updatePaymentMethod(hotelId, id, request));
    }

    // --- Canaux --------------------------------------------------------------------------------------------

    @GetMapping("/channels")
    @RequiresPermission("HOTEL_VIEW")
    ApiResponse<List<ChannelResponse>> channels(@PathVariable UUID hotelId) {
        return ApiResponse.of(referenceData.channels(hotelId));
    }

    @PostMapping("/channels")
    @RequiresPermission("SETTINGS_UPDATE")
    ResponseEntity<ApiResponse<ChannelResponse>> createChannel(@PathVariable UUID hotelId,
                                                               @Valid @RequestBody ChannelRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(referenceData.createChannel(hotelId, request)));
    }

    @PutMapping("/channels/{id}")
    @RequiresPermission("SETTINGS_UPDATE")
    ApiResponse<ChannelResponse> updateChannel(@PathVariable UUID hotelId, @PathVariable UUID id,
                                               @Valid @RequestBody ChannelRequest request) {
        return ApiResponse.of(referenceData.updateChannel(hotelId, id, request));
    }

    // --- Motifs --------------------------------------------------------------------------------------------

    @GetMapping("/reasons")
    @RequiresPermission("HOTEL_VIEW")
    ApiResponse<List<ReasonResponse>> reasons(@PathVariable UUID hotelId,
                                              @RequestParam(required = false) ReasonCode.Domain domain) {
        return ApiResponse.of(referenceData.reasons(hotelId, domain));
    }

    @PostMapping("/reasons")
    @RequiresPermission("SETTINGS_UPDATE")
    ResponseEntity<ApiResponse<ReasonResponse>> createReason(@PathVariable UUID hotelId,
                                                             @Valid @RequestBody ReasonRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(referenceData.createReason(hotelId, request)));
    }

    @PutMapping("/reasons/{id}")
    @RequiresPermission("SETTINGS_UPDATE")
    ApiResponse<ReasonResponse> updateReason(@PathVariable UUID hotelId, @PathVariable UUID id,
                                             @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.of(referenceData.updateReason(hotelId, id, request));
    }
}
