package it.boff.bike.bicicletta;

/** Tipologia di bicicletta. */
public enum Tipologia {
    CITTA("Città"),
    STRADA("Bici da strada"),
    GRAVEL("Gravel"),
    MTB("Mountain bike (MTB)"),
    TREKKING("Trekking"),
    EBIKE("E-bike"),
    BAMBINO("Bambino"),
    ALTRO("Altro");

    private final String etichetta;

    Tipologia(String etichetta) {
        this.etichetta = etichetta;
    }

    public String getEtichetta() {
        return etichetta;
    }
}
