package it.boff.bike.lavoro;

/** Stato di un intervento extra proposto al cliente (null = intervento normale, nessuna approvazione). */
public enum Approvazione {
    DA_APPROVARE("In attesa del cliente"),
    APPROVATA("Approvato dal cliente"),
    RIFIUTATA("Rifiutato dal cliente");

    private final String etichetta;

    Approvazione(String etichetta) {
        this.etichetta = etichetta;
    }

    public String getEtichetta() {
        return etichetta;
    }
}
