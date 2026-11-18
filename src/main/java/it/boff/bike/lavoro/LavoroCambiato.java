package it.boff.bike.lavoro;

/**
 * Evento pubblicato quando un lavoro cambia stato (o la data prevista di consegna):
 * le notifiche al cliente lo ascoltano dopo il commit della transazione.
 */
public record LavoroCambiato(Long lavoroId, StatoLavoro prima, StatoLavoro dopo) {
}
