package sn.niirma.iam;

/** Événements d'authentification journalisés dans {@code auth_event}. */
public enum AuthEventType {
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOGIN_LOCKED,
    ACCOUNT_LOCKED,
    LOGOUT,
    SESSION_REVOKED,
    REFRESH_TOKEN_REUSED,
    PASSWORD_CHANGED,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET
}
