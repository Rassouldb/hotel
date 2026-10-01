package sn.niirma.idempotency;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Rend une méthode de contrôleur idempotente (§32.4) : l'en-tête {@code Idempotency-Key} devient
 * obligatoire et un renvoi de la même requête avec la même clé rejoue la première réponse sans
 * réexécuter l'opération. La méthode doit renvoyer un {@code ResponseEntity}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    String HEADER = "Idempotency-Key";

    String REPLAYED_HEADER = "Idempotent-Replayed";
}
