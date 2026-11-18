package it.boff.bike.bicicletta;

import it.boff.bike.common.ErroreDominio;

/** La bici non esiste o non appartiene all'utente che la richiede. */
public class BiciclettaNonTrovataException extends ErroreDominio {

    public BiciclettaNonTrovataException() {
        super("Bicicletta non trovata");
    }
}
