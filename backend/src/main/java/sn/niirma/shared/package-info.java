/**
 * Noyau partagé : enveloppe des réponses API, gestion des erreurs, traçabilité des requêtes,
 * base des entités persistées. Utilisable par tous les modules.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package sn.niirma.shared;

import org.springframework.modulith.ApplicationModule;
