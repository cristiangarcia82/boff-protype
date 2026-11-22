package it.boff.bike.pianificazione;

/**
 * Il negozio ha messo (o spostato) in calendario un lavoro annunciato, prima che arrivi la bici:
 * il cliente va avvisato di quando portarla. Pubblicato dopo il ricalcolo, ascoltato dopo il commit.
 */
public record LavoroAnnunciatoFissato(Long lavoroId) {
}
