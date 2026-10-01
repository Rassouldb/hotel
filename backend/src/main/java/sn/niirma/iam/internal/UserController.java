package sn.niirma.iam.internal;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

import sn.niirma.iam.internal.UserDtos.CreateUserRequest;
import sn.niirma.iam.internal.UserDtos.UpdateUserRequest;
import sn.niirma.iam.internal.UserDtos.UserResponse;
import sn.niirma.iam.internal.UserDtos.UserWithTemporaryPassword;
import sn.niirma.shared.api.ApiResponse;
import sn.niirma.shared.api.PageMeta;
import sn.niirma.shared.security.RequiresPermission;

@RestController
@RequestMapping("/api/v1/users")
class UserController {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserAdministrationService service;

    UserController(UserAdministrationService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermission("USER_VIEW")
    ApiResponse<List<UserResponse>> list(@RequestParam(required = false) String search,
                                         @RequestParam(required = false) User.Status status,
                                         @RequestParam(required = false) UUID hotelId,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "25") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 1) - 1, Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by("lastName", "firstName"));
        Page<UserResponse> result = service.list(search, status, hotelId, pageable);
        return ApiResponse.of(result.getContent(), PageMeta.from(result));
    }

    @GetMapping("/{userId}")
    @RequiresPermission("USER_VIEW")
    ApiResponse<UserResponse> get(@PathVariable UUID userId) {
        return ApiResponse.of(service.get(userId));
    }

    @PostMapping
    @RequiresPermission("USER_CREATE")
    ResponseEntity<ApiResponse<UserWithTemporaryPassword>> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(service.create(request)));
    }

    @PutMapping("/{userId}")
    @RequiresPermission("USER_UPDATE")
    ApiResponse<UserResponse> update(@PathVariable UUID userId, @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.of(service.update(userId, request));
    }

    @PostMapping("/{userId}/disable")
    @RequiresPermission("USER_DISABLE")
    ApiResponse<UserResponse> disable(@PathVariable UUID userId) {
        return ApiResponse.of(service.disable(userId));
    }

    @PostMapping("/{userId}/enable")
    @RequiresPermission("USER_DISABLE")
    ApiResponse<UserResponse> enable(@PathVariable UUID userId) {
        return ApiResponse.of(service.enable(userId));
    }

    @PostMapping("/{userId}/reset-password")
    @RequiresPermission("USER_UPDATE")
    ApiResponse<UserWithTemporaryPassword> resetPassword(@PathVariable UUID userId) {
        return ApiResponse.of(service.resetPassword(userId));
    }
}
