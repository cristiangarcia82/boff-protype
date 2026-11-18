package it.boff.bike.security;

/**
 * Nomi dei ruoli Spring Security (senza prefisso "ROLE_"),
 * da usare in {@code @RolesAllowed} e nei controlli di accesso.
 */
public final class Ruoli {

    public static final String STAFF = "STAFF";
    public static final String CLIENTE = "CLIENTE";
    /** Ruolo aggiuntivo concesso al solo titolare. */
    public static final String TITOLARE = "TITOLARE";

    private Ruoli() {
    }
}
