package sn.niirma.iam.internal;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Contrats de l'API d'administration des utilisateurs.
 */
public final class UserDtos {

    private UserDtos() {
    }

    public record AssignmentRequest(@NotNull UUID hotelId, @NotNull UUID roleId) {
    }

    public record CreateUserRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{3,50}",
                    message = "3 à 50 caractères : lettres, chiffres, point, tiret, tiret bas") String username,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @Email @Size(max = 200) String email,
            @Size(max = 30) String phone,
            @Pattern(regexp = "fr|en") String language,
            boolean superAdmin,
            @Valid List<AssignmentRequest> assignments) {
    }

    public record UpdateUserRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @Email @Size(max = 200) String email,
            @Size(max = 30) String phone,
            @NotBlank @Pattern(regexp = "fr|en") String language,
            boolean superAdmin,
            @Valid List<AssignmentRequest> assignments,
            @NotNull Long version) {
    }

    public record AssignmentResponse(UUID hotelId, UUID roleId, String roleCode, String roleName) {
    }

    public record UserResponse(
            UUID id,
            String username,
            String firstName,
            String lastName,
            String email,
            String phone,
            String language,
            boolean superAdmin,
            String status,
            boolean mustChangePassword,
            boolean locked,
            OffsetDateTime lastLoginAt,
            List<AssignmentResponse> assignments,
            Long version) {
    }

    /** Le mot de passe temporaire n'est renvoyé qu'une fois, à la création ou à la réinitialisation. */
    public record UserWithTemporaryPassword(UserResponse user, String temporaryPassword) {
    }
}
