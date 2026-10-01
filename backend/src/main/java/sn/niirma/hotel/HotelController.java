package sn.niirma.hotel;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/{hotelId}")
    @RequiresPermission("HOTEL_VIEW")
    ApiResponse<HotelView> get(@PathVariable UUID hotelId) {
        return ApiResponse.of(hotelService.get(hotelId));
    }
}
