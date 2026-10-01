package sn.niirma.iam.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    List<Role> findByOrganizationIdOrderByNameAsc(UUID organizationId);

    Optional<Role> findByOrganizationIdAndCode(UUID organizationId, String code);

    boolean existsByOrganizationIdAndCode(UUID organizationId, String code);
}
