package it.boff.bike.ui.componenti;

import com.vaadin.flow.component.html.Span;
import it.boff.bike.lavoro.StatoLavoro;

/** Etichetta colorata con lo stato di un lavoro. */
public class BadgeStato extends Span {

    public BadgeStato(StatoLavoro stato) {
        super(stato.getEtichetta());
        String tema = switch (stato) {
            case ANNUNCIATO -> "badge contrast";
            case IN_NEGOZIO, PIANIFICATO -> "badge";
            case IN_LAVORAZIONE -> "badge primary";
            case PRONTO -> "badge success primary";
            case RITIRATO -> "badge success";
            case ANNULLATO -> "badge error";
        };
        getElement().getThemeList().addAll(java.util.List.of(tema.split(" ")));
        setTitle(stato.getDescrizione());
    }
}
