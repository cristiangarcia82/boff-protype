package it.boff.bike.appuntamento;

import java.util.EnumSet;
import java.util.Set;

/** Ciclo di vita di un appuntamento di consulenza. */
public enum StatoAppuntamento {
    /** Chiesto dal cliente dall'app: lo spazio è tenuto, il negozio deve confermare. */
    RICHIESTO("Da confermare"),
    CONFERMATO("Confermato"),
    SVOLTO("Svolto"),
    ANNULLATO("Annullato");

    /** Stati che occupano tempo in calendario. */
    public static final Set<StatoAppuntamento> ATTIVI = EnumSet.of(RICHIESTO, CONFERMATO);

    private final String etichetta;

    StatoAppuntamento(String etichetta) {
        this.etichetta = etichetta;
    }

    public String getEtichetta() {
        return etichetta;
    }
}
