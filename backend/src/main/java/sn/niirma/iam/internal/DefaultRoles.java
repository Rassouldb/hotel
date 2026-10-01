package sn.niirma.iam.internal;

import static sn.niirma.iam.Permission.*;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import sn.niirma.iam.Permission;

/**
 * Rôles créés pour chaque organisation (matrice §7.5). Le profil super administrateur n'est pas un
 * rôle : c'est un indicateur de l'utilisateur, qui donne toutes les permissions sur tous les hôtels.
 * Les permissions par défaut ne sont appliquées qu'à la création ; elles restent modifiables ensuite.
 */
final class DefaultRoles {

    record Definition(String code, String name, String description, Set<Permission> permissions) {
    }

    static final List<Definition> ALL = List.of(
            new Definition("DIRECTOR", "Directeur / manager",
                    "Supervision complète de l'hôtel, tarifs, rapports, validations",
                    EnumSet.complementOf(EnumSet.of(ROLE_MANAGE))),
            new Definition("RECEPTIONIST", "Réceptionniste",
                    "Réservations, clients, check-in/out, folios, paiements, factures",
                    EnumSet.of(HOTEL_VIEW, ROOM_VIEW, CUSTOMER_VIEW, CUSTOMER_CREATE, CUSTOMER_UPDATE,
                            CUSTOMER_ID_DOCUMENT_VIEW, COMPANY_VIEW, RATE_VIEW, RESERVATION_VIEW, RESERVATION_CREATE,
                            RESERVATION_UPDATE, RESERVATION_CANCEL, CHECKIN_CREATE, CHECKOUT_CREATE, STAY_ROOM_CHANGE,
                            STAY_EXTEND, FOLIO_VIEW, FOLIO_CHARGE_POST, DISCOUNT_APPLY, INVOICE_VIEW, INVOICE_ISSUE,
                            INVOICE_PROFORMA, INVOICE_SEND, PAYMENT_VIEW, PAYMENT_CREATE, CASH_SESSION_OPEN,
                            CASH_SESSION_CLOSE, CASH_MOVEMENT_CREATE, HK_VIEW, MAINTENANCE_VIEW,
                            MAINTENANCE_TICKET_CREATE, POS_VIEW, DASHBOARD_VIEW, REPORT_OPERATIONS_VIEW)),
            new Definition("CASHIER", "Caissier",
                    "Encaissements, sessions de caisse, remboursements",
                    EnumSet.of(HOTEL_VIEW, CUSTOMER_VIEW, RESERVATION_VIEW, FOLIO_VIEW, FOLIO_CHARGE_POST, INVOICE_VIEW,
                            INVOICE_ISSUE, INVOICE_SEND, PAYMENT_VIEW, PAYMENT_CREATE, PAYMENT_CANCEL, PAYMENT_REFUND,
                            CASH_SESSION_OPEN, CASH_SESSION_CLOSE, CASH_MOVEMENT_CREATE, POS_VIEW, POS_ORDER_CREATE,
                            EXPENSE_VIEW, EXPENSE_CREATE, DASHBOARD_VIEW, REPORT_OPERATIONS_VIEW)),
            new Definition("ACCOUNTANT", "Comptable",
                    "Factures, avoirs, paiements, dépenses, fournisseurs, rapports financiers",
                    EnumSet.of(HOTEL_VIEW, CUSTOMER_VIEW, COMPANY_VIEW, COMPANY_MANAGE, RATE_VIEW, RESERVATION_VIEW,
                            FOLIO_VIEW, INVOICE_VIEW, INVOICE_ISSUE, INVOICE_PROFORMA, CREDIT_NOTE_ISSUE, INVOICE_SEND,
                            PAYMENT_VIEW, NIGHT_AUDIT_VIEW, POS_VIEW, STOCK_VIEW, STOCK_MOVEMENT_CREATE, STOCK_INVENTORY,
                            SUPPLIER_MANAGE, PURCHASE_MANAGE, SUPPLIER_PAYMENT_CREATE, EXPENSE_VIEW, EXPENSE_CREATE,
                            EXPENSE_VALIDATE, DASHBOARD_VIEW, REPORT_OPERATIONS_VIEW, REPORT_FINANCE_VIEW, REPORT_EXPORT,
                            AUDIT_VIEW)),
            new Definition("HOUSEKEEPING_MANAGER", "Responsable housekeeping",
                    "État des chambres, tâches de ménage, affectations, inspections",
                    EnumSet.of(HOTEL_VIEW, ROOM_VIEW, RESERVATION_VIEW, HK_VIEW, HK_STATUS_UPDATE, HK_TASK_ASSIGN,
                            HK_INSPECT, MAINTENANCE_VIEW, MAINTENANCE_TICKET_CREATE, DASHBOARD_VIEW,
                            REPORT_OPERATIONS_VIEW)),
            new Definition("HOUSEKEEPING_AGENT", "Agent housekeeping",
                    "Tâches assignées, statut ménage, signalements",
                    EnumSet.of(HOTEL_VIEW, ROOM_VIEW, HK_VIEW, HK_STATUS_UPDATE, MAINTENANCE_TICKET_CREATE)),
            new Definition("MAINTENANCE_MANAGER", "Responsable maintenance",
                    "Tickets, équipements, blocages de chambres",
                    EnumSet.of(HOTEL_VIEW, ROOM_VIEW, ROOM_UPDATE, ROOM_BLOCK_MANAGE, RESERVATION_VIEW, HK_VIEW,
                            MAINTENANCE_VIEW, MAINTENANCE_TICKET_CREATE, MAINTENANCE_TICKET_MANAGE, STOCK_VIEW,
                            EXPENSE_VIEW, EXPENSE_CREATE, REPORT_OPERATIONS_VIEW)),
            new Definition("RESTAURANT_MANAGER", "Responsable restaurant",
                    "Points de vente, commandes, produits, transfert en chambre",
                    EnumSet.of(HOTEL_VIEW, CUSTOMER_VIEW, FOLIO_VIEW, FOLIO_CHARGE_POST, PAYMENT_VIEW, PAYMENT_CREATE,
                            CASH_SESSION_OPEN, CASH_SESSION_CLOSE, CASH_MOVEMENT_CREATE, POS_VIEW, POS_ORDER_CREATE,
                            POS_ORDER_VOID, POS_TRANSFER_TO_ROOM, POS_PRODUCT_MANAGE, STOCK_VIEW, STOCK_MOVEMENT_CREATE,
                            SUPPLIER_MANAGE, PURCHASE_MANAGE, EXPENSE_VIEW, EXPENSE_CREATE, REPORT_OPERATIONS_VIEW)),
            new Definition("NIGHT_AUDITOR", "Auditeur de nuit",
                    "Clôture journalière, contrôles, rapports de nuit",
                    EnumSet.of(HOTEL_VIEW, ROOM_VIEW, CUSTOMER_VIEW, COMPANY_VIEW, RATE_VIEW, RESERVATION_VIEW,
                            RESERVATION_CREATE, RESERVATION_UPDATE, RESERVATION_NO_SHOW, CHECKIN_CREATE,
                            CHECKOUT_CREATE, FOLIO_VIEW, FOLIO_CHARGE_POST, INVOICE_VIEW, INVOICE_ISSUE, PAYMENT_VIEW,
                            PAYMENT_CREATE, CASH_SESSION_OPEN, CASH_SESSION_CLOSE, CASH_MOVEMENT_CREATE,
                            NIGHT_AUDIT_RUN, NIGHT_AUDIT_VIEW, HK_VIEW, DASHBOARD_VIEW, REPORT_OPERATIONS_VIEW)));

    private DefaultRoles() {
    }
}
