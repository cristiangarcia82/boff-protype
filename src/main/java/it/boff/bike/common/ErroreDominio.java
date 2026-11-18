package it.boff.bike.common;

/**
 * Errore dovuto a una regola del negozio (non a un bug): il messaggio è in italiano
 * ed è pensato per essere mostrato all'utente così com'è.
 */
public class ErroreDominio extends RuntimeException {

    public ErroreDominio(String messaggio) {
        super(messaggio);
    }
}
