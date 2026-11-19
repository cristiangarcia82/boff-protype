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

    private final it.boff.bike.funzionalita.FunzionalitaService funzionalita;

    public StatisticheView(StatisticheService statistiche, it.boff.bike.funzionalita.FunzionalitaService funzionalita) {
        this.statistiche = statistiche;
        this.funzionalita = funzionalita;
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
        if (funzionalita.attiva(it.boff.bike.funzionalita.Funzionalita.TEMPO_EFFETTIVO)) {
            contenuto.add(tempiReali(giorni));
        }
    }

    /** Tempo standard del catalogo contro tempo reale registrato. */
    private Div tempiReali(int giorni) {
        Div card = new Div(new H3("Tempi standard e tempi reali"));
        card.addClassName("boff-card");
        card.getStyle().set("margin-top", "var(--lumo-space-m)");
        var righe = statistiche.tempiEffettivi(giorni);
        if (righe.isEmpty()) {
            Paragraph p = new Paragraph("Ancora nessun tempo reale registrato nel periodo: si inserisce nel dettaglio "
                    + "del lavoro quando un intervento è fatto.");
            p.addClassName("boff-testo-secondario");
            card.add(p);
            return card;
        }
        com.vaadin.flow.component.grid.Grid<StatisticheService.Confronto> g = new com.vaadin.flow.component.grid.Grid<>();
        g.addColumn(StatisticheService.Confronto::intervento).setHeader("Intervento").setFlexGrow(3);
        g.addColumn(StatisticheService.Confronto::casi).setHeader("Casi").setAutoWidth(true);
        g.addColumn(c -> Durate.formatta(Math.round(c.stimatoMedio()))).setHeader("Standard").setAutoWidth(true);
        g.addColumn(c -> Durate.formatta(Math.round(c.effettivoMedio()))).setHeader("Reale (media)").setAutoWidth(true);
        g.addColumn(c -> (c.scartoPercento() > 0 ? "+" : "") + c.scartoPercento() + "%").setHeader("Scarto").setAutoWidth(true);
        g.setItems(righe);
        g.setAllRowsVisible(true);
        Paragraph nota = new Paragraph("Scarto positivo = ci si mette più del tempo standard: valuta di aggiornarlo "
                + "nel Catalogo interventi. Con pochi casi la media è poco affidabile.");
        nota.addClassName("boff-testo-secondario");
        card.add(g, nota);
        return card;
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
