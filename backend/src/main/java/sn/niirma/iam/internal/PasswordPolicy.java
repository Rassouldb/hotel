package sn.niirma.iam.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import sn.niirma.shared.error.BusinessException;

/**
 * Règles de mot de passe (§7.2) : 10 à 128 caractères, absent de la liste des 10 000 mots de passe
 * les plus courants, ne contient pas le nom d'utilisateur.
 */
@Component
public class PasswordPolicy {

    static final int MIN_LENGTH = 10;
    static final int MAX_LENGTH = 128;

    private final Set<String> commonPasswords;

    PasswordPolicy() {
        this.commonPasswords = loadCommonPasswords();
    }

    public void validate(String password, String username) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw weak("MIN_LENGTH", "Le mot de passe doit contenir au moins " + MIN_LENGTH + " caractères.");
        }
        if (password.length() > MAX_LENGTH) {
            throw weak("MAX_LENGTH", "Le mot de passe ne peut pas dépasser " + MAX_LENGTH + " caractères.");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (commonPasswords.contains(lower)) {
            throw weak("COMMON", "Ce mot de passe est trop courant. Choisissez-en un autre.");
        }
        if (username != null && lower.contains(username.toLowerCase(Locale.ROOT))) {
            throw weak("CONTAINS_USERNAME", "Le mot de passe ne doit pas contenir le nom d'utilisateur.");
        }
    }

    private static BusinessException weak(String rule, String message) {
        return new BusinessException(HttpStatus.UNPROCESSABLE_CONTENT, "WEAK_PASSWORD", message,
                List.of(Map.of("field", "newPassword", "issue", rule)));
    }

    private static Set<String> loadCommonPasswords() {
        ClassPathResource resource = new ClassPathResource("security/common-passwords.txt");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .map(line -> line.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new UncheckedIOException("Liste des mots de passe courants introuvable", e);
        }
    }
}
