package it.boff.bike.calendario;

/** Come si usa una fascia oraria: al banco con i clienti o in officina. */
public enum TipoFascia {
    VENDITA("Vendita"),
    MANUTENZIONE("Officina");

    private final String etichetta;

    TipoFascia(String etichetta) {
        this.etichetta = etichetta;
    }

    public String getEtichetta() {
        return etichetta;
    }
}
