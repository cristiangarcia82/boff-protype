package it.boff.bike.ui.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import it.boff.bike.calendario.CalendarioService;
import it.boff.bike.calendario.Disponibilita;
import it.boff.bike.calendario.FasciaOraria;
import it.boff.bike.calendario.TipoFascia;
import it.boff.bike.security.Ruoli;
import it.boff.bike.ui.componenti.Durate;
import it.boff.bike.ui.componenti.Formati;
import it.boff.bike.utente.Utente;
import jakarta.annotation.security.RolesAllowed;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Calendario settimanale: una riga per persona, una colonna per giorno.
 * Mostra le fasce di vendita e di officina, assenze e chiusure.
 */
@Route("calendario")
@PageTitle("Calendario | Boff Bike")
@RolesAllowed(Ruoli.STAFF)
public class CalendarioView extends Div {

    private static final DateTimeFormatter TITOLO = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALY);

    private final CalendarioService calendario;
    private final Div griglia = new Div();
    private final Span periodo = new Span();
    private LocalDate lunedi;

    public CalendarioView(CalendarioService calendario) {
        this.calendario = calendario;
        this.lunedi = calendario.oggi().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        addClassName("boff-pagina");

        Button prec = new Button(VaadinIcon.ANGLE_LEFT.create(), e -> sposta(-1));
        prec.setAriaLabel("Settimana precedente");
        Button oggi = new Button("Oggi", e -> {
            lunedi = calendario.oggi().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            mostra();
        });
        Button succ = new Button(VaadinIcon.ANGLE_RIGHT.create(), e -> sposta(1));
        succ.setAriaLabel("Settimana successiva");
        for (Button b : List.of(prec, oggi, succ)) b.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        periodo.addClassName("boff-periodo");
        Div navigazione = new Div(prec, oggi, succ, periodo);
        navigazione.addClassName("boff-navigazione-settimana");

        Div intestazione = new Div(new H2("Calendario"), navigazione);
        intestazione.addClassName("boff-intestazione");
        Div legenda = new Div(voceLegenda("vendita", "Vendita (banco)"), voceLegenda("officina", "Officina"),
                voceLegenda("assenza", "Assente / chiuso"));
        legenda.addClassName("boff-legenda");

        Div scorrimento = new Div(griglia);
        scorrimento.addClassName("boff-scorrimento-orizzontale");
        add(intestazione, legenda, scorrimento);
        mostra();
    }

    private void sposta(int settimane) {
        lunedi = lunedi.plusWeeks(settimane);
        mostra();
    }

    private void mostra() {
        LocalDate domenica = lunedi.plusDays(6);
        periodo.setText(lunedi.getDayOfMonth() + " – " + TITOLO.format(domenica));
        Disponibilita d = calendario.disponibilita(lunedi, domenica);
        List<LocalDate> giorni = Stream.iterate(lunedi, g -> g.plusDays(1)).limit(7)
                // la domenica si mostra solo se qualcuno lavora
                .filter(g -> g.getDayOfWeek() != DayOfWeek.SUNDAY
                        || d.personale().stream().anyMatch(p -> !d.fasce(p.getId(), g).isEmpty()))
                .toList();

        griglia.removeAll();
        griglia.addClassName("boff-griglia-settimana");
        griglia.getStyle().set("grid-template-columns", "minmax(110px, 0.6fr) repeat(" + giorni.size() + ", minmax(140px, 1fr))");

        griglia.add(new Div());
        LocalDate oggi = calendario.oggi();
        for (LocalDate g : giorni) {
            Span nome = new Span(Formati.giorno(g));
            Div testa = new Div(nome);
            testa.addClassName("boff-testa-giorno");
            if (g.equals(oggi)) testa.addClassName("oggi");
            d.chiusura(g).ifPresent(motivo -> {
                Span c = new Span("Chiuso · " + motivo);
                c.addClassName("boff-chiuso");
                testa.add(c);
            });
            griglia.add(testa);
        }
        if (d.personale().isEmpty()) {
            Div vuoto = new Div(new Span("Nessun orario impostato: il titolare può farlo da Impostazioni → Orari."));
            vuoto.getStyle().set("grid-column", "1 / -1");
            vuoto.addClassName("boff-testo-secondario");
            griglia.add(vuoto);
        }
        for (Utente p : d.personale()) {
            Div nome = new Div(new Span(p.getNomeCompleto()));
            nome.addClassName("boff-nome-persona");
            griglia.add(nome);
            for (LocalDate g : giorni) {
                griglia.add(cella(d, p, g));
            }
        }
    }

    /** Contenuto di una cella persona/giorno. */
    protected Component cella(Disponibilita d, Utente p, LocalDate g) {
        Div cella = new Div();
        cella.addClassName("boff-cella");
        if (d.chiusura(g).isPresent()) {
            cella.addClassName("chiusa");
            return cella;
        }
        var assenza = d.assenza(p.getId(), g);
        if (assenza.isPresent()) {
            Span a = new Span(assenza.get().getMotivo().getEtichetta());
            a.addClassNames("boff-blocco", "assenza");
            cella.add(a);
            return cella;
        }
        List<FasciaOraria> fasce = d.fasce(p.getId(), g);
        if (fasce.isEmpty()) {
            Span libero = new Span("Non lavora");
            libero.addClassName("boff-testo-secondario");
            cella.add(libero);
            return cella;
        }
        for (FasciaOraria f : fasce) {
            Span b = new Span(f.getOraInizio() + "–" + f.getOraFine() + " " + f.getTipo().getEtichetta());
            b.addClassNames("boff-blocco", f.getTipo() == TipoFascia.VENDITA ? "vendita" : "officina");
            cella.add(b);
        }
        long officina = d.minuti(p.getId(), g, TipoFascia.MANUTENZIONE);
        if (officina > 0) {
            Span cap = new Span("Officina: " + Durate.formatta(officina));
            cap.addClassName("boff-capacita");
            cella.add(cap);
        }
        return cella;
    }

    private static Span voceLegenda(String classe, String testo) {
        Span quadrato = new Span();
        quadrato.addClassNames("boff-quadrato", classe);
        Span s = new Span(quadrato, new Span(testo));
        s.addClassName("boff-voce-legenda");
        return s;
    }
}
