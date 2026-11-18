package it.boff.bike.ui.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import it.boff.bike.lavoro.Lavoro;
import it.boff.bike.lavoro.LavoroService;
import it.boff.bike.lavoro.StatoLavoro;
import it.boff.bike.security.Ruoli;
import it.boff.bike.ui.componenti.Formati;
import jakarta.annotation.security.RolesAllowed;

import java.util.EnumSet;
import java.util.List;

/**
 * Bacheca del personale (titolare e collaboratore).
 * Pensata per il monitor del Mac: indicatori in riga, pannello principale largo
 * e colonna laterale; su telefono gli stessi blocchi si impilano.
 */
@Route("bacheca")
@PageTitle("Bacheca | Boff Bike")
@RolesAllowed(Ruoli.STAFF)
public class BachecaView extends Div {

    public BachecaView(LavoroService lavori) {
        addClassName("boff-pagina");

        Div kpi = new Div(
                indicatore(String.valueOf(lavori.conta(StatoLavoro.IN_OFFICINA)), "Bici in officina"),
                indicatore(String.valueOf(lavori.conta(EnumSet.of(StatoLavoro.PRONTO))), "Pronte da ritirare"),
                indicatore(String.valueOf(lavori.conta(EnumSet.of(StatoLavoro.ANNUNCIATO))), "Annunci in attesa della bici"),
                indicatore("–", "Carico officina settimana"));
        kpi.addClassName("boff-kpi");

        Div principale = new Div(
                elencoLavori("In officina", lavori.cerca(StatoLavoro.IN_OFFICINA, ""),
                        "Nessuna bici in officina."),
                new Segnaposto("Calendario della settimana",
                        "Vista a colonne per te e per il collaboratore: turni, ore di vendita e di officina, "
                                + "lavori trascinabili da un giorno all'altro.", "fasi 3–4"));
        principale.addClassName("boff-colonna");

        Div laterale = new Div(
                elencoLavori("Nuovi annunci", lavori.cerca(EnumSet.of(StatoLavoro.ANNUNCIATO), ""),
                        "Nessun annuncio in attesa."),
                elencoLavori("Pronte da ritirare", lavori.cerca(EnumSet.of(StatoLavoro.PRONTO), ""),
                        "Nessuna bici in attesa di ritiro."));
        laterale.addClassName("boff-colonna");

        Div corpo = new Div(principale, laterale);
        corpo.addClassName("boff-bacheca-corpo");

        add(new H2("Bacheca"), kpi, corpo);
    }

    private static Component elencoLavori(String titolo, List<Lavoro> elenco, String vuoto) {
        Div card = new Div(new H3(titolo));
        card.addClassName("boff-card");
        if (elenco.isEmpty()) {
            Paragraph p = new Paragraph(vuoto);
            p.addClassName("boff-testo-secondario");
            card.add(p);
        }
        elenco.stream().limit(8).forEach(l -> {
            RouterLink link = new RouterLink(LavoriView.class, LavoriView.parametri(l.getId()));
            Span bici = new Span(l.getBicicletta().getNome());
            bici.addClassName("boff-bici-nome-piccolo");
            Span dettaglio = new Span(l.getBicicletta().getProprietario().getNomeCompleto()
                    + (l.getFinePrevista() != null ? " · prevista " + Formati.giorno(l.getFinePrevista()) : ""));
            dettaglio.addClassName("boff-testo-secondario");
            link.add(bici, dettaglio);
            link.addClassName("boff-voce-elenco");
            card.add(link);
        });
        if (elenco.size() > 8) {
            card.add(new RouterLink("Vedi tutti (" + elenco.size() + ")", LavoriView.class));
        }
        return card;
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
