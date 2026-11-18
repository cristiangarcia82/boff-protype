package it.boff.bike.lavoro;

import java.util.EnumSet;
import java.util.Set;

/**
 * Ciclo di vita di un lavoro.
 * <pre>
 * ANNUNCIATO ──check-in──▶ IN_NEGOZIO ──pianificazione──▶ PIANIFICATO
 *                               │                              │
 *                               └────────────┬─────────────────┘
 *                                            ▼
 *                                     IN_LAVORAZIONE ──▶ PRONTO ──▶ RITIRATO
 * (ANNULLATO da qualsiasi stato aperto)
 * </pre>
 */
public enum StatoLavoro {
    ANNUNCIATO("Annunciato", "Il cliente ha annunciato il lavoro: la bici non è ancora in negozio"),
    IN_NEGOZIO("In negozio", "La bici è in negozio, in attesa di essere pianificata"),
    PIANIFICATO("Pianificato", "Il lavoro è in calendario"),
    IN_LAVORAZIONE("In lavorazione", "Ci stiamo lavorando"),
    PRONTO("Pronta", "La bici è pronta per il ritiro"),
    RITIRATO("Ritirata", "La bici è stata ritirata"),
    ANNULLATO("Annullato", "Il lavoro è stato annullato");

    /** Stati in cui la bici è fisicamente in negozio e c'è ancora da lavorare. */
    public static final Set<StatoLavoro> IN_OFFICINA = EnumSet.of(IN_NEGOZIO, PIANIFICATO, IN_LAVORAZIONE);
    public static final Set<StatoLavoro> CHIUSI = EnumSet.of(RITIRATO, ANNULLATO);
    public static final Set<StatoLavoro> APERTI = EnumSet.complementOf(EnumSet.of(RITIRATO, ANNULLATO));

    private final String etichetta;
    private final String descrizione;

    StatoLavoro(String etichetta, String descrizione) {
        this.etichetta = etichetta;
        this.descrizione = descrizione;
    }

    public String getEtichetta() { return etichetta; }
    public String getDescrizione() { return descrizione; }
    public boolean isAperto() { return !CHIUSI.contains(this); }
}
