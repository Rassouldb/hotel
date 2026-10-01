package sn.niirma.iam.internal;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import sn.niirma.audit.AuditEntry;
import sn.niirma.audit.AuditService;
import sn.niirma.shared.api.ApiResponse;
import sn.niirma.shared.error.BusinessException;
import sn.niirma.shared.security.AuthenticatedOnly;
import sn.niirma.shared.security.CurrentUser;

/**
 * Profil de l'utilisateur connecté (US-AUTH-07) : identité, et pour chaque hôtel affecté, ses rôles
 * et permissions effectives. Le frontend s'en sert pour adapter le menu et les actions.
 */
@RestController
@RequestMapping("/api/v1/me")
class ProfileController {

    record RoleSummary(UUID id, String code, String name) {
    }

    record HotelAccess(UUID hotelId, List<RoleSummary> roles, List<String> permissions) {
    }

    record ProfileResponse(UUID id, String username, String firstName, String lastName, String email, String phone,
                           String language, boolean superAdmin, boolean mustChangePassword, List<HotelAccess> hotels) {
    }

    record ProfileUpdateRequest(@NotBlank @Size(max = 100) String firstName, @NotBlank @Size(max = 100) String lastName,
                                @Size(max = 30) String phone, @NotBlank @Pattern(regexp = "fr|en") String language) {
    }

    private final UserRepository users;
    private final RoleRepository roles;
    private final AuditService audit;

    ProfileController(UserRepository users, RoleRepository roles, AuditService audit) {
        this.users = users;
        this.roles = roles;
        this.audit = audit;
    }

    @GetMapping
    @AuthenticatedOnly(allowedDuringPasswordChange = true)
    @Transactional(readOnly = true)
    public ApiResponse<ProfileResponse> get() {
        return ApiResponse.of(toResponse(currentUser()));
    }

    @PutMapping
    @AuthenticatedOnly
    @Transactional
    public ApiResponse<ProfileResponse> update(@Valid @RequestBody ProfileUpdateRequest request) {
        User user = currentUser();
        Map<String, Object> before = Map.of("firstName", user.getFirstName(), "lastName", user.getLastName(),
                "language", user.getLanguage());
        user.updateProfile(request.firstName().trim(), request.lastName().trim(),
                request.phone() == null || request.phone().isBlank() ? null : request.phone().trim(), request.language());
        audit.record(AuditEntry.of("PROFILE_UPDATE", "User", user.getId()).withChange(before,
                Map.of("firstName", user.getFirstName(), "lastName", user.getLastName(), "language", user.getLanguage())));
        return ApiResponse.of(toResponse(user));
    }

    private User currentUser() {
        return users.findById(CurrentUser.require().userId())
                .orElseThrow(() -> BusinessException.notFound("USER_NOT_FOUND", "Utilisateur introuvable."));
    }

    private ProfileResponse toResponse(User user) {
        Map<UUID, Role> roleIndex = roles.findByOrganizationIdOrderByNameAsc(user.getOrganizationId()).stream()
                .collect(Collectors.toMap(Role::getId, Function.identity()));
        Map<UUID, List<Role>> rolesByHotel = user.getAssignments().stream()
                .filter(a -> roleIndex.containsKey(a.roleId()))
                .collect(Collectors.groupingBy(HotelRoleAssignment::hotelId,
                        Collectors.mapping(a -> roleIndex.get(a.roleId()), Collectors.toList())));
        List<HotelAccess> hotels = rolesByHotel.entrySet().stream()
                .map(entry -> {
                    Set<String> permissions = new TreeSet<>();
                    entry.getValue().forEach(role -> permissions.addAll(role.getPermissions()));
                    List<RoleSummary> summaries = entry.getValue().stream()
                            .sorted(Comparator.comparing(Role::getName))
                            .map(role -> new RoleSummary(role.getId(), role.getCode(), role.getName()))
                            .toList();
                    return new HotelAccess(entry.getKey(), summaries, List.copyOf(permissions));
                })
                .sorted(Comparator.comparing(HotelAccess::hotelId))
                .toList();
        return new ProfileResponse(user.getId(), user.getUsername(), user.getFirstName(), user.getLastName(),
                user.getEmail(), user.getPhone(), user.getLanguage(), user.isSuperAdmin(), user.isMustChangePassword(),
                hotels);
    }
}
