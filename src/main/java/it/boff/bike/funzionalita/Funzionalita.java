package it.boff.bike.funzionalita;

/** Funzionalità opzionali: ognuna è spenta finché il titolare non la attiva. */
public enum Funzionalita {
    APPROVAZIONE_EXTRA("funzione.approvazione-extra"),
    PROMEMORIA_MANUTENZIONE("funzione.promemoria"),
    TEMPO_EFFETTIVO("funzione.tempo-effettivo"),
    AVVISO_CARICO("funzione.avviso-carico");

    private final String chiave;

    Funzionalita(String chiave) {
        this.chiave = chiave;
    }

    String chiave() {
        return chiave;
    }
}
