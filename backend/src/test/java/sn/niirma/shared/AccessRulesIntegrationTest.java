package sn.niirma.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import sn.niirma.shared.security.AuthenticatedOnly;
import sn.niirma.shared.security.PublicEndpoint;
import sn.niirma.shared.security.RequiresPermission;
import sn.niirma.support.IntegrationTest;

/**
 * US-IAM-03 : chaque endpoint du PMS déclare explicitement sa règle d'accès. Un endpoint ajouté sans
 * règle fait échouer ce test (et serait refusé en 500 à l'exécution).
 */
@IntegrationTest
class AccessRulesIntegrationTest {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    @Test
    void everyApplicationEndpointDeclaresExactlyOneAccessRule() {
        List<HandlerMethod> applicationHandlers = handlerMapping.getHandlerMethods().values().stream()
                .filter(method -> method.getBeanType().getPackageName().startsWith("sn.niirma"))
                .toList();

        assertThat(applicationHandlers).isNotEmpty();
        assertThat(applicationHandlers)
                .allSatisfy(method -> assertThat(Stream.of(
                                method.hasMethodAnnotation(PublicEndpoint.class),
                                method.hasMethodAnnotation(AuthenticatedOnly.class),
                                method.hasMethodAnnotation(RequiresPermission.class))
                        .filter(Boolean::booleanValue).count())
                        .as(method.getShortLogMessage())
                        .isEqualTo(1));
    }

    @Test
    void publicEndpointsAreLimitedToAuthentication() {
        List<String> publicHandlers = handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getValue().hasMethodAnnotation(PublicEndpoint.class))
                .flatMap(entry -> entry.getKey().getPatternValues().stream())
                .sorted()
                .toList();

        assertThat(publicHandlers).containsExactly(
                "/api/v1/auth/login",
                "/api/v1/auth/logout",
                "/api/v1/auth/password/forgot",
                "/api/v1/auth/password/reset",
                "/api/v1/auth/refresh");
    }
}
