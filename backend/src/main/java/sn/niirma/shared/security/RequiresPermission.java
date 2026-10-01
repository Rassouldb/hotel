package sn.niirma.shared.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Exige une permission (§7.4). Si l'URL contient {@code {hotelId}}, la permission est vérifiée pour cet
 * hôtel ; sinon il suffit de la détenir dans au moins un hôtel, et le service restreint les données
 * aux hôtels concernés.
 *
 * <p>Chaque méthode de contrôleur porte exactement une des annotations {@link RequiresPermission},
 * {@link AuthenticatedOnly} ou {@link PublicEndpoint} (vérifié par un test).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresPermission {

    String value();
}
