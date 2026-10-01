package sn.niirma.rooms.internal;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
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

import sn.niirma.rooms.internal.RoomBlockService.BlockRequest;
import sn.niirma.rooms.internal.RoomBlockService.BlockResponse;
import sn.niirma.rooms.internal.RoomCatalogService.AmenityRequest;
import sn.niirma.rooms.internal.RoomCatalogService.AmenityResponse;
import sn.niirma.rooms.internal.RoomCatalogService.Photo;
import sn.niirma.rooms.internal.RoomCatalogService.RoomTypeRequest;
import sn.niirma.rooms.internal.RoomCatalogService.RoomTypeResponse;
import sn.niirma.rooms.internal.RoomService.BatchRequest;
import sn.niirma.rooms.internal.RoomService.HousekeepingRequest;
import sn.niirma.rooms.internal.RoomService.RoomRequest;
import sn.niirma.rooms.internal.RoomService.RoomResponse;
import sn.niirma.shared.api.ApiResponse;
import sn.niirma.shared.security.RequiresPermission;

@RestController
@RequestMapping("/api/v1/hotels/{hotelId}")
class RoomController {

    private final RoomCatalogService catalog;
    private final RoomService rooms;
    private final RoomBlockService blocks;

    RoomController(RoomCatalogService catalog, RoomService rooms, RoomBlockService blocks) {
        this.catalog = catalog;
        this.rooms = rooms;
        this.blocks = blocks;
    }

    // --- Équipements ---------------------------------------------------------------------------------------

    @GetMapping("/amenities")
    @RequiresPermission("ROOM_VIEW")
    ApiResponse<List<AmenityResponse>> amenities(@PathVariable UUID hotelId) {
        return ApiResponse.of(catalog.amenities(hotelId));
    }

    @PostMapping("/amenities")
    @RequiresPermission("ROOM_UPDATE")
    ResponseEntity<ApiResponse<AmenityResponse>> createAmenity(@PathVariable UUID hotelId,
                                                               @Valid @RequestBody AmenityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(catalog.createAmenity(hotelId, request)));
    }

    @PutMapping("/amenities/{amenityId}")
    @RequiresPermission("ROOM_UPDATE")
    ApiResponse<AmenityResponse> updateAmenity(@PathVariable UUID hotelId, @PathVariable UUID amenityId,
                                               @Valid @RequestBody AmenityRequest request) {
        return ApiResponse.of(catalog.updateAmenity(hotelId, amenityId, request));
    }

    // --- Types de chambres ---------------------------------------------------------------------------------

    @GetMapping("/room-types")
    @RequiresPermission("ROOM_VIEW")
    ApiResponse<List<RoomTypeResponse>> roomTypes(@PathVariable UUID hotelId) {
        return ApiResponse.of(catalog.roomTypes(hotelId));
    }

    @PostMapping("/room-types")
    @RequiresPermission("ROOM_CREATE")
    ResponseEntity<ApiResponse<RoomTypeResponse>> createRoomType(@PathVariable UUID hotelId,
                                                                 @Valid @RequestBody RoomTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(catalog.createRoomType(hotelId, request)));
    }

    @PutMapping("/room-types/{roomTypeId}")
    @RequiresPermission("ROOM_UPDATE")
    ApiResponse<RoomTypeResponse> updateRoomType(@PathVariable UUID hotelId, @PathVariable UUID roomTypeId,
                                                 @Valid @RequestBody RoomTypeRequest request) {
        return ApiResponse.of(catalog.updateRoomType(hotelId, roomTypeId, request));
    }

    @PostMapping(path = "/room-types/{roomTypeId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission("ROOM_UPDATE")
    ResponseEntity<ApiResponse<Map<String, UUID>>> addPhoto(@PathVariable UUID hotelId, @PathVariable UUID roomTypeId,
                                                            @RequestPart("file") MultipartFile file) throws IOException {
        UUID photoId = catalog.addPhoto(hotelId, roomTypeId, file.getBytes(), file.getContentType());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(Map.of("id", photoId)));
    }

    @GetMapping("/room-types/{roomTypeId}/photos/{photoId}")
    @RequiresPermission("ROOM_VIEW")
    ResponseEntity<byte[]> photo(@PathVariable UUID hotelId, @PathVariable UUID roomTypeId, @PathVariable UUID photoId) {
        Photo photo = catalog.photo(hotelId, roomTypeId, photoId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.noCache().cachePrivate())
                .body(photo.content());
    }

    @DeleteMapping("/room-types/{roomTypeId}/photos/{photoId}")
    @RequiresPermission("ROOM_UPDATE")
    ResponseEntity<Void> deletePhoto(@PathVariable UUID hotelId, @PathVariable UUID roomTypeId, @PathVariable UUID photoId) {
        catalog.deletePhoto(hotelId, roomTypeId, photoId);
        return ResponseEntity.noContent().build();
    }

    // --- Chambres ------------------------------------------------------------------------------------------

    @GetMapping("/rooms")
    @RequiresPermission("ROOM_VIEW")
    ApiResponse<List<RoomResponse>> rooms(@PathVariable UUID hotelId) {
        return ApiResponse.of(rooms.list(hotelId));
    }

    @PostMapping("/rooms")
    @RequiresPermission("ROOM_CREATE")
    ResponseEntity<ApiResponse<RoomResponse>> createRoom(@PathVariable UUID hotelId, @Valid @RequestBody RoomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(rooms.create(hotelId, request)));
    }

    @PostMapping("/rooms/batch")
    @RequiresPermission("ROOM_CREATE")
    ResponseEntity<ApiResponse<List<RoomResponse>>> createRooms(@PathVariable UUID hotelId,
                                                                @Valid @RequestBody BatchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(rooms.createBatch(hotelId, request)));
    }

    @PutMapping("/rooms/{roomId}")
    @RequiresPermission("ROOM_UPDATE")
    ApiResponse<RoomResponse> updateRoom(@PathVariable UUID hotelId, @PathVariable UUID roomId,
                                         @Valid @RequestBody RoomRequest request) {
        return ApiResponse.of(rooms.update(hotelId, roomId, request));
    }

    @PutMapping("/rooms/{roomId}/housekeeping-status")
    @RequiresPermission("HK_STATUS_UPDATE")
    ApiResponse<RoomResponse> changeHousekeepingStatus(@PathVariable UUID hotelId, @PathVariable UUID roomId,
                                                       @Valid @RequestBody HousekeepingRequest request) {
        return ApiResponse.of(rooms.changeHousekeepingStatus(hotelId, roomId, request));
    }

    @GetMapping("/rooms/{roomId}/history")
    @RequiresPermission("ROOM_VIEW")
    ApiResponse<List<RoomStatusHistory.Entry>> history(@PathVariable UUID hotelId, @PathVariable UUID roomId) {
        return ApiResponse.of(rooms.history(hotelId, roomId));
    }

    // --- Blocages ------------------------------------------------------------------------------------------

    @GetMapping("/room-blocks")
    @RequiresPermission("ROOM_VIEW")
    ApiResponse<List<BlockResponse>> blocks(@PathVariable UUID hotelId,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.of(blocks.list(hotelId, from, to));
    }

    @PostMapping("/room-blocks")
    @RequiresPermission("ROOM_BLOCK_MANAGE")
    ResponseEntity<ApiResponse<BlockResponse>> createBlock(@PathVariable UUID hotelId, @Valid @RequestBody BlockRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(blocks.create(hotelId, request)));
    }

    @PostMapping("/room-blocks/{blockId}/release")
    @RequiresPermission("ROOM_BLOCK_MANAGE")
    ApiResponse<BlockResponse> releaseBlock(@PathVariable UUID hotelId, @PathVariable UUID blockId) {
        return ApiResponse.of(blocks.release(hotelId, blockId));
    }

    @DeleteMapping("/room-blocks/{blockId}")
    @RequiresPermission("ROOM_BLOCK_MANAGE")
    ResponseEntity<Void> deleteBlock(@PathVariable UUID hotelId, @PathVariable UUID blockId) {
        blocks.delete(hotelId, blockId);
        return ResponseEntity.noContent().build();
    }
}
