package it.boff.bike.ui.views;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;

/** Riquadro "in arrivo" per le funzioni delle prossime fasi. */
final class Segnaposto extends Div {

    Segnaposto(String titolo, String descrizione, String fase) {
        addClassName("boff-card");
        H3 h = new H3(titolo);
        h.getStyle().set("margin-top", "0");
        Paragraph p = new Paragraph(descrizione);
        Paragraph f = new Paragraph("In arrivo: " + fase);
        f.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "var(--lumo-font-size-s)");
        add(h, p, f);
    }
}
