package it.boff.bike.ui.views;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import it.boff.bike.security.Ruoli;
import it.boff.bike.statistiche.StatisticheService;
import it.boff.bike.statistiche.StatisticheService.Intervento;
import it.boff.bike.ui.componenti.Durate;
import jakarta.annotation.security.RolesAllowed;

import java.util.List;
import java.util.Locale;

/** Andamento dell'officina: numeri chiave e interventi più richiesti. Solo per il titolare. */
@Route("statistiche")
@PageTitle("Statistiche | Boff Bike")
@RolesAllowed(Ruoli.TITOLARE)
public class StatisticheView extends Div {

    private static final int[] PERIODI = {30, 90, 365};

    private final StatisticheService statistiche;
    private final Div contenuto = new Div();

    public StatisticheView(StatisticheService statistiche) {
        this.statistiche = statistiche;
        addClassName("boff-pagina");
        Tabs periodo = new Tabs(new Tab("Ultimi 30 giorni"), new Tab("Ultimi 3 mesi"), new Tab("Ultimo anno"));
        periodo.addSelectedChangeListener(e -> mostra(PERIODI[periodo.getSelectedIndex()]));
        add(new H2("Statistiche"), periodo, contenuto);
        mostra(30);
    }

    private void mostra(int giorni) {
        contenuto.removeAll();
        StatisticheService.Riepilogo r = statistiche.ultimiGiorni(giorni);

        Div kpi = new Div(
                tessera(String.valueOf(r.nuoviLavori()), "Lavori ricevuti"),
                tessera(String.valueOf(r.ritirati()), "Bici consegnate"),
                tessera(r.giorniMediInNegozio() == null ? "–"
                        : String.format(Locale.ITALY, "%.1f gg", r.giorniMediInNegozio()), "Tempo medio in negozio (check-in → pronta)"),
                tessera(r.puntualitaPercento() == null ? "–" : r.puntualitaPercento() + "%",
                        "Pronte entro la data promessa (" + r.conDataPromessa() + " con data)"),
                tessera(String.valueOf(r.nuoviClienti()), "Nuovi clienti"),
                tessera(String.valueOf(r.annullati()), "Lavori annullati"));
        kpi.addClassName("boff-kpi");
        kpi.getStyle().set("margin-top", "var(--lumo-space-m)");

        Div schedaInterventi = new Div(new H3("Interventi più richiesti"));
        schedaInterventi.addClassName("boff-card");
        List<Intervento> elenco = r.interventiFrequenti();
        if (elenco.isEmpty()) {
            Paragraph p = new Paragraph("Nessun lavoro nel periodo.");
            p.addClassName("boff-testo-secondario");
            schedaInterventi.add(p);
        } else {
            long massimo = elenco.get(0).volte();
            Div barre = new Div();
            barre.addClassName("boff-barre");
            barre.getElement().setAttribute("role", "table");
            barre.getElement().setAttribute("aria-label", "Interventi più richiesti: numero di volte e ore stimate");
            for (Intervento i : elenco) {
                Span nome = new Span(i.descrizione());
                nome.addClassName("nome");
                Span barra = new Span();
                barra.addClassName("barra");
                barra.getStyle().set("width", Math.max(2, Math.round(100.0 * i.volte() / massimo)) + "%");
                Div traccia = new Div(barra);
                traccia.addClassName("traccia");
                Span valore = new Span(i.volte() + (i.volte() == 1 ? " volta · " : " volte · ") + Durate.formatta(i.minutiTotali()));
                valore.addClassName("valore");
                Div riga = new Div(nome, traccia, valore);
                riga.addClassName("riga");
                riga.getElement().setAttribute("role", "row");
                riga.getElement().setAttribute("title", i.descrizione() + ": " + i.volte() + " volte, "
                        + Durate.formatta(i.minutiTotali()) + " stimate");
                barre.add(riga);
            }
            Paragraph nota = new Paragraph("Numero di volte nel periodo e ore di lavoro stimate (tempi standard o "
                    + "modificati sul lavoro).");
            nota.addClassName("boff-testo-secondario");
            schedaInterventi.add(barre, nota);
        }
        contenuto.add(kpi, schedaInterventi);
    }

    private static Div tessera(String valore, String etichetta) {
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
