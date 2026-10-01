package sn.niirma.shared.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Accessible à tout utilisateur authentifié, sans permission particulière (ex. son propre profil).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuthenticatedOnly {

    /** Autorise l'appel même si l'utilisateur doit encore remplacer son mot de passe temporaire. */
    boolean allowedDuringPasswordChange() default false;
}
