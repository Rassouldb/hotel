package sn.niirma.shared.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Accessible sans authentification (connexion, mot de passe oublié). La liste des chemins publics
 * doit aussi être déclarée dans la configuration Spring Security.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PublicEndpoint {
}
