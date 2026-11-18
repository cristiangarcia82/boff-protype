package it.boff.bike.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.ZoneId;

/**
 * Proprietà applicative (prefisso {@code boff}).
 *
 * @param titolare      credenziali del primo account titolare, creato all'avvio se non esiste
 * @param demo          se true crea utenti e dati di prova (solo sviluppo!)
 * @param fusoOrario    fuso orario del negozio: "oggi" e gli orari di lavoro si calcolano qui,
 *                      indipendentemente dal fuso del server (di solito UTC)
 * @param rememberMeKey segreto per firmare il cookie "resta collegato"; se vuoto ne viene
 *                      generato uno casuale a ogni avvio (gli utenti dovranno rifare il login)
 * @param urlPubblico   indirizzo pubblico dell'app, usato nei link delle email
 */
@ConfigurationProperties(prefix = "boff")
public record BoffProperties(
        Titolare titolare,
        boolean demo,
        @DefaultValue("Europe/Rome") ZoneId fusoOrario,
        String rememberMeKey,
        @DefaultValue("http://localhost:8080") String urlPubblico) {

    public BoffProperties {
        if (titolare == null) {
            titolare = new Titolare(null, null, "Titolare", "");
        }
    }

    public record Titolare(String email, String password, String nome, String cognome) {
    }
}
