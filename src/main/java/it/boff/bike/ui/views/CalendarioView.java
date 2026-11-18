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
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.dnd.DragSource;
import com.vaadin.flow.component.dnd.DropTarget;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.router.RouterLink;
import it.boff.bike.calendario.CalendarioService;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.lavoro.LavoroService;
import it.boff.bike.pianificazione.Assegnazione;
import it.boff.bike.pianificazione.EsitoPianificazione;
import it.boff.bike.pianificazione.PianificazioneService;
import it.boff.bike.ui.componenti.Avvisi;
import it.boff.bike.ui.componenti.CampoData;
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
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Calendario settimanale: una riga per persona, una colonna per giorno.
 * Mostra le fasce di vendita e di officina, assenze, chiusure e gli interventi
 * pianificati, che si possono trascinare (sul Mac) o spostare con un tocco (telefono).
 */
@Route("calendario")
@PageTitle("Calendario | Boff Bike")
@RolesAllowed(Ruoli.STAFF)
public class CalendarioView extends Div {

    private static final DateTimeFormatter TITOLO = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALY);

    private final CalendarioService calendario;
    private final PianificazioneService pianificazione;
    private final LavoroService lavori;
    private final Div griglia = new Div();
    private final Div avvisi = new Div();
    private Map<String, List<Assegnazione>> assegnazioniPerCella = Map.of();
    private final Span periodo = new Span();
    private LocalDate lunedi;

    public CalendarioView(CalendarioService calendario, PianificazioneService pianificazione, LavoroService lavori) {
        this.calendario = calendario;
        this.pianificazione = pianificazione;
        this.lavori = lavori;
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
        Button ripianifica = new Button("Ripianifica", VaadinIcon.REFRESH.create(), e -> {
            mostraEsito(pianificazione.ripianifica());
            mostra();
            Avvisi.ok("Calendario ricalcolato");
        });
        ripianifica.setTooltipText("Ricalcola il calendario (gli interventi spostati a mano restano dove sono)");
        Div navigazione = new Div(prec, oggi, succ, periodo, ripianifica);
        navigazione.addClassName("boff-navigazione-settimana");

        Div intestazione = new Div(new H2("Calendario"), navigazione);
        intestazione.addClassName("boff-intestazione");
        Div legenda = new Div(voceLegenda("vendita", "Vendita (banco)"), voceLegenda("officina", "Officina"),
                voceLegenda("lavoro", "Intervento pianificato"), voceLegenda("assenza", "Assente / chiuso"));
        Span suggerimento = new Span("Trascina un intervento su un'altra persona o giorno per spostarlo (o toccalo).");
        suggerimento.addClassName("boff-testo-secondario");
        legenda.add(suggerimento);
        avvisi.addClassName("boff-colonna");
        legenda.addClassName("boff-legenda");

        Div scorrimento = new Div(griglia);
        scorrimento.addClassName("boff-scorrimento-orizzontale");
        add(intestazione, avvisi, legenda, scorrimento);
        mostraEsito(pianificazione.ripianifica());
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
        assegnazioniPerCella = pianificazione.tra(lunedi, domenica).stream()
                .collect(Collectors.groupingBy(a -> a.getUtente().getId() + "|" + a.getData()));
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
        DropTarget<Div> destinazione = DropTarget.create(cella);
        destinazione.addDropListener(e -> e.getDragData().ifPresent(id -> sposta((Long) id, p.getId(), g)));
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
        d.assenzeParziali(p.getId(), g).forEach(a -> {
            var o = a.orario().orElseThrow();
            Span b = new Span(o.inizio() + "–" + o.fine() + " " + a.getMotivo().getEtichetta());
            b.addClassNames("boff-blocco", "assenza");
            cella.add(b);
        });
        List<Assegnazione> elenco = assegnazioniPerCella.getOrDefault(p.getId() + "|" + g, List.of());
        elenco.forEach(a -> cella.add(chip(a)));
        long officina = d.minuti(p.getId(), g, TipoFascia.MANUTENZIONE);
        if (officina > 0) {
            long occupati = elenco.stream().mapToLong(a -> a.intervallo().minuti()).sum();
            Span cap = new Span("Officina: " + Durate.formatta(Math.max(0, officina - occupati)) + " liberi su "
                    + Durate.formatta(officina));
            cap.addClassName("boff-capacita");
            if (occupati >= officina) cap.addClassName("pieno");
            cella.add(cap);
        }
        return cella;
    }

    private Component chip(Assegnazione a) {
        var lavoro = a.getRiga().getLavoro();
        Div chip = new Div();
        chip.addClassNames("boff-blocco", "lavoro");
        if (lavoro.isUrgente()) chip.addClassName("urgente");
        Span orario = new Span(a.getOraInizio() + "–" + a.getOraFine());
        orario.addClassName("orario");
        if (a.isBloccata()) {
            var lucchetto = VaadinIcon.LOCK.create();
            lucchetto.addClassName("lucchetto");
            chip.add(lucchetto);
        }
        chip.add(orario, new Span(" " + lavoro.getBicicletta().getNome()));
        chip.setTitle(a.getRiga().getDescrizione() + " — " + lavoro.getBicicletta().getProprietario().getNomeCompleto()
                + (a.isBloccata() ? " (fissato a mano)" : ""));
        DragSource<Div> sorgente = DragSource.create(chip);
        sorgente.setDragData(a.getId());
        chip.addClickListener(e -> dettaglioAssegnazione(a));
        chip.getElement().setAttribute("role", "button");
        chip.getElement().setAttribute("tabindex", "0");
        return chip;
    }

    private void dettaglioAssegnazione(Assegnazione a) {
        var lavoro = a.getRiga().getLavoro();
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(lavoro.getBicicletta().getNome());
        dialog.setWidth("min(480px, 100vw)");
        Paragraph cosa = new Paragraph(a.getRiga().getDescrizione() + " · " + a.getUtente().getNomeCompleto() + ", "
                + Formati.giorno(a.getData()) + " " + a.getOraInizio() + "–" + a.getOraFine());
        Paragraph cliente = new Paragraph("Cliente: " + lavoro.getBicicletta().getProprietario().getNomeCompleto());
        cliente.addClassName("boff-testo-secondario");

        Select<Utente> persona = new Select<>();
        persona.setLabel("Sposta a");
        List<Utente> personale = calendario.disponibilita(lunedi, lunedi).personale();
        persona.setItems(personale);
        persona.setItemLabelGenerator(Utente::getNomeCompleto);
        personale.stream().filter(u -> u.getId().equals(a.getUtente().getId())).findFirst().ifPresent(persona::setValue);
        DatePicker giorno = CampoData.crea("Giorno");
        giorno.setValue(a.getData());
        giorno.setMin(calendario.oggi());
        TimePicker ora = new TimePicker("Alle (facoltativo)");
        ora.setStep(java.time.Duration.ofMinutes(15));
        ora.setLocale(Locale.ITALY);
        ora.setHelperText("Vuoto = primo spazio libero");
        Button sposta = new Button("Sposta", e -> {
            if (persona.getValue() != null && giorno.getValue() != null) {
                sposta(a.getId(), persona.getValue().getId(), giorno.getValue(), ora.getValue());
                dialog.close();
            }
        });
        sposta.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Div spostamento = new Div(persona, giorno, ora, sposta);
        spostamento.addClassName("boff-modulo-riga");

        dialog.add(cosa, cliente, spostamento);
        if (a.isBloccata()) {
            Paragraph nota = new Paragraph("Fissato a mano: la ripianificazione automatica non lo sposta.");
            nota.addClassName("boff-testo-secondario");
            dialog.add(nota);
        }
        Button apri = new Button("Apri lavoro", e -> {
            dialog.close();
            getUI().ifPresent(ui -> ui.navigate(LavoriView.class, LavoriView.parametri(lavoro.getId())));
        });
        Button blocco = a.isBloccata()
                ? new Button("Sblocca", VaadinIcon.UNLOCK.create(), e -> {
                    pianificazione.sblocca(a.getId());
                    dialog.close();
                    mostra();
                })
                : new Button("Fissa qui", VaadinIcon.LOCK.create(), e -> {
                    pianificazione.blocca(a.getId());
                    dialog.close();
                    mostra();
                });
        blocco.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getFooter().add(blocco, apri, new Button("Chiudi", e -> dialog.close()));
        dialog.open();
    }

    private void sposta(Long assegnazioneId, Long utenteId, LocalDate giorno) {
        sposta(assegnazioneId, utenteId, giorno, null);
    }

    private void sposta(Long assegnazioneId, Long utenteId, LocalDate giorno, java.time.LocalTime ora) {
        try {
            pianificazione.sposta(assegnazioneId, utenteId, giorno, ora);
            Avvisi.ok("Intervento spostato e fissato");
        } catch (ErroreDominio ex) {
            Avvisi.errore(ex.getMessage());
        }
        mostra();
    }

    private void mostraEsito(EsitoPianificazione esito) {
        avvisi.removeAll();
        if (!esito.nonPianificabili().isEmpty()) {
            avvisi.add(avviso("Lavori senza spazio in officina nei prossimi 90 giorni: ", esito.nonPianificabili(),
                    "Controlla gli orari: serve almeno una fascia di officina."));
        }
        if (!esito.inRitardo().isEmpty()) {
            avvisi.add(avviso("Lavori che finiscono dopo la data promessa al cliente: ", esito.inRitardo(),
                    "Segnali come urgenti o sposta altri interventi."));
        }
    }

    private Component avviso(String testo, List<Long> idLavori, String consiglio) {
        Div box = new Div(new Span(testo));
        box.addClassName("boff-errore");
        for (Long id : idLavori) {
            var l = lavori.dettaglio(id);
            RouterLink link = new RouterLink(l.getBicicletta().getNome(), LavoriView.class, LavoriView.parametri(id));
            box.add(link, new Span(" "));
        }
        box.add(new Span(" — " + consiglio));
        return box;
    }

    private static Span voceLegenda(String classe, String testo) {
        Span quadrato = new Span();
        quadrato.addClassNames("boff-quadrato", classe);
        Span s = new Span(quadrato, new Span(testo));
        s.addClassName("boff-voce-legenda");
        return s;
    }
}
