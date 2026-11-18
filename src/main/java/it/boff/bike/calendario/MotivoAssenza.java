package it.boff.bike.calendario;

public enum MotivoAssenza {
    FERIE("Ferie"),
    RIPOSO("Giorno libero"),
    MALATTIA("Malattia"),
    ALTRO("Altro");

    private final String etichetta;

    MotivoAssenza(String etichetta) {
        this.etichetta = etichetta;
    }

    public String getEtichetta() {
        return etichetta;
    }
}
