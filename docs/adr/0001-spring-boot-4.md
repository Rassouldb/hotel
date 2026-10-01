# 0001 — Spring Boot 4.1 au lieu de Spring Boot 3

**Statut** : acceptée — 2026-10-01

## Contexte

Le cahier des charges (H1, ADR-02) prévoyait Java 21 et Spring Boot 3. Au démarrage de l'itération 0, Spring Boot 3 n'est plus proposé par Spring Initializr et son support open source est terminé. La version stable courante est Spring Boot 4.1.

## Décision

Le backend utilise **Spring Boot 4.1** (Spring Framework 7, Hibernate 7, Jackson 3, Spring Modulith 2.1), toujours compilé en **Java 21**.

## Conséquences

- Correctifs de sécurité assurés pendant toute la durée du projet.
- Jackson 3 : packages `tools.jackson.*` (les annotations restent `com.fasterxml.jackson.annotation`).
- Testcontainers 2 : `org.testcontainers.postgresql.PostgreSQLContainer`.
- Les logs JSON utilisent la journalisation structurée native de Spring Boot (format logstash) : aucune bibliothèque supplémentaire.
- La pile technique décrite au §40 reste valable par ailleurs.
