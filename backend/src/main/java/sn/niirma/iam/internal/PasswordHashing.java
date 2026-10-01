package sn.niirma.iam.internal;

import java.security.SecureRandom;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Hachage Argon2id des mots de passe et génération des mots de passe temporaires.
 */
@Configuration(proxyBeanMethods = false)
public class PasswordHashing {

    /** Sans 0/O, 1/l/I : le mot de passe temporaire est dicté ou recopié à la main. */
    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final String DIGITS = "23456789";
    private static final int TEMPORARY_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    /** Mot de passe temporaire aléatoire, à remplacer à la première connexion. */
    public static String generateTemporaryPassword() {
        StringBuilder password = new StringBuilder(TEMPORARY_LENGTH);
        for (int i = 0; i < TEMPORARY_LENGTH - 2; i++) {
            password.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        password.append(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        password.append(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        return password.toString();
    }
}
