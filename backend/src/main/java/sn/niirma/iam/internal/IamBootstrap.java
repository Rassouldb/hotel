package sn.niirma.iam.internal;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import sn.niirma.hotel.HotelDirectory;
import sn.niirma.iam.Permission;

/**
 * Initialisation au démarrage :
 * <ol>
 *   <li>synchronise le catalogue des permissions avec l'énumération {@link Permission} ;</li>
 *   <li>crée les rôles par défaut manquants dans chaque organisation ;</li>
 *   <li>crée le premier super administrateur s'il n'en existe aucun ;</li>
 *   <li>en développement, crée des comptes de démonstration (un par rôle principal).</li>
 * </ol>
 */
@Component
@EnableConfigurationProperties(IamBootstrap.Properties.class)
class IamBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IamBootstrap.class);

    /**
     * @param adminUsername          super administrateur créé si aucun n'existe (vide : rien n'est créé)
     * @param adminMustChangePassword impose le remplacement du mot de passe initial (vrai en production)
     * @param demoUsersPassword      si renseigné, crée les comptes de démonstration avec ce mot de passe
     */
    @ConfigurationProperties("pms.bootstrap")
    record Properties(String organizationName, String organizationCountry, String adminUsername, String adminPassword,
                      Boolean adminMustChangePassword, String demoUsersPassword) {
    }

    private record DemoUser(String username, String firstName, String lastName, String roleCode, boolean allHotels) {
    }

    private static final List<DemoUser> DEMO_USERS = List.of(
            new DemoUser("direction", "Awa", "Ndiaye", "DIRECTOR", false),
            new DemoUser("reception", "Moussa", "Diallo", "RECEPTIONIST", false),
            new DemoUser("caisse", "Fatou", "Sarr", "CASHIER", false),
            new DemoUser("comptable", "Ibrahima", "Fall", "ACCOUNTANT", true),
            new DemoUser("menage", "Aminata", "Ba", "HOUSEKEEPING_AGENT", false));

    private final Properties properties;
    private final JdbcClient jdbc;
    private final RoleRepository roles;
    private final UserRepository users;
    private final HotelDirectory hotelDirectory;
    private final PasswordEncoder passwordEncoder;

    IamBootstrap(Properties properties, JdbcClient jdbc, RoleRepository roles, UserRepository users,
                 HotelDirectory hotelDirectory, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.jdbc = jdbc;
        this.roles = roles;
        this.users = users;
        this.hotelDirectory = hotelDirectory;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        synchronizePermissions();
        hotelDirectory.organizationIds().forEach(this::provisionSystemRoles);
        createFirstSuperAdmin();
        if (hasText(properties.demoUsersPassword())) {
            createDemoUsers();
        }
    }

    private void synchronizePermissions() {
        for (Permission permission : Permission.values()) {
            jdbc.sql("""
                            INSERT INTO permission (code, module, description) VALUES (:code, :module, :description)
                            ON CONFLICT (code) DO UPDATE SET module = EXCLUDED.module, description = EXCLUDED.description
                            """)
                    .param("code", permission.name())
                    .param("module", permission.module().name())
                    .param("description", permission.description())
                    .update();
        }
    }

    /** Crée les rôles par défaut absents ; ne modifie jamais un rôle existant. */
    void provisionSystemRoles(UUID organizationId) {
        for (DefaultRoles.Definition definition : DefaultRoles.ALL) {
            if (!roles.existsByOrganizationIdAndCode(organizationId, definition.code())) {
                Set<String> permissions = definition.permissions().stream().map(Enum::name).collect(Collectors.toSet());
                roles.save(new Role(organizationId, definition.code(), definition.name(), definition.description(), true,
                        permissions));
                log.info("Rôle par défaut {} créé pour l'organisation {}", definition.code(), organizationId);
            }
        }
    }

    private void createFirstSuperAdmin() {
        if (users.existsBySuperAdminTrue()) {
            return;
        }
        if (!hasText(properties.adminUsername()) || !hasText(properties.adminPassword())) {
            log.warn("Aucun super administrateur : définissez PMS_BOOTSTRAP_ADMIN_USERNAME et PMS_BOOTSTRAP_ADMIN_PASSWORD");
            return;
        }
        UUID organizationId = hotelDirectory.findFirstOrganizationId().orElseGet(() -> {
            UUID created = hotelDirectory.createOrganization(
                    hasText(properties.organizationName()) ? properties.organizationName() : "Organisation",
                    hasText(properties.organizationCountry()) ? properties.organizationCountry() : "SN");
            provisionSystemRoles(created);
            return created;
        });
        User admin = new User(organizationId, properties.adminUsername().trim().toLowerCase(Locale.ROOT),
                "Super", "Administrateur", passwordEncoder.encode(properties.adminPassword()));
        admin.setSuperAdmin(true);
        if (Boolean.FALSE.equals(properties.adminMustChangePassword())) {
            admin.changePassword(admin.getPasswordHash(), null);
        }
        users.save(admin);
        log.info("Super administrateur initial « {} » créé", admin.getUsername());
    }

    private void createDemoUsers() {
        UUID organizationId = hotelDirectory.findFirstOrganizationId().orElse(null);
        if (organizationId == null) {
            return;
        }
        List<UUID> hotels = hotelDirectory.hotelIdsOf(organizationId);
        if (hotels.isEmpty()) {
            return;
        }
        String hash = passwordEncoder.encode(properties.demoUsersPassword());
        for (DemoUser demo : DEMO_USERS) {
            if (users.existsByUsername(demo.username())) {
                continue;
            }
            Role role = roles.findByOrganizationIdAndCode(organizationId, demo.roleCode()).orElseThrow();
            User user = new User(organizationId, demo.username(), demo.firstName(), demo.lastName(), hash);
            user.changePassword(hash, null);
            List<UUID> assignedHotels = demo.allHotels() ? hotels : List.of(hotels.getFirst());
            user.replaceAssignments(assignedHotels.stream()
                    .map(hotelId -> new HotelRoleAssignment(hotelId, role.getId()))
                    .collect(Collectors.toSet()));
            users.save(user);
        }
        log.info("Comptes de démonstration disponibles : {}",
                DEMO_USERS.stream().map(DemoUser::username).collect(Collectors.joining(", ")));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
