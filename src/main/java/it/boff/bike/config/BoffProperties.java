package it.boff.bike.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Proprietà applicative (prefisso {@code boff}).
 *
 * @param titolare credenziali del primo account titolare, creato all'avvio se non esiste
 * @param demo     se true crea utenti di prova (solo sviluppo!)
 */
@ConfigurationProperties(prefix = "boff")
public record BoffProperties(Titolare titolare, boolean demo) {

    public BoffProperties {
        if (titolare == null) {
            titolare = new Titolare(null, null, "Titolare", "");
        }
    }

    public record Titolare(String email, String password, String nome, String cognome) {
    }
}
