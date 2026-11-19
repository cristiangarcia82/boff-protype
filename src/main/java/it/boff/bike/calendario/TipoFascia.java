package it.boff.bike.calendario;

/**
 * Tipo di fascia oraria. Dalla fase 12 l'orario settimanale è solo {@link #MANUTENZIONE} (orario di lavoro,
 * per officina e appuntamenti); {@link #VENDITA} resta per compatibilità ma l'interfaccia non lo propone:
 * il tempo al banco si inserisce a mano con i turni di vendita.
 */
public enum TipoFascia {
    VENDITA("Vendita"),
    MANUTENZIONE("Lavoro");

    private final String etichetta;

    TipoFascia(String etichetta) {
        this.etichetta = etichetta;
    }

    public String getEtichetta() {
        return etichetta;
    }
}
