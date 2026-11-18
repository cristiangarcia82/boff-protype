package it.boff.bike.ui.views;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import it.boff.bike.security.Ruoli;
import jakarta.annotation.security.RolesAllowed;

/**
 * Bacheca del personale (titolare e collaboratore).
 * Pensata per il monitor del Mac: indicatori in riga, pannello principale largo
 * e colonna laterale; su telefono gli stessi blocchi si impilano.
 */
@Route("bacheca")
@PageTitle("Bacheca | Boff Bike")
@RolesAllowed(Ruoli.STAFF)
public class BachecaView extends Div {

    public BachecaView() {
        addClassName("boff-pagina");

        Div kpi = new Div(
                indicatore("–", "Bici in negozio"),
                indicatore("–", "Lavori pronti da ritirare"),
                indicatore("–", "Ore di officina libere questa settimana"),
                indicatore("–", "Carico settimana"));
        kpi.addClassName("boff-kpi");

        Div principale = new Div(
                new Segnaposto("Calendario della settimana",
                        "Vista a colonne per te e per il collaboratore: turni, ore di vendita e di officina, "
                                + "lavori trascinabili da un giorno all'altro.", "fasi 3–4"),
                new Segnaposto("Lavori in negozio",
                        "Elenco filtrabile delle bici consegnate con il dettaglio del lavoro affiancato.", "fase 2"));
        principale.addClassName("boff-colonna");

        Div laterale = new Div(
                new Segnaposto("Oggi in officina", "Cosa c'è da fare oggi, per ciascuno.", "fase 4"),
                new Segnaposto("Nuovi annunci", "Lavori annunciati dai clienti, in attesa della bici.", "fase 2"),
                new Segnaposto("Assenze", "Giorni liberi e ferie in arrivo.", "fase 3"));
        laterale.addClassName("boff-colonna");

        Div corpo = new Div(principale, laterale);
        corpo.addClassName("boff-bacheca-corpo");

        add(new H2("Bacheca"), kpi, corpo);
    }

    private static Div indicatore(String valore, String etichetta) {
        Span v = new Span(valore);
        v.addClassName("valore");
        Span e = new Span(etichetta);
        e.addClassName("etichetta");
        Div card = new Div(v, e);
        card.addClassName("boff-card");
        card.getStyle().set("display", "flex").set("flex-direction", "column");
        return card;
    }
}
