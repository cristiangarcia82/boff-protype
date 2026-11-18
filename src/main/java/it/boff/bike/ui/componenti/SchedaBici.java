package it.boff.bike.ui.componenti;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import it.boff.bike.bicicletta.Bicicletta;
import it.boff.bike.bicicletta.Tipologia;

import java.util.ArrayList;
import java.util.List;

/** Scheda cliccabile di una bicicletta (usata dal cliente e dal negozio). */
public class SchedaBici extends Div {

    public SchedaBici(Bicicletta b, Runnable alClic) {
        Image icona = new Image("icons/icon.png", "");
        icona.addClassName("boff-bici-icona");

        Span nome = new Span(b.getNome());
        nome.addClassName("boff-bici-nome");
        Span info = new Span(descrizione(b));
        info.addClassName("boff-bici-info");

        Div testi = new Div(nome, info);
        testi.addClassName("boff-bici-testi");

        add(icona, testi, VaadinIcon.ANGLE_RIGHT.create());
        addClassNames("boff-card", "boff-bici");
        getElement().setAttribute("role", "button");
        getElement().setAttribute("tabindex", "0");
        getElement().setAttribute("aria-label", "Apri " + b.getNome());
        addClickListener(e -> alClic.run());
        Shortcuts.addShortcutListener(this, alClic::run, Key.ENTER).listenOn(this);
    }

    public static String descrizione(Bicicletta b) {
        List<String> dettagli = new ArrayList<>();
        dettagli.add(b.getTipologia().getEtichetta());
        if (b.isElettrica() && b.getTipologia() != Tipologia.EBIKE) {
            dettagli.add("elettrica");
        }
        if (b.getTaglia() != null) dettagli.add("taglia " + b.getTaglia());
        if (b.getColore() != null) dettagli.add(b.getColore());
        if (b.getAnno() != null) dettagli.add(String.valueOf(b.getAnno()));
        return String.join(" · ", dettagli);
    }
}
