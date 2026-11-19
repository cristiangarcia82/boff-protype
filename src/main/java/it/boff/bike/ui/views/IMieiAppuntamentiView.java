package it.boff.bike.ui.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import it.boff.bike.appuntamento.Appuntamento;
import it.boff.bike.appuntamento.AppuntamentoService;
import it.boff.bike.appuntamento.StatoAppuntamento;
import it.boff.bike.appuntamento.TipoAppuntamento;
import it.boff.bike.bicicletta.Bicicletta;
import it.boff.bike.bicicletta.BiciclettaService;
import it.boff.bike.calendario.CalendarioService;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.security.Ruoli;
import it.boff.bike.security.UtenteCorrente;
import it.boff.bike.ui.componenti.Avvisi;
import it.boff.bike.ui.componenti.Formati;
import jakarta.annotation.security.RolesAllowed;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Il cliente prenota una consulenza: sceglie il tipo, poi giorno e ora fra quelli liberi.
 * La richiesta tiene l'orario; il negozio conferma (arriva un avviso).
 */
@Route("i-miei-appuntamenti")
@PageTitle("Appuntamenti | Boff Bike")
@RolesAllowed(Ruoli.CLIENTE)
public class IMieiAppuntamentiView extends Div {

    private final AppuntamentoService service;
    private final CalendarioService calendario;
    private final Long clienteId;
    private final List<Bicicletta> bici;
    private final Div prenota = new Div();
    private final Div elenco = new Div();

    private TipoAppuntamento tipoScelto;
    private LocalDate giornoScelto;
    private LocalTime oraScelta;

    public IMieiAppuntamentiView(AppuntamentoService service, CalendarioService calendario, BiciclettaService biciclette,
                                 UtenteCorrente utenteCorrente) {
        this.service = service;
        this.calendario = calendario;
        this.clienteId = utenteCorrente.get().orElseThrow().getId();
        this.bici = biciclette.elencoDi(clienteId);
        addClassNames("boff-pagina", "boff-pagina-cliente", "boff-colonna");

        Paragraph intro = new Paragraph("Vuoi un consiglio per una bici nuova, un'e-bike o la posizione in sella? "
                + "Prenota un appuntamento: in quell'orario siamo a disposizione solo per te.");
        intro.addClassName("boff-testo-secondario");
        Button inizia = new Button("Prenota un appuntamento", VaadinIcon.CALENDAR_CLOCK.create(), e -> mostraPrenotazione());
        inizia.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        prenota.addClassNames("boff-card", "boff-colonna", "boff-prenota");
        prenota.setVisible(false);
        add(new H2("Appuntamenti"), intro, new Div(inizia), prenota, elenco);
        aggiornaElenco();
    }

    // ------------------------------------------------------------ prenotazione

    private void mostraPrenotazione() {
        tipoScelto = null;
        giornoScelto = null;
        oraScelta = null;
        prenota.removeAll();
        prenota.setVisible(true);

        RadioButtonGroup<TipoAppuntamento> tipo = new RadioButtonGroup<>("1. Di cosa hai bisogno?");
        tipo.setItems(service.tipiAttivi());
        tipo.setRenderer(new com.vaadin.flow.data.renderer.ComponentRenderer<>(t -> {
            Span nome = new Span(t.getNome());
            nome.addClassName("boff-tipo-nome");
            Div d = new Div(nome);
            if (t.getDescrizione() != null) {
                Span descr = new Span(t.getDescrizione() + " Circa " + t.getDurataMin() + " minuti.");
                descr.addClassName("boff-testo-secondario");
                d.add(new Div(descr));
            }
            return d;
        }));
        tipo.addThemeName("vertical");
        Div giorni = new Div();
        Div ore = new Div();
        Div dettagli = new Div();
        tipo.addValueChangeListener(e -> {
            tipoScelto = e.getValue();
            giornoScelto = null;
            oraScelta = null;
            ore.removeAll();
            dettagli.removeAll();
            mostraGiorni(giorni, ore, dettagli);
        });
        prenota.add(new H3("Nuovo appuntamento"), tipo, giorni, ore, dettagli);
        prenota.scrollIntoView();
    }

    private void mostraGiorni(Div giorni, Div ore, Div dettagli) {
        giorni.removeAll();
        LocalDate oggi = calendario.oggi();
        Map<LocalDate, List<LocalTime>> liberi = service.orariLiberi(tipoScelto.getId(), oggi,
                oggi.plusDays(AppuntamentoService.GIORNI_PRENOTABILI));
        Span titolo = new Span("2. Quando?");
        titolo.addClassName("boff-passo");
        giorni.add(titolo);
        if (liberi.isEmpty()) {
            Paragraph p = new Paragraph("Nessun orario libero nelle prossime settimane: chiamaci e troviamo una soluzione.");
            p.addClassName("boff-testo-secondario");
            giorni.add(p);
            return;
        }
        Div scelte = new Div();
        scelte.addClassName("boff-scelte");
        for (var voce : liberi.entrySet()) {
            Button b = new Button(Formati.giorno(voce.getKey()));
            b.addThemeVariants(ButtonVariant.LUMO_CONTRAST, ButtonVariant.LUMO_TERTIARY);
            b.addClickListener(e -> {
                seleziona(scelte, b);
                giornoScelto = voce.getKey();
                oraScelta = null;
                dettagli.removeAll();
                mostraOre(ore, voce.getValue(), dettagli);
            });
            scelte.add(b);
        }
        giorni.add(scelte);
    }

    private void mostraOre(Div ore, List<LocalTime> orari, Div dettagli) {
        ore.removeAll();
        Span titolo = new Span("A che ora?");
        titolo.addClassName("boff-passo");
        Div scelte = new Div();
        scelte.addClassName("boff-scelte");
        for (LocalTime t : orari) {
            Button b = new Button(t.toString());
            b.addThemeVariants(ButtonVariant.LUMO_CONTRAST, ButtonVariant.LUMO_TERTIARY);
            b.addClickListener(e -> {
                seleziona(scelte, b);
                oraScelta = t;
                mostraDettagli(dettagli);
            });
            scelte.add(b);
        }
        ore.add(titolo, scelte);
    }

    /** Un solo pulsante evidenziato fra le scelte (giorno o ora). */
    private static void seleziona(Div scelte, Button scelto) {
        scelte.getChildren().map(c -> (Button) c).forEach(b -> {
            b.removeThemeVariants(ButtonVariant.LUMO_PRIMARY);
            b.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            b.getElement().setAttribute("aria-pressed", "false");
        });
        scelto.removeThemeVariants(ButtonVariant.LUMO_TERTIARY);
        scelto.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        scelto.getElement().setAttribute("aria-pressed", "true");
    }

    private void mostraDettagli(Div dettagli) {
        if (!dettagli.getChildren().findAny().isEmpty()) return;
        Span titolo = new Span("3. Qualcosa da dirci? (facoltativo)");
        titolo.addClassName("boff-passo");
        Select<Bicicletta> biciScelta = new Select<>();
        biciScelta.setLabel("Riguarda una tua bici?");
        biciScelta.setItems(bici);
        biciScelta.setItemLabelGenerator(b -> b == null ? "No" : b.getNome());
        biciScelta.setEmptySelectionAllowed(true);
        biciScelta.setEmptySelectionCaption("No");
        biciScelta.setVisible(!bici.isEmpty());
        TextArea note = new TextArea("Note");
        note.setPlaceholder("Es. budget, uso che ne fai, misure…");
        note.setMaxLength(AppuntamentoService.MAX_NOTE);
        note.setWidthFull();
        Button invia = new Button("Invia la richiesta", e -> {
            try {
                service.richiedi(clienteId, tipoScelto.getId(), giornoScelto, oraScelta,
                        biciScelta.getValue() == null ? null : biciScelta.getValue().getId(), note.getValue());
                prenota.setVisible(false);
                Avvisi.ok("Richiesta inviata: ti avvisiamo appena la confermiamo");
                aggiornaElenco();
            } catch (ErroreDominio ex) {
                Avvisi.errore(ex.getMessage());
            }
        });
        invia.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dettagli.add(titolo, biciScelta, note, new Div(invia));
    }

    // ------------------------------------------------------------ elenco

    private void aggiornaElenco() {
        elenco.removeAll();
        LocalDate oggi = calendario.oggi();
        List<Appuntamento> tutti = service.delCliente(clienteId);
        List<Appuntamento> prossimi = tutti.stream().filter(a -> a.isAttivo() && !a.getData().isBefore(oggi))
                .sorted(java.util.Comparator.comparing(Appuntamento::getData).thenComparing(Appuntamento::getOraInizio))
                .toList();
        H3 titolo = new H3("I tuoi appuntamenti");
        elenco.add(titolo);
        if (prossimi.isEmpty()) {
            Paragraph p = new Paragraph("Nessun appuntamento in programma.");
            p.addClassName("boff-testo-secondario");
            elenco.add(p);
        }
        prossimi.forEach(a -> elenco.add(voce(a)));
    }

    private Component voce(Appuntamento a) {
        Span quando = new Span(Formati.giorno(a.getData()) + " alle " + a.getOraInizio());
        quando.addClassName("boff-voce-quando");
        Span stato = new Span(a.getStato() == StatoAppuntamento.RICHIESTO ? "In attesa di conferma" : "Confermato");
        stato.getElement().getThemeList().add(a.getStato() == StatoAppuntamento.RICHIESTO ? "badge contrast" : "badge success");
        Div testo = new Div(new Div(quando, new Span(" "), stato), new Div(new Span(a.getTipo().getNome())));
        Button annulla = new Button("Annulla", e -> {
            ConfirmDialog c = new ConfirmDialog("Annullare l'appuntamento?",
                    a.getTipo().getNome() + ", " + Formati.giorno(a.getData()) + " alle " + a.getOraInizio() + ".",
                    "Annulla appuntamento", ev -> {
                        try {
                            service.annullaDelCliente(clienteId, a.getId());
                            Avvisi.ok("Appuntamento annullato");
                            aggiornaElenco();
                        } catch (ErroreDominio ex) {
                            Avvisi.errore(ex.getMessage());
                        }
                    });
            c.setCancelable(true);
            c.setCancelText("Tienilo");
            c.setConfirmButtonTheme("error primary");
            c.open();
        });
        annulla.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
        Div voce = new Div(testo, annulla);
        voce.addClassNames("boff-voce-appuntamento", "boff-intestazione");
        if (a.getStato() == StatoAppuntamento.RICHIESTO) voce.addClassName("richiesto");
        return voce;
    }
}
