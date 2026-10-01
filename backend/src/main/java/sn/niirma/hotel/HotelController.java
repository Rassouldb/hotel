package sn.niirma.hotel;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import sn.niirma.hotel.HotelService.CreateHotelRequest;
import sn.niirma.hotel.HotelService.Logo;
import sn.niirma.hotel.HotelService.UpdateHotelRequest;
import sn.niirma.shared.api.ApiResponse;
import sn.niirma.shared.api.PageMeta;
import sn.niirma.shared.security.AuthenticatedOnly;
import sn.niirma.shared.security.RequiresPermission;

@RestController
@RequestMapping("/api/v1/hotels")
class HotelController {

    private static final int MAX_PAGE_SIZE = 100;

    private final HotelService hotelService;

    HotelController(HotelService hotelService) {
        this.hotelService = hotelService;
    }

    @GetMapping
    @AuthenticatedOnly
    ApiResponse<List<HotelView>> list(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "25") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 1) - 1, Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by("name"));
        Page<HotelView> result = hotelService.list(pageable);
        return ApiResponse.of(result.getContent(), PageMeta.from(result));
    }

    /** Réservé au super administrateur (contrôlé par le service). */
    @PostMapping
    @AuthenticatedOnly
    ResponseEntity<ApiResponse<HotelView>> create(@Valid @RequestBody CreateHotelRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(hotelService.create(request)));
    }

    @GetMapping("/{hotelId}")
    @RequiresPermission("HOTEL_VIEW")
    ApiResponse<HotelView> get(@PathVariable UUID hotelId) {
        return ApiResponse.of(hotelService.get(hotelId));
    }

    @PutMapping("/{hotelId}")
    @RequiresPermission("HOTEL_UPDATE")
    ApiResponse<HotelView> update(@PathVariable UUID hotelId, @Valid @RequestBody UpdateHotelRequest request) {
        return ApiResponse.of(hotelService.update(hotelId, request));
    }

    @GetMapping("/{hotelId}/settings")
    @RequiresPermission("HOTEL_VIEW")
    ApiResponse<HotelSettings> settings(@PathVariable UUID hotelId) {
        return ApiResponse.of(hotelService.settings(hotelId));
    }

    @PutMapping("/{hotelId}/settings")
    @RequiresPermission("SETTINGS_UPDATE")
    ApiResponse<HotelSettings> updateSettings(@PathVariable UUID hotelId, @RequestBody HotelSettings settings) {
        return ApiResponse.of(hotelService.updateSettings(hotelId, settings));
    }

    @GetMapping("/{hotelId}/logo")
    @RequiresPermission("HOTEL_VIEW")
    ResponseEntity<byte[]> logo(@PathVariable UUID hotelId) {
        Logo logo = hotelService.logo(hotelId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(logo.contentType()))
                .cacheControl(CacheControl.noCache().cachePrivate())
                .body(logo.content());
    }

    @PutMapping(path = "/{hotelId}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission("HOTEL_UPDATE")
    ResponseEntity<Void> updateLogo(@PathVariable UUID hotelId, @RequestPart("file") MultipartFile file) throws IOException {
        hotelService.updateLogo(hotelId, file.getBytes(), file.getContentType());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{hotelId}/logo")
    @RequiresPermission("HOTEL_UPDATE")
    ResponseEntity<Void> deleteLogo(@PathVariable UUID hotelId) {
        hotelService.deleteLogo(hotelId);
        return ResponseEntity.noContent().build();
    }
}
