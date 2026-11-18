package it.boff.bike.bicicletta;

/** La bici non esiste o non appartiene all'utente che la richiede. */
public class BiciclettaNonTrovataException extends RuntimeException {

    public BiciclettaNonTrovataException() {
        super("Bicicletta non trovata");
    }
}
