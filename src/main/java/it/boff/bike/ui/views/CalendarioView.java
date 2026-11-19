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
public class CalendarioView extends Div implements com.vaadin.flow.router.BeforeEnterObserver {

    /** Modi di visualizzazione, da confrontare con chi usa il calendario (scelta salvata nella sessione). */
    public enum Vista {
        COMPATTA("compatta", "Compatta"), DETTAGLIATA("dettagliata", "Dettagliata"), TEMPO("tempo", "Linea del tempo");

        final String parametro;
        final String etichetta;

        Vista(String parametro, String etichetta) {
            this.parametro = parametro;
            this.etichetta = etichetta;
        }

        static java.util.Optional<Vista> da(String parametro) {
            return java.util.Arrays.stream(values()).filter(v -> v.parametro.equalsIgnoreCase(parametro)).findFirst();
        }
    }

    static final String CHIAVE_VISTA = CalendarioView.class.getName() + ".vista";
    static final String CHIAVE_SETTIMANE = CalendarioView.class.getName() + ".settimane";
    /** Linea del tempo: pixel per ora. */
    static final int PX_ORA = 44;

    private static final DateTimeFormatter TITOLO = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALY);
    private static final DateTimeFormatter MESE = DateTimeFormatter.ofPattern("MMM", Locale.ITALY);

    private final CalendarioService calendario;
    private final PianificazioneService pianificazione;
    private final LavoroService lavori;
    private final Div griglia = new Div();
    private final Div avvisi = new Div();
    private Map<String, List<Assegnazione>> assegnazioniPerCella = Map.of();
    private Map<String, List<it.boff.bike.appuntamento.Appuntamento>> appuntamentiPerCella = Map.of();
    private final it.boff.bike.appuntamento.AppuntamentoService appuntamenti;
    private List<Utente> personaleAttivo = List.of();
    private final Span periodo = new Span();
    private LocalDate lunedi;
    private Vista vista = Vista.COMPATTA;
    /** Settimane mostrate una sotto l'altra: con 2 si può trascinare un lavoro alla settimana dopo. */
    private int settimane = 2;
    private final Div sceltaVista = new Div();
    /** Linea del tempo: inizio e fine della giornata più lunga della settimana. */
    private java.time.LocalTime inizioScala = java.time.LocalTime.of(8, 0), fineScala = java.time.LocalTime.of(20, 0);

    public CalendarioView(CalendarioService calendario, PianificazioneService pianificazione, LavoroService lavori,
                          it.boff.bike.appuntamento.AppuntamentoService appuntamenti) {
        this.appuntamenti = appuntamenti;
        this.calendario = calendario;
        this.pianificazione = pianificazione;
        this.lavori = lavori;
        this.lunedi = calendario.oggi().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        addClassName("boff-pagina");
        Object salvata = com.vaadin.flow.server.VaadinSession.getCurrent() == null ? null
                : com.vaadin.flow.server.VaadinSession.getCurrent().getAttribute(CHIAVE_VISTA);
        if (salvata instanceof Vista v) vista = v;
        Object n = com.vaadin.flow.server.VaadinSession.getCurrent() == null ? null
                : com.vaadin.flow.server.VaadinSession.getCurrent().getAttribute(CHIAVE_SETTIMANE);
        if (n instanceof Integer i) settimane = i;

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
        Div legenda = new Div(voceLegenda("vendita", "Vendita e consulenze"), voceLegenda("officina", "Officina"),
                voceLegenda("lavoro", "Lavoro in officina"), voceLegenda("appuntamento", "Appuntamento"),
                voceLegenda("assenza", "Assente / chiuso"));
        Span suggerimento = new Span("Tocca una bici per i dettagli; trascinala su un'altra persona o giorno per spostarla.");
        suggerimento.addClassName("boff-testo-secondario");
        legenda.add(suggerimento);
        avvisi.addClassName("boff-colonna");
        legenda.addClassName("boff-legenda");

        Div scorrimento = new Div(griglia);
        scorrimento.addClassName("boff-scorrimento-orizzontale");
        sceltaVista.addClassName("boff-scelta-vista");
        sceltaVista.getElement().setAttribute("role", "group");
        sceltaVista.getElement().setAttribute("aria-label", "Visualizzazione");
        aggiornaSceltaVista();
        add(intestazione, sceltaVista, avvisi, legenda, scorrimento);
        // aprire il calendario non riorganizza nulla: aggiorna solo ciò che manca (o tutto, se automatico)
        mostraEsito(pianificazione.aggiorna());
        mostra();
    }

    @Override
    public void beforeEnter(com.vaadin.flow.router.BeforeEnterEvent event) {
        var parametri = event.getLocation().getQueryParameters();
        parametri.getSingleParameter("settimane").filter(x -> x.equals("1") || x.equals("2"))
                .map(Integer::valueOf).filter(x -> x != settimane).ifPresent(this::cambiaSettimane);
        parametri.getSingleParameter("vista").flatMap(Vista::da).ifPresent(v -> {
            if (v != vista) cambiaVista(v);
        });
    }

    private void cambiaVista(Vista nuova) {
        vista = nuova;
        if (com.vaadin.flow.server.VaadinSession.getCurrent() != null) {
            com.vaadin.flow.server.VaadinSession.getCurrent().setAttribute(CHIAVE_VISTA, nuova);
        }
        aggiornaSceltaVista();
        mostra();
    }

    private void cambiaSettimane(int n) {
        settimane = n;
        if (com.vaadin.flow.server.VaadinSession.getCurrent() != null) {
            com.vaadin.flow.server.VaadinSession.getCurrent().setAttribute(CHIAVE_SETTIMANE, n);
        }
        aggiornaSceltaVista();
        mostra();
    }

    private void aggiornaSceltaVista() {
        sceltaVista.removeAll();
        Span etichetta = new Span("Visualizzazione:");
        etichetta.addClassName("boff-testo-secondario");
        sceltaVista.add(etichetta);
        for (Vista v : Vista.values()) {
            Button b = new Button(v.etichetta, e -> cambiaVista(v));
            b.addThemeVariants(ButtonVariant.LUMO_SMALL, v == vista ? ButtonVariant.LUMO_PRIMARY : ButtonVariant.LUMO_CONTRAST);
            if (v != vista) b.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            b.getElement().setAttribute("aria-pressed", String.valueOf(v == vista));
            sceltaVista.add(b);
        }
        Span sep = new Span("Settimane:");
        sep.addClassNames("boff-testo-secondario", "boff-scelta-separata");
        sceltaVista.add(sep);
        for (int n : new int[] {1, 2}) {
            Button b = new Button(String.valueOf(n), e -> cambiaSettimane(n));
            b.addThemeVariants(ButtonVariant.LUMO_SMALL, n == settimane ? ButtonVariant.LUMO_PRIMARY : ButtonVariant.LUMO_CONTRAST);
            if (n != settimane) b.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            b.getElement().setAttribute("aria-pressed", String.valueOf(n == settimane));
            b.setAriaLabel(n == 1 ? "Una settimana" : "Due settimane");
            sceltaVista.add(b);
        }
    }

    private void sposta(int settimane) {
        lunedi = lunedi.plusWeeks(settimane);
        mostra();
    }

    private void mostra() {
        LocalDate domenica = lunedi.plusDays(7L * settimane - 1);
        periodo.setText(lunedi.getDayOfMonth() + (lunedi.getMonth() == domenica.getMonth() ? "" : " " + MESE.format(lunedi))
                + " – " + TITOLO.format(domenica));
        Disponibilita d = calendario.disponibilita(lunedi, domenica);
        assegnazioniPerCella = pianificazione.tra(lunedi, domenica).stream()
                .collect(Collectors.groupingBy(a -> a.getUtente().getId() + "|" + a.getData()));
        appuntamentiPerCella = appuntamenti.tra(lunedi, domenica).stream()
                .collect(Collectors.groupingBy(a -> a.getUtente().getId() + "|" + a.getData()));
        personaleAttivo = d.personale();
        // la domenica si mostra solo se qualcuno lavora in una delle settimane
        boolean domeniche = Stream.iterate(lunedi.plusDays(6), g -> g.plusWeeks(1)).limit(settimane)
                .anyMatch(g -> d.personale().stream().anyMatch(p -> !d.fasce(p.getId(), g).isEmpty()));
        List<LocalDate> giorni = Stream.iterate(lunedi, g -> g.plusDays(1)).limit(7L * settimane)
                .filter(g -> g.getDayOfWeek() != DayOfWeek.SUNDAY || domeniche)
                .toList();
        int colonne = domeniche ? 7 : 6;

        // scala oraria comune a tutta la settimana (linea del tempo)
        inizioScala = java.time.LocalTime.of(8, 0);
        fineScala = java.time.LocalTime.of(19, 0);
        for (Utente p : d.personale()) {
            for (LocalDate g : giorni) {
                for (FasciaOraria f : d.fasce(p.getId(), g)) {
                    if (f.getOraInizio().isBefore(inizioScala)) inizioScala = f.getOraInizio().withMinute(0);
                    if (f.getOraFine().isAfter(fineScala)) fineScala = f.getOraFine().getMinute() == 0
                            ? f.getOraFine() : f.getOraFine().withMinute(0).plusHours(1);
                }
            }
        }

        griglia.removeAll();
        griglia.addClassName("boff-griglia-settimana");
        griglia.getElement().setAttribute("data-vista", vista.parametro);
        griglia.getStyle().set("grid-template-columns", "minmax(110px, 0.6fr) repeat(" + colonne + ", minmax(140px, 1fr))");

        LocalDate oggi = calendario.oggi();
        for (int s = 0; s < settimane; s++) {
            LocalDate inizio = lunedi.plusWeeks(s);
            List<LocalDate> settimana = giorni.stream()
                    .filter(g -> !g.isBefore(inizio) && g.isBefore(inizio.plusWeeks(1))).toList();
            Div etichetta = new Div(new Span(s == 0 ? "Questa settimana" : "Settimana dopo"));
            etichetta.addClassName("boff-etichetta-settimana");
            if (s > 0) etichetta.addClassName("seconda");
            if (settimane == 1) etichetta.setText("");
            griglia.add(etichetta);
            for (LocalDate g : settimana) {
                Span nome = new Span(Formati.giorno(g));
                Div testa = new Div(nome);
                testa.addClassName("boff-testa-giorno");
                if (s > 0) testa.addClassName("seconda");
                if (g.equals(oggi)) testa.addClassName("oggi");
                if (g.isBefore(oggi)) testa.addClassName("passato");
                d.chiusura(g).ifPresent(motivo -> {
                    Span c = new Span("Chiuso · " + motivo);
                    c.addClassName("boff-chiuso");
                    testa.add(c);
                });
                griglia.add(testa);
            }
            if (d.personale().isEmpty() && s == 0) {
                Div vuoto = new Div(new Span("Nessun orario impostato: il titolare può farlo da Impostazioni → Orari."));
                vuoto.getStyle().set("grid-column", "1 / -1");
                vuoto.addClassName("boff-testo-secondario");
                griglia.add(vuoto);
            }
            for (Utente p : d.personale()) {
                Span nomeTesto = new Span(p.getNomeCompleto());
                nomeTesto.addClassName("boff-nome-testo");
                Div nome = new Div(nomeTesto);
                nome.addClassName("boff-nome-persona");
                if (vista == Vista.TEMPO) nome.add(righello());
                griglia.add(nome);
                for (LocalDate g : settimana) {
                    griglia.add(cella(d, p, g));
                }
            }
        }
    }

    /** Contenuto di una cella persona/giorno. */
    protected Component cella(Disponibilita d, Utente p, LocalDate g) {
        Div cella = new Div();
        cella.addClassName("boff-cella");
        DropTarget<Div> destinazione = DropTarget.create(cella);
        destinazione.addDropListener(e -> e.getDragData().ifPresent(dati -> spostaGruppo(idDi(dati), p.getId(), g, null)));
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
        List<Assegnazione> elencoCella = assegnazioniPerCella.getOrDefault(p.getId() + "|" + g, List.of());
        if (vista == Vista.TEMPO) {
            return lineaDelTempo(cella, d, p, g, fasce, elencoCella);
        }
        if (vista == Vista.DETTAGLIATA) {
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
            elencoCella.stream().sorted(java.util.Comparator.comparing(Assegnazione::getOraInizio))
                    .forEach(a -> cella.add(chip(a)));
            appuntamentiDi(p, g).forEach(a -> cella.add(slotAppuntamento(a)));
            capacita(cella, d, p, g, elencoCella, true);
            return cella;
        }
        // compatta: orari piccoli e leggeri, il protagonista è la bici
        Div orari = new Div();
        orari.addClassName("boff-orari-giorno");
        for (FasciaOraria f : fasce) {
            Span b = new Span(breve(f.getOraInizio()) + "–" + breve(f.getOraFine()));
            b.addClassNames("boff-orario-mini", f.getTipo() == TipoFascia.VENDITA ? "vendita" : "officina");
            b.setTitle(f.getTipo().getEtichetta() + " " + f.getOraInizio() + "–" + f.getOraFine());
            orari.add(b);
        }
        d.assenzeParziali(p.getId(), g).forEach(a -> {
            var o = a.orario().orElseThrow();
            Span b = new Span(breve(o.inizio()) + "–" + breve(o.fine()));
            b.addClassNames("boff-orario-mini", "assenza");
            b.setTitle(a.getMotivo().getEtichetta() + " " + o.inizio() + "–" + o.fine());
            orari.add(b);
        });
        cella.add(orari);

        record Blocco(java.time.LocalTime inizio, Component c) { }
        List<Blocco> blocchi = new java.util.ArrayList<>();
        raggruppa(elencoCella).forEach(gruppo -> blocchi.add(new Blocco(gruppo.get(0).getOraInizio(), slot(gruppo))));
        appuntamentiDi(p, g).forEach(a -> blocchi.add(new Blocco(a.getOraInizio(), slotAppuntamento(a))));
        blocchi.stream().sorted(java.util.Comparator.comparing(Blocco::inizio)).forEach(b -> cella.add(b.c()));
        capacita(cella, d, p, g, elencoCella, false);
        return cella;
    }

    /** Ore di officina ancora libere: estesa (vista dettagliata) o breve. */
    private void capacita(Div cella, Disponibilita d, Utente p, LocalDate g, List<Assegnazione> elenco, boolean estesa) {
        long officina = d.minuti(p.getId(), g, TipoFascia.MANUTENZIONE);
        if (officina <= 0) return;
        long occupati = elenco.stream().mapToLong(a -> a.intervallo().minuti()).sum();
        String completo = "Officina: " + Durate.formatta(Math.max(0, officina - occupati)) + " liberi su "
                + Durate.formatta(officina);
        Span cap = new Span(estesa ? completo
                : occupati >= officina ? "Officina piena" : "Libero " + Durate.formatta(officina - occupati));
        cap.addClassName("boff-capacita");
        cap.setTitle(completo);
        if (occupati >= officina) cap.addClassName("pieno");
        cella.add(cap);
    }

    private int px(java.time.LocalTime t) {
        return (int) Math.round(java.time.Duration.between(inizioScala, t).toMinutes() * PX_ORA / 60.0);
    }

    /** Ore della scala, accanto al nome della persona. */
    private Component righello() {
        Div r = new Div();
        r.addClassName("boff-righello");
        r.getStyle().set("height", px(fineScala) + "px");
        for (java.time.LocalTime t = inizioScala; !t.isAfter(fineScala); t = t.plusHours(1)) {
            Span ora = new Span(t.getHour() + ":00");
            ora.getStyle().set("top", px(t) + "px");
            r.add(ora);
            if (t.getHour() == 23) break;
        }
        return r;
    }

    /** Linea del tempo: fasce come sfondo, bici come blocchi alti quanto la loro durata. */
    private Component lineaDelTempo(Div cella, Disponibilita d, Utente p, LocalDate g, List<FasciaOraria> fasce,
                                    List<Assegnazione> elenco) {
        cella.addClassName("tempo");
        Div tela = new Div();
        tela.addClassName("boff-tela");
        tela.getStyle().set("height", px(fineScala) + "px");
        tela.getStyle().set("--boff-px-ora", PX_ORA + "px");
        for (FasciaOraria f : fasce) {
            Div banda = new Div();
            banda.addClassNames("boff-banda", f.getTipo() == TipoFascia.VENDITA ? "vendita" : "officina");
            banda.getStyle().set("top", px(f.getOraInizio()) + "px").set("height", (px(f.getOraFine()) - px(f.getOraInizio())) + "px");
            banda.setTitle(f.getTipo().getEtichetta() + " " + f.getOraInizio() + "–" + f.getOraFine());
            if (f.getTipo() == TipoFascia.VENDITA) banda.add(new Span("Vendita"));
            tela.add(banda);
        }
        d.assenzeParziali(p.getId(), g).forEach(a -> {
            var o = a.orario().orElseThrow();
            Div banda = new Div(new Span(a.getMotivo().getEtichetta()));
            banda.addClassNames("boff-banda", "assenza");
            banda.getStyle().set("top", px(o.inizio()) + "px").set("height", (px(o.fine()) - px(o.inizio())) + "px");
            tela.add(banda);
        });
        for (List<Assegnazione> gruppo : raggruppa(elenco)) {
            Component s = slot(gruppo);
            var inizio = gruppo.get(0).getOraInizio();
            var fine = gruppo.get(gruppo.size() - 1).getOraFine();
            s.getElement().getClassList().add("posizionato");
            s.getElement().getStyle().set("top", px(inizio) + "px")
                    .set("height", Math.max(18, px(fine) - px(inizio) - 2) + "px");
            tela.add(s);
        }
        for (var a : appuntamentiDi(p, g)) {
            Component s = slotAppuntamento(a);
            s.getElement().getClassList().add("posizionato");
            s.getElement().getStyle().set("top", px(a.getOraInizio()) + "px")
                    .set("height", Math.max(18, px(a.getOraFine()) - px(a.getOraInizio()) - 2) + "px");
            tela.add(s);
        }
        // riga in alto di altezza fissa (allineata al nome della persona): ore libere
        Div testa = new Div();
        testa.addClassName("boff-tela-testa");
        capacita(testa, d, p, g, elenco, false);
        cella.add(testa, tela);
        return cella;
    }

    private List<it.boff.bike.appuntamento.Appuntamento> appuntamentiDi(Utente p, LocalDate g) {
        return appuntamentiPerCella.getOrDefault(p.getId() + "|" + g, List.of());
    }

    /** Un appuntamento di consulenza: cliente e tipo; toccandolo si conferma, sposta o annulla. */
    private Component slotAppuntamento(it.boff.bike.appuntamento.Appuntamento a) {
        Div slot = new Div();
        slot.addClassNames("boff-slot", "appuntamento");
        boolean richiesto = a.getStato() == it.boff.bike.appuntamento.StatoAppuntamento.RICHIESTO;
        if (richiesto) slot.addClassName("richiesto");
        Span chi = new Span(a.getCliente().getNomeCompleto());
        chi.addClassName("boff-slot-bici");
        Span quando = new Span(breve(a.getOraInizio()) + "–" + breve(a.getOraFine()));
        quando.addClassName("boff-slot-orario");
        Span cosa = new Span(richiesto ? "da confermare" : a.getTipo().getNome());
        cosa.addClassName("boff-slot-etichetta");
        Div riga = new Div(quando);
        riga.addClassName("boff-slot-riga");
        Div etichetta = new Div(cosa);
        etichetta.addClassName("boff-slot-riga");
        slot.add(chi, riga, etichetta);
        slot.setTitle(a.getTipo().getNome() + " · " + a.getCliente().getNomeCompleto());
        slot.getElement().setAttribute("role", "button");
        slot.getElement().setAttribute("tabindex", "0");
        slot.addClickListener(e -> new AppuntamentoDialog(appuntamenti, personaleAttivo, calendario.oggi(), a.getId(),
                this::mostra).open());
        return slot;
    }

    /** Vista dettagliata: un blocco per intervento, con orario e bici. */
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
        sorgente.setDragData(List.of(a.getId()));
        chip.addClickListener(e -> dettaglio(List.of(a)));
        chip.getElement().setAttribute("role", "button");
        chip.getElement().setAttribute("tabindex", "0");
        return chip;
    }

    /** 9:00 → "9", 12:30 → "12:30": gli orari della cella restano brevi. */
    static String breve(java.time.LocalTime t) {
        return t.getMinute() == 0 ? String.valueOf(t.getHour()) : t.getHour() + ":" + String.format("%02d", t.getMinute());
    }

    /** Interventi consecutivi dello stesso lavoro nella stessa cella = un solo blocco. */
    static List<List<Assegnazione>> raggruppa(List<Assegnazione> elenco) {
        List<Assegnazione> ordinati = elenco.stream()
                .sorted(java.util.Comparator.comparing(Assegnazione::getOraInizio)).toList();
        List<List<Assegnazione>> gruppi = new java.util.ArrayList<>();
        for (Assegnazione a : ordinati) {
            List<Assegnazione> ultimo = gruppi.isEmpty() ? null : gruppi.get(gruppi.size() - 1);
            Assegnazione prec = ultimo == null ? null : ultimo.get(ultimo.size() - 1);
            if (prec != null && prec.getRiga().getLavoro().getId().equals(a.getRiga().getLavoro().getId())
                    && !a.getOraInizio().isAfter(prec.getOraFine())) {
                ultimo.add(a);
            } else {
                gruppi.add(new java.util.ArrayList<>(List.of(a)));
            }
        }
        return gruppi;
    }

    @SuppressWarnings("unchecked")
    private static List<Long> idDi(Object dati) {
        return dati instanceof List<?> l ? (List<Long>) l : List.of((Long) dati);
    }

    /** Un blocco nel calendario: solo la bici e l'orario; il resto si vede toccandolo. */
    private Component slot(List<Assegnazione> gruppo) {
        Assegnazione primo = gruppo.get(0), ultimo = gruppo.get(gruppo.size() - 1);
        var lavoro = primo.getRiga().getLavoro();
        Div slot = new Div();
        slot.addClassNames("boff-slot");
        if (lavoro.isUrgente()) slot.addClassName("urgente");

        Span bici = new Span(lavoro.getBicicletta().getNome());
        bici.addClassName("boff-slot-bici");
        Span quando = new Span(breve(primo.getOraInizio()) + "–" + breve(ultimo.getOraFine()));
        quando.addClassName("boff-slot-orario");
        Div riga = new Div(quando);
        riga.addClassName("boff-slot-riga");
        if (gruppo.stream().anyMatch(Assegnazione::isBloccata)) {
            var lucchetto = VaadinIcon.LOCK.create();
            lucchetto.addClassName("lucchetto");
            riga.add(lucchetto);
        }
        if (lavoro.isUrgente()) {
            Span u = new Span("urgente");
            u.addClassName("boff-slot-etichetta");
            riga.add(u);
        }
        slot.add(bici, riga);
        slot.setTitle(lavoro.getBicicletta().getNome() + " · " + lavoro.getBicicletta().getProprietario().getNomeCompleto());

        DragSource<Div> sorgente = DragSource.create(slot);
        sorgente.setDragData(gruppo.stream().map(Assegnazione::getId).toList());
        slot.addClickListener(e -> dettaglio(gruppo));
        slot.getElement().setAttribute("role", "button");
        slot.getElement().setAttribute("tabindex", "0");
        slot.getElement().setAttribute("aria-label", lavoro.getBicicletta().getNome() + ", "
                + primo.getOraInizio() + "–" + ultimo.getOraFine() + ": apri i dettagli");
        return slot;
    }

    /** Dettagli di un blocco: cliente, interventi con orari, note, e comandi per spostarlo o fissarlo. */
    private void dettaglio(List<Assegnazione> gruppo) {
        Assegnazione primo = gruppo.get(0), ultimo = gruppo.get(gruppo.size() - 1);
        var lavoro = primo.getRiga().getLavoro();
        var proprietario = lavoro.getBicicletta().getProprietario();
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(lavoro.getBicicletta().getNome());
        dialog.setWidth("min(520px, 100vw)");

        Paragraph quando = new Paragraph(primo.getUtente().getNomeCompleto() + " · " + Formati.giorno(primo.getData())
                + ", " + primo.getOraInizio() + "–" + ultimo.getOraFine());
        quando.addClassName("boff-dettaglio-quando");
        Div cliente = new Div(new Span("Cliente: " + proprietario.getNomeCompleto()));
        cliente.addClassName("boff-testo-secondario");
        if (proprietario.getTelefono() != null) {
            cliente.add(new Span(" · "), new com.vaadin.flow.component.html.Anchor("tel:" + proprietario.getTelefono(),
                    proprietario.getTelefono()));
        }
        if (!proprietario.usaApp()) {
            Span senzaApp = new Span("Senza app");
            senzaApp.getElement().getThemeList().add("badge");
            senzaApp.getElement().getThemeList().add("contrast");
            cliente.add(new Span(" "), senzaApp);
        }
        dialog.add(quando, cliente);

        com.vaadin.flow.component.html.UnorderedList interventi = new com.vaadin.flow.component.html.UnorderedList();
        interventi.addClassName("boff-dettaglio-interventi");
        for (Assegnazione a : gruppo) {
            Span ora = new Span(breve(a.getOraInizio()) + "–" + breve(a.getOraFine()));
            ora.addClassName("boff-slot-orario");
            interventi.add(new com.vaadin.flow.component.html.ListItem(ora, new Span(" " + a.getRiga().getDescrizione())));
        }
        dialog.add(interventi);

        if (lavoro.isUrgente() || lavoro.getDataPromessa() != null) {
            Paragraph p = new Paragraph((lavoro.isUrgente() ? "Urgente. " : "")
                    + (lavoro.getDataPromessa() == null ? "" : "Promessa al cliente per " + Formati.giorno(lavoro.getDataPromessa()) + "."));
            p.addClassName("boff-urgente");
            dialog.add(p);
        }
        if (lavoro.getNoteCliente() != null && !lavoro.getNoteCliente().isBlank()) {
            dialog.add(new Paragraph("Note del cliente: " + lavoro.getNoteCliente()));
        }
        if (lavoro.getNoteInterne() != null && !lavoro.getNoteInterne().isBlank()) {
            Paragraph n = new Paragraph("Note interne: " + lavoro.getNoteInterne());
            n.addClassName("boff-testo-secondario");
            dialog.add(n);
        }

        Select<Utente> persona = new Select<>();
        persona.setLabel("Sposta a");
        List<Utente> personale = calendario.disponibilita(lunedi, lunedi).personale();
        persona.setItems(personale);
        persona.setItemLabelGenerator(Utente::getNomeCompleto);
        personale.stream().filter(u -> u.getId().equals(primo.getUtente().getId())).findFirst().ifPresent(persona::setValue);
        DatePicker giorno = CampoData.crea("Giorno");
        giorno.setValue(primo.getData());
        giorno.setMin(calendario.oggi());
        TimePicker ora = new TimePicker("Alle (facoltativo)");
        ora.setStep(java.time.Duration.ofMinutes(15));
        ora.setLocale(Locale.ITALY);
        ora.setHelperText("Vuoto = primo spazio libero");
        List<Long> ids = gruppo.stream().map(Assegnazione::getId).toList();
        Button sposta = new Button("Sposta", e -> {
            if (persona.getValue() != null && giorno.getValue() != null) {
                dialog.close();
                spostaGruppo(ids, persona.getValue().getId(), giorno.getValue(), ora.getValue());
            }
        });
        sposta.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Div spostamento = new Div(persona, giorno, ora, sposta);
        spostamento.addClassName("boff-modulo-riga");
        com.vaadin.flow.component.details.Details sezioneSposta =
                new com.vaadin.flow.component.details.Details("Sposta", spostamento);
        dialog.add(sezioneSposta);

        boolean bloccato = gruppo.stream().allMatch(Assegnazione::isBloccata);
        if (bloccato) {
            Paragraph nota = new Paragraph("Fissato a mano: la ripianificazione automatica non lo sposta.");
            nota.addClassName("boff-testo-secondario");
            dialog.add(nota);
        }
        Button apri = new Button("Apri lavoro", e -> {
            dialog.close();
            getUI().ifPresent(ui -> ui.navigate(LavoriView.class, LavoriView.parametri(lavoro.getId())));
        });
        apri.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button blocco = bloccato
                ? new Button("Sblocca", VaadinIcon.UNLOCK.create(), e -> {
                    ids.forEach(pianificazione::sblocca);
                    dialog.close();
                    mostra();
                })
                : new Button("Fissa qui", VaadinIcon.LOCK.create(), e -> {
                    ids.forEach(pianificazione::blocca);
                    dialog.close();
                    mostra();
                });
        blocco.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getFooter().add(blocco, new Button("Chiudi", e -> dialog.close()), apri);
        dialog.open();
    }

    /**
     * Sposta tutti gli interventi di un blocco, nell'ordine: con un orario li mette uno dopo l'altro
     * da quell'ora; senza, ciascuno nel primo spazio libero (quindi di seguito).
     */
    private void spostaGruppo(List<Long> ids, Long utenteId, LocalDate giorno, java.time.LocalTime ora) {
        try {
            pianificazione.spostaTutti(ids, utenteId, giorno, ora);
            Avvisi.ok(ids.size() == 1 ? "Intervento spostato e fissato" : "Interventi spostati e fissati");
        } catch (ErroreDominio ex) {
            Avvisi.errore(ex.getMessage());
        }
        mostra();
    }

    private void mostraEsito(EsitoPianificazione esito) {
        avvisi.removeAll();
        if (!pianificazione.automatica()) {
            Div modo = new Div(new Span("Ricalcolo automatico spento: ciò che è in calendario resta dov'è e i lavori "
                    + "nuovi vanno nei primi spazi liberi. Premi "), grassetto("Ripianifica"),
                    new Span(" per riorganizzare tutto (gli interventi fissati non si spostano)."));
            modo.addClassName("boff-info");
            avvisi.add(modo);
        }
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

    private static Span grassetto(String testo) {
        Span s = new Span(testo);
        s.getStyle().set("font-weight", "600");
        return s;
    }

    private static Span voceLegenda(String classe, String testo) {
        Span quadrato = new Span();
        quadrato.addClassNames("boff-quadrato", classe);
        Span s = new Span(quadrato, new Span(testo));
        s.addClassName("boff-voce-legenda");
        return s;
    }
}
