package sn.niirma.iam.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByUsername(String username);

    @Query("select u from User u where lower(u.email) = lower(?1)")
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByUsername(String username);

    @Query("select count(u) > 0 from User u where lower(u.email) = lower(?1) and u.id <> ?2")
    boolean emailUsedByAnotherUser(String email, UUID userId);

    @Query("select count(u) from User u where u.organizationId = ?1 and u.superAdmin = true and u.status = 'ACTIVE'")
    long countActiveSuperAdmins(UUID organizationId);

    boolean existsBySuperAdminTrue();

    @Query("select count(u) from User u join u.assignments a where a.roleId = ?1")
    long countAssignmentsOfRole(UUID roleId);
}
