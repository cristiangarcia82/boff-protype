package it.boff.bike.appuntamento;

/** Evento per le notifiche: cosa è successo a un appuntamento. */
public record AppuntamentoCambiato(Long appuntamentoId, Cosa cosa) {
    public enum Cosa { RICHIESTO, CREATO_DAL_NEGOZIO, CONFERMATO, SPOSTATO, ANNULLATO_DAL_NEGOZIO, ANNULLATO_DAL_CLIENTE }
}
