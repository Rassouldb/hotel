package sn.niirma.iam;

/**
 * Catalogue des permissions (§7.4). Synchronisé au démarrage dans la table {@code permission} ;
 * le code de l'énumération est le code stocké et utilisé dans {@code @RequiresPermission}.
 */
public enum Permission {

    HOTEL_VIEW(Module.HOTEL, "Consulter l'hôtel"),
    HOTEL_UPDATE(Module.HOTEL, "Modifier l'hôtel"),
    SETTINGS_UPDATE(Module.HOTEL, "Modifier le paramétrage"),
    TAX_MANAGE(Module.HOTEL, "Gérer les taxes"),
    PAYMENT_METHOD_MANAGE(Module.HOTEL, "Gérer les moyens de paiement"),

    USER_VIEW(Module.USERS, "Consulter les utilisateurs et les rôles"),
    USER_CREATE(Module.USERS, "Créer un utilisateur"),
    USER_UPDATE(Module.USERS, "Modifier un utilisateur, réinitialiser son mot de passe"),
    USER_DISABLE(Module.USERS, "Désactiver ou réactiver un utilisateur"),
    ROLE_MANAGE(Module.USERS, "Gérer les rôles et leurs permissions"),

    ROOM_VIEW(Module.ROOMS, "Consulter les chambres"),
    ROOM_CREATE(Module.ROOMS, "Créer une chambre"),
    ROOM_UPDATE(Module.ROOMS, "Modifier une chambre"),
    ROOM_BLOCK_MANAGE(Module.ROOMS, "Bloquer ou débloquer une chambre"),

    CUSTOMER_VIEW(Module.CUSTOMERS, "Consulter les clients"),
    CUSTOMER_CREATE(Module.CUSTOMERS, "Créer un client"),
    CUSTOMER_UPDATE(Module.CUSTOMERS, "Modifier un client"),
    CUSTOMER_MERGE(Module.CUSTOMERS, "Fusionner deux fiches client"),
    CUSTOMER_ID_DOCUMENT_VIEW(Module.CUSTOMERS, "Consulter les pièces d'identité"),

    COMPANY_VIEW(Module.COMPANIES, "Consulter les entreprises"),
    COMPANY_MANAGE(Module.COMPANIES, "Gérer les entreprises"),
    COMPANY_CREDIT_OVERRIDE(Module.COMPANIES, "Dépasser le plafond de crédit"),

    RATE_VIEW(Module.RATES, "Consulter les tarifs"),
    RATE_MANAGE(Module.RATES, "Gérer les tarifs"),
    RATE_OVERRIDE(Module.RATES, "Saisir un prix manuel"),
    PROMOTION_MANAGE(Module.RATES, "Gérer les promotions"),

    RESERVATION_VIEW(Module.RESERVATIONS, "Consulter les réservations et le planning"),
    RESERVATION_CREATE(Module.RESERVATIONS, "Créer une réservation"),
    RESERVATION_UPDATE(Module.RESERVATIONS, "Modifier une réservation"),
    RESERVATION_CANCEL(Module.RESERVATIONS, "Annuler une réservation"),
    RESERVATION_NO_SHOW(Module.RESERVATIONS, "Déclarer un no-show"),
    RESERVATION_REINSTATE(Module.RESERVATIONS, "Réactiver une réservation"),

    CHECKIN_CREATE(Module.STAYS, "Effectuer un check-in"),
    CHECKIN_OVERRIDE_HOUSEKEEPING(Module.STAYS, "Check-in sur une chambre non prête"),
    CHECKOUT_CREATE(Module.STAYS, "Effectuer un check-out"),
    CHECKOUT_WITH_BALANCE(Module.STAYS, "Check-out avec solde non nul"),
    STAY_ROOM_CHANGE(Module.STAYS, "Changer de chambre en cours de séjour"),
    STAY_EXTEND(Module.STAYS, "Prolonger ou écourter un séjour"),

    FOLIO_VIEW(Module.FOLIOS, "Consulter les folios"),
    FOLIO_CHARGE_POST(Module.FOLIOS, "Ajouter une charge"),
    FOLIO_CHARGE_CORRECT(Module.FOLIOS, "Corriger une charge"),
    FOLIO_TRANSFER(Module.FOLIOS, "Transférer une charge entre folios"),
    DISCOUNT_APPLY(Module.FOLIOS, "Appliquer une remise"),
    DISCOUNT_ABOVE_LIMIT(Module.FOLIOS, "Appliquer une remise au-delà du plafond"),

    INVOICE_VIEW(Module.INVOICING, "Consulter les factures"),
    INVOICE_ISSUE(Module.INVOICING, "Émettre une facture"),
    INVOICE_PROFORMA(Module.INVOICING, "Émettre une facture pro forma"),
    CREDIT_NOTE_ISSUE(Module.INVOICING, "Émettre un avoir"),
    INVOICE_SEND(Module.INVOICING, "Envoyer une facture par email"),

    PAYMENT_VIEW(Module.PAYMENTS, "Consulter les paiements"),
    PAYMENT_CREATE(Module.PAYMENTS, "Enregistrer un paiement"),
    PAYMENT_CANCEL(Module.PAYMENTS, "Annuler un paiement"),
    PAYMENT_REFUND(Module.PAYMENTS, "Rembourser un paiement"),

    CASH_SESSION_OPEN(Module.CASH, "Ouvrir une session de caisse"),
    CASH_SESSION_CLOSE(Module.CASH, "Clôturer une session de caisse"),
    CASH_MOVEMENT_CREATE(Module.CASH, "Enregistrer un mouvement de caisse"),
    CASH_ADJUSTMENT(Module.CASH, "Enregistrer un ajustement de caisse"),
    CASH_SUPERVISE(Module.CASH, "Superviser toutes les caisses"),

    NIGHT_AUDIT_RUN(Module.NIGHT_AUDIT, "Lancer la clôture journalière"),
    NIGHT_AUDIT_VIEW(Module.NIGHT_AUDIT, "Consulter les clôtures journalières"),

    HK_VIEW(Module.HOUSEKEEPING, "Consulter le housekeeping"),
    HK_STATUS_UPDATE(Module.HOUSEKEEPING, "Mettre à jour le statut ménage"),
    HK_TASK_ASSIGN(Module.HOUSEKEEPING, "Affecter les tâches de ménage"),
    HK_INSPECT(Module.HOUSEKEEPING, "Inspecter les chambres"),

    MAINTENANCE_VIEW(Module.MAINTENANCE, "Consulter la maintenance"),
    MAINTENANCE_TICKET_CREATE(Module.MAINTENANCE, "Signaler un problème"),
    MAINTENANCE_TICKET_MANAGE(Module.MAINTENANCE, "Traiter les tickets"),

    POS_VIEW(Module.RESTAURANT, "Consulter le restaurant / bar"),
    POS_ORDER_CREATE(Module.RESTAURANT, "Prendre une commande"),
    POS_ORDER_VOID(Module.RESTAURANT, "Annuler une ligne envoyée"),
    POS_TRANSFER_TO_ROOM(Module.RESTAURANT, "Transférer une addition en chambre"),
    POS_PRODUCT_MANAGE(Module.RESTAURANT, "Gérer les produits"),

    STOCK_VIEW(Module.STOCK, "Consulter les stocks"),
    STOCK_MOVEMENT_CREATE(Module.STOCK, "Enregistrer un mouvement de stock"),
    STOCK_INVENTORY(Module.STOCK, "Réaliser un inventaire"),
    STOCK_ADJUST(Module.STOCK, "Ajuster un stock"),

    SUPPLIER_MANAGE(Module.SUPPLIERS, "Gérer les fournisseurs"),
    PURCHASE_MANAGE(Module.SUPPLIERS, "Gérer les achats"),
    SUPPLIER_PAYMENT_CREATE(Module.SUPPLIERS, "Payer un fournisseur"),

    EXPENSE_VIEW(Module.EXPENSES, "Consulter les dépenses"),
    EXPENSE_CREATE(Module.EXPENSES, "Saisir une dépense"),
    EXPENSE_VALIDATE(Module.EXPENSES, "Valider une dépense"),

    DASHBOARD_VIEW(Module.REPORTING, "Consulter le tableau de bord"),
    REPORT_OPERATIONS_VIEW(Module.REPORTING, "Consulter les rapports opérationnels"),
    REPORT_FINANCE_VIEW(Module.REPORTING, "Consulter les rapports financiers"),
    REPORT_EXPORT(Module.REPORTING, "Exporter les rapports"),

    AUDIT_VIEW(Module.AUDIT, "Consulter le journal d'audit");

    /** Regroupement des permissions pour l'affichage (matrice des rôles). */
    public enum Module {
        HOTEL("Hôtel et paramétrage"),
        USERS("Utilisateurs et rôles"),
        ROOMS("Chambres"),
        CUSTOMERS("Clients"),
        COMPANIES("Entreprises"),
        RATES("Tarifs et promotions"),
        RESERVATIONS("Réservations"),
        STAYS("Séjours"),
        FOLIOS("Folios et remises"),
        INVOICING("Facturation"),
        PAYMENTS("Paiements"),
        CASH("Caisse"),
        NIGHT_AUDIT("Clôture journalière"),
        HOUSEKEEPING("Housekeeping"),
        MAINTENANCE("Maintenance"),
        RESTAURANT("Restaurant / bar"),
        STOCK("Stocks"),
        SUPPLIERS("Fournisseurs"),
        EXPENSES("Dépenses"),
        REPORTING("Rapports"),
        AUDIT("Audit");

        private final String label;

        Module(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final Module module;
    private final String description;

    Permission(Module module, String description) {
        this.module = module;
        this.description = description;
    }

    public Module module() {
        return module;
    }

    public String description() {
        return description;
    }
}
