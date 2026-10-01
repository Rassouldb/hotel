package sn.niirma.iam.internal;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.niirma.audit.AuditEntry;
import sn.niirma.audit.AuditService;
import sn.niirma.hotel.HotelDirectory;
import sn.niirma.iam.Permission;
import sn.niirma.iam.UserSessionsRevocationRequested;
import sn.niirma.iam.internal.UserDtos.AssignmentRequest;
import sn.niirma.iam.internal.UserDtos.AssignmentResponse;
import sn.niirma.iam.internal.UserDtos.CreateUserRequest;
import sn.niirma.iam.internal.UserDtos.UpdateUserRequest;
import sn.niirma.iam.internal.UserDtos.UserResponse;
import sn.niirma.iam.internal.UserDtos.UserWithTemporaryPassword;
import sn.niirma.shared.error.BusinessException;
import sn.niirma.shared.security.AccessControl;
import sn.niirma.shared.security.CurrentUser;
import sn.niirma.shared.security.HotelScope;

/**
 * Administration des comptes (US-IAM-01). Un administrateur d'hôtel ne gère que les comptes dont
 * toutes les affectations sont dans ses hôtels, et ne peut pas attribuer une permission qu'il n'a pas.
 * Seul un super administrateur gère les super administrateurs.
 */
@Service
@Transactional
class UserAdministrationService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final HotelDirectory hotelDirectory;
    private final AccessControl accessControl;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    UserAdministrationService(UserRepository users, RoleRepository roles, HotelDirectory hotelDirectory,
                              AccessControl accessControl, PasswordEncoder passwordEncoder, AuditService audit,
                              ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.hotelDirectory = hotelDirectory;
        this.accessControl = accessControl;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> list(String search, User.Status status, UUID hotelId, Pageable pageable) {
        CurrentUser caller = CurrentUser.require();
        HotelScope scope = accessControl.hotelsWithPermission(Permission.USER_VIEW.name());
        Specification<User> specification = visibleTo(caller, scope)
                .and(matching(search))
                .and(withStatus(status))
                .and(assignedTo(hotelId));
        Page<User> page = users.findAll(specification, pageable);
        Map<UUID, Role> roleIndex = roleIndex(caller.organizationId());
        return page.map(user -> toResponse(user, roleIndex));
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID userId) {
        User user = loadVisible(userId);
        return toResponse(user, roleIndex(user.getOrganizationId()));
    }

    public UserWithTemporaryPassword create(CreateUserRequest request) {
        CurrentUser caller = CurrentUser.require();
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        if (users.existsByUsername(username)) {
            throw BusinessException.conflict("USERNAME_ALREADY_EXISTS", "Ce nom d'utilisateur est déjà utilisé.");
        }
        String email = normalizeEmail(request.email());
        if (email != null && users.findByEmailIgnoreCase(email).isPresent()) {
            throw BusinessException.conflict("EMAIL_ALREADY_EXISTS", "Cette adresse email est déjà utilisée.");
        }
        if (request.superAdmin() && !callerIsSuperAdmin()) {
            throw permissionDenied();
        }
        Set<HotelRoleAssignment> assignments = validateAssignments(caller, request.assignments(), request.superAdmin(),
                Permission.USER_CREATE);

        String temporaryPassword = PasswordHashing.generateTemporaryPassword();
        User user = new User(caller.organizationId(), username, request.firstName().trim(), request.lastName().trim(),
                passwordEncoder.encode(temporaryPassword));
        user.updateProfile(request.firstName().trim(), request.lastName().trim(), blankToNull(request.phone()),
                request.language() == null ? "fr" : request.language());
        user.setEmail(email);
        user.setSuperAdmin(request.superAdmin());
        user.replaceAssignments(assignments);
        users.saveAndFlush(user);

        audit.record(AuditEntry.of("USER_CREATE", "User", user.getId()).withChange(null, snapshot(user)));
        return new UserWithTemporaryPassword(toResponse(user, roleIndex(user.getOrganizationId())), temporaryPassword);
    }

    public UserResponse update(UUID userId, UpdateUserRequest request) {
        CurrentUser caller = CurrentUser.require();
        User user = loadManageable(userId, Permission.USER_UPDATE);
        if (!Objects.equals(user.getVersion(), request.version())) {
            throw BusinessException.conflict("VERSION_CONFLICT",
                    "Ce compte a été modifié entre-temps. Rechargez puis réessayez.");
        }
        String email = normalizeEmail(request.email());
        if (email != null && users.emailUsedByAnotherUser(email, user.getId())) {
            throw BusinessException.conflict("EMAIL_ALREADY_EXISTS", "Cette adresse email est déjà utilisée.");
        }
        if (request.superAdmin() != user.isSuperAdmin()) {
            if (!callerIsSuperAdmin()) {
                throw permissionDenied();
            }
            if (!request.superAdmin()) {
                ensureNotLastSuperAdmin(user);
            }
        }
        Set<HotelRoleAssignment> assignments = validateAssignments(caller, request.assignments(), request.superAdmin(),
                Permission.USER_UPDATE);

        Map<String, Object> before = snapshot(user);
        user.updateProfile(request.firstName().trim(), request.lastName().trim(), blankToNull(request.phone()),
                request.language());
        user.setEmail(email);
        user.setSuperAdmin(request.superAdmin());
        user.replaceAssignments(assignments);
        users.saveAndFlush(user);

        Map<String, Object> after = snapshot(user);
        audit.record(AuditEntry.of("USER_UPDATE", "User", user.getId())
                .withChange(changedOnly(before, after), changedOnly(after, before)));
        return toResponse(user, roleIndex(user.getOrganizationId()));
    }

    public UserResponse disable(UUID userId) {
        CurrentUser caller = CurrentUser.require();
        User user = loadManageable(userId, Permission.USER_DISABLE);
        if (user.getId().equals(caller.userId())) {
            throw BusinessException.conflict("CANNOT_DISABLE_SELF", "Vous ne pouvez pas désactiver votre propre compte.");
        }
        if (user.isActive()) {
            ensureNotLastSuperAdmin(user);
            user.disable();
            audit.record(AuditEntry.of("USER_DISABLE", "User", user.getId())
                    .withChange(Map.of("status", "ACTIVE"), Map.of("status", "DISABLED")));
            events.publishEvent(new UserSessionsRevocationRequested(user.getId(), null, "USER_DISABLED"));
        }
        return toResponse(user, roleIndex(user.getOrganizationId()));
    }

    public UserResponse enable(UUID userId) {
        User user = loadManageable(userId, Permission.USER_DISABLE);
        if (!user.isActive()) {
            user.enable();
            audit.record(AuditEntry.of("USER_ENABLE", "User", user.getId())
                    .withChange(Map.of("status", "DISABLED"), Map.of("status", "ACTIVE")));
        }
        return toResponse(user, roleIndex(user.getOrganizationId()));
    }

    public UserWithTemporaryPassword resetPassword(UUID userId) {
        User user = loadManageable(userId, Permission.USER_UPDATE);
        String temporaryPassword = PasswordHashing.generateTemporaryPassword();
        user.assignTemporaryPassword(passwordEncoder.encode(temporaryPassword), OffsetDateTime.now(clock));
        audit.record(AuditEntry.of("USER_PASSWORD_RESET", "User", user.getId()));
        events.publishEvent(new UserSessionsRevocationRequested(user.getId(), null, "PASSWORD_RESET_BY_ADMIN"));
        return new UserWithTemporaryPassword(toResponse(user, roleIndex(user.getOrganizationId())), temporaryPassword);
    }

    // --- Règles de portée --------------------------------------------------------------------------------

    private User loadVisible(UUID userId) {
        CurrentUser caller = CurrentUser.require();
        HotelScope scope = accessControl.hotelsWithPermission(Permission.USER_VIEW.name());
        return users.findById(userId)
                .filter(user -> user.getOrganizationId().equals(caller.organizationId()))
                .filter(user -> callerIsSuperAdmin() || (!user.isSuperAdmin()
                        && user.getAssignments().stream().anyMatch(a -> scope.includes(a.hotelId()))))
                .orElseThrow(() -> BusinessException.notFound("USER_NOT_FOUND", "Utilisateur introuvable."));
    }

    private User loadManageable(UUID userId, Permission permission) {
        CurrentUser caller = CurrentUser.require();
        User user = loadVisible(userId);
        if (callerIsSuperAdmin()) {
            return user;
        }
        HotelScope scope = accessControl.hotelsWithPermission(permission.name());
        boolean allHotelsInScope = !user.getAssignments().isEmpty()
                && user.getAssignments().stream().allMatch(a -> scope.includes(a.hotelId()));
        if (!allHotelsInScope) {
            throw permissionDenied();
        }
        return user;
    }

    private Set<HotelRoleAssignment> validateAssignments(CurrentUser caller, List<AssignmentRequest> requested,
                                                         boolean targetIsSuperAdmin, Permission permission) {
        List<AssignmentRequest> list = requested == null ? List.of() : requested;
        if (list.isEmpty() && !targetIsSuperAdmin) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_CONTENT, "ASSIGNMENT_REQUIRED",
                    "Affectez au moins un rôle dans un hôtel.",
                    List.of(Map.of("field", "assignments", "issue", "EMPTY")));
        }
        boolean callerIsSuperAdmin = callerIsSuperAdmin();
        HotelScope scope = accessControl.hotelsWithPermission(permission.name());
        Set<HotelRoleAssignment> result = new HashSet<>();
        for (AssignmentRequest assignment : list) {
            boolean hotelInOrganization = hotelDirectory.organizationOf(assignment.hotelId())
                    .filter(caller.organizationId()::equals)
                    .isPresent();
            if (!hotelInOrganization) {
                throw BusinessException.unprocessable("HOTEL_NOT_FOUND", "Hôtel introuvable : " + assignment.hotelId());
            }
            Role role = roles.findById(assignment.roleId())
                    .filter(r -> r.getOrganizationId().equals(caller.organizationId()))
                    .orElseThrow(() -> BusinessException.unprocessable("ROLE_NOT_FOUND",
                            "Rôle introuvable : " + assignment.roleId()));
            if (!callerIsSuperAdmin) {
                if (!scope.includes(assignment.hotelId())) {
                    throw permissionDenied();
                }
                boolean exceedsOwnPermissions = role.getPermissions().stream()
                        .anyMatch(p -> !accessControl.hasPermission(p, assignment.hotelId()));
                if (exceedsOwnPermissions) {
                    throw new BusinessException(HttpStatus.FORBIDDEN, "ROLE_EXCEEDS_OWN_PERMISSIONS",
                            "Vous ne pouvez pas attribuer le rôle « " + role.getName()
                                    + " » : il contient des permissions que vous n'avez pas.");
                }
            }
            result.add(new HotelRoleAssignment(assignment.hotelId(), role.getId()));
        }
        return result;
    }

    private void ensureNotLastSuperAdmin(User user) {
        if (user.isSuperAdmin() && user.isActive() && users.countActiveSuperAdmins(user.getOrganizationId()) <= 1) {
            throw BusinessException.conflict("LAST_SUPER_ADMIN",
                    "Impossible : c'est le dernier super administrateur actif de l'organisation.");
        }
    }

    /** Lu en base (et non dans le jeton) : une rétrogradation s'applique immédiatement. */
    private boolean callerIsSuperAdmin() {
        return accessControl.accessibleHotels().allHotels();
    }

    private static BusinessException permissionDenied() {
        return new BusinessException(HttpStatus.FORBIDDEN, "PERMISSION_DENIED",
                "Vous n'avez pas les droits nécessaires pour cette opération.");
    }

    // --- Recherche ---------------------------------------------------------------------------------------

    private Specification<User> visibleTo(CurrentUser caller, HotelScope scope) {
        return (root, query, cb) -> {
            Predicate sameOrganization = cb.equal(root.get("organizationId"), caller.organizationId());
            if (scope.allHotels()) {
                return sameOrganization;
            }
            if (scope.hotelIds().isEmpty()) {
                return cb.disjunction();
            }
            query.distinct(true);
            Join<User, HotelRoleAssignment> assignment = root.join("assignments", JoinType.INNER);
            return cb.and(sameOrganization, cb.isFalse(root.get("superAdmin")),
                    assignment.get("hotelId").in(scope.hotelIds()));
        };
    }

    private static Specification<User> matching(String search) {
        if (search == null || search.isBlank()) {
            return Specification.unrestricted();
        }
        String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("username")), pattern),
                cb.like(cb.lower(root.get("firstName")), pattern),
                cb.like(cb.lower(root.get("lastName")), pattern),
                cb.like(cb.lower(cb.coalesce(root.get("email"), "")), pattern));
    }

    private static Specification<User> withStatus(User.Status status) {
        return status == null ? Specification.unrestricted() : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    private static Specification<User> assignedTo(UUID hotelId) {
        if (hotelId == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> {
            query.distinct(true);
            Join<User, HotelRoleAssignment> assignment = root.join("assignments", JoinType.INNER);
            return cb.equal(assignment.get("hotelId"), hotelId);
        };
    }

    // --- Conversion --------------------------------------------------------------------------------------

    private Map<UUID, Role> roleIndex(UUID organizationId) {
        return roles.findByOrganizationIdOrderByNameAsc(organizationId).stream()
                .collect(Collectors.toMap(Role::getId, Function.identity()));
    }

    private UserResponse toResponse(User user, Map<UUID, Role> roleIndex) {
        List<AssignmentResponse> assignments = user.getAssignments().stream()
                .map(a -> {
                    Role role = roleIndex.get(a.roleId());
                    return new AssignmentResponse(a.hotelId(), a.roleId(), role == null ? null : role.getCode(),
                            role == null ? null : role.getName());
                })
                .sorted(Comparator.comparing(AssignmentResponse::hotelId).thenComparing(r -> String.valueOf(r.roleName())))
                .toList();
        return new UserResponse(user.getId(), user.getUsername(), user.getFirstName(), user.getLastName(),
                user.getEmail(), user.getPhone(), user.getLanguage(), user.isSuperAdmin(), user.getStatus().name(),
                user.isMustChangePassword(), user.isLocked(OffsetDateTime.now(clock)), user.getLastLoginAt(),
                assignments, user.getVersion());
    }

    private static Map<String, Object> snapshot(User user) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("username", user.getUsername());
        values.put("firstName", user.getFirstName());
        values.put("lastName", user.getLastName());
        values.put("email", user.getEmail());
        values.put("phone", user.getPhone());
        values.put("language", user.getLanguage());
        values.put("superAdmin", user.isSuperAdmin());
        values.put("assignments", user.getAssignments().stream()
                .map(a -> Map.of("hotelId", a.hotelId().toString(), "roleId", a.roleId().toString()))
                .sorted(Comparator.comparing(m -> m.get("hotelId") + m.get("roleId")))
                .toList());
        return values;
    }

    private static Map<String, Object> changedOnly(Map<String, Object> values, Map<String, Object> other) {
        Map<String, Object> changed = new HashMap<>();
        values.forEach((key, value) -> {
            if (!Objects.equals(value, other.get(key))) {
                changed.put(key, value);
            }
        });
        return changed;
    }

    private static String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
