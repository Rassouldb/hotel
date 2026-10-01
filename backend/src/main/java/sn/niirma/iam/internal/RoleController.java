package sn.niirma.iam.internal;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import sn.niirma.iam.internal.RoleService.PermissionResponse;
import sn.niirma.iam.internal.RoleService.RoleRequest;
import sn.niirma.iam.internal.RoleService.RoleResponse;
import sn.niirma.shared.api.ApiResponse;
import sn.niirma.shared.security.RequiresPermission;

@RestController
@RequestMapping("/api/v1")
class RoleController {

    private final RoleService service;

    RoleController(RoleService service) {
        this.service = service;
    }

    /** Consultable par ceux qui gèrent les utilisateurs, pour choisir les rôles à affecter. */
    @GetMapping("/roles")
    @RequiresPermission("USER_VIEW")
    ApiResponse<List<RoleResponse>> list() {
        return ApiResponse.of(service.list());
    }

    @GetMapping("/permissions")
    @RequiresPermission("USER_VIEW")
    ApiResponse<List<PermissionResponse>> permissions() {
        return ApiResponse.of(service.permissions());
    }

    @PostMapping("/roles")
    @RequiresPermission("ROLE_MANAGE")
    ResponseEntity<ApiResponse<RoleResponse>> create(@Valid @RequestBody RoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(service.create(request)));
    }

    @PutMapping("/roles/{roleId}")
    @RequiresPermission("ROLE_MANAGE")
    ApiResponse<RoleResponse> update(@PathVariable UUID roleId, @Valid @RequestBody RoleRequest request) {
        return ApiResponse.of(service.update(roleId, request));
    }

    @DeleteMapping("/roles/{roleId}")
    @RequiresPermission("ROLE_MANAGE")
    ResponseEntity<Void> delete(@PathVariable UUID roleId) {
        service.delete(roleId);
        return ResponseEntity.noContent().build();
    }
}
