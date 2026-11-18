package it.boff.bike.pianificazione;

/**
 * Evento: qualcosa che influenza il calendario dell'officina è cambiato (lavori, tempi,
 * orari, assenze…). Dopo il commit la pianificazione viene ricalcolata.
 */
public record RichiestaRipianificazione(String motivo) {
}
