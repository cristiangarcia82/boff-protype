package it.boff.bike.ui.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import it.boff.bike.catalogo.CatalogoService;
import it.boff.bike.catalogo.TipoIntervento;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.lavoro.FotoService;
import it.boff.bike.lavoro.Lavoro;
import it.boff.bike.ui.componenti.GalleriaFoto;
import it.boff.bike.lavoro.LavoroService;
import it.boff.bike.lavoro.RigaLavoro;
import it.boff.bike.lavoro.StatoLavoro;
import it.boff.bike.pianificazione.PianificazioneService;
import it.boff.bike.ui.componenti.Avvisi;
import it.boff.bike.ui.componenti.BadgeStato;
import it.boff.bike.ui.componenti.CampoData;
import it.boff.bike.ui.componenti.Durate;
import it.boff.bike.ui.componenti.Formati;
import it.boff.bike.ui.componenti.SchedaBici;
import it.boff.bike.utente.Utente;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/** Dettaglio di un lavoro per lo staff: azioni di stato, interventi, tempi, note. */
class LavoroPannello extends Div {

    private final LavoroService service;
    private final CatalogoService catalogo;
    private final PianificazioneService pianificazione;
    private final FotoService foto;
    private final it.boff.bike.funzionalita.FunzionalitaService funzionalita;
    private final Runnable dopoModifica;
    private Long lavoroId;
    private final java.util.function.Supplier<java.util.List<Utente>> personale;
    private final java.util.function.Supplier<java.time.LocalDate> oggi;

    LavoroPannello(LavoroService service, CatalogoService catalogo, PianificazioneService pianificazione,
                   FotoService foto, it.boff.bike.funzionalita.FunzionalitaService funzionalita,
                   Runnable dopoModifica, java.util.function.Supplier<java.util.List<Utente>> personale,
                   java.util.function.Supplier<java.time.LocalDate> oggi) {
        this.personale = personale;
        this.oggi = oggi;
        this.foto = foto;
        this.funzionalita = funzionalita;
        this.pianificazione = pianificazione;
        this.service = service;
        this.catalogo = catalogo;
        this.dopoModifica = dopoModifica;
        addClassName("boff-colonna");
    }

    void mostra(Long id) {
        this.lavoroId = id;
        removeAll();
        if (id == null) {
            Div vuoto = new Div(new Paragraph("Seleziona un lavoro per vederne il dettaglio."));
            vuoto.addClassNames("boff-card", "boff-vuoto");
            add(vuoto);
            return;
        }
        Lavoro l = service.dettaglio(id);
        Utente cliente = l.getBicicletta().getProprietario();
        boolean aperto = l.getStato().isAperto();

        // --- testata ---
        Div testa = new Div(new H3(l.getBicicletta().getNome()), new BadgeStato(l.getStato()));
        testa.addClassName("boff-intestazione");
        Paragraph bici = new Paragraph(SchedaBici.descrizione(l.getBicicletta()));
        bici.addClassName("boff-testo-secondario");
        Div clienteRiga = new Div(VaadinIcon.USER.create(), new Span(cliente.getNomeCompleto()));
        clienteRiga.addClassName("boff-riga-icona");
        if (cliente.getTelefono() != null) {
            clienteRiga.add(new Anchor("tel:" + cliente.getTelefono(), cliente.getTelefono()));
        }
        if (!cliente.usaApp()) {
            Span senzaApp = new Span("Senza app");
            senzaApp.getElement().getThemeList().add("badge");
            senzaApp.getElement().getThemeList().add("contrast");
            senzaApp.setTitle("Il cliente non usa l'app: non riceve notifiche né email");
            clienteRiga.add(senzaApp);
        }
        Div date = new Div(new Span("Annunciato " + Formati.quando(l.getAnnunciatoIl())));
        if (l.getCheckinIl() != null) date.add(new Span(" · in negozio dal " + Formati.quando(l.getCheckinIl())));
        if (l.getFinePrevista() != null) date.add(new Span(" · pronta prevista " + Formati.giorno(l.getFinePrevista())));
        date.addClassName("boff-testo-secondario");
        com.vaadin.flow.router.RouterLink foglio = new com.vaadin.flow.router.RouterLink("Foglio lavori", FoglioLavoroView.class,
                new com.vaadin.flow.router.RouteParameters("id", String.valueOf(l.getId())));
        foglio.getElement().setAttribute("title", "Date del lavoro, interventi fatti, quando e da chi");
        date.add(new Span(" · "), foglio);
        Div schedaTesta = new Div(testa, bici, clienteRiga, date);
        if (!cliente.usaApp() && l.getStato() == StatoLavoro.PRONTO) {
            Paragraph chiama = new Paragraph("Il cliente non usa l'app: chiamalo per dirgli che la bici è pronta"
                    + (cliente.getTelefono() == null ? "." : " (" + cliente.getTelefono() + ")."));
            chiama.addClassName("boff-avviso");
            schedaTesta.add(chiama);
        }
        schedaTesta.add(azioni(l));
        schedaTesta.addClassName("boff-card");
        add(schedaTesta);

        // --- pianificazione ---
        if (aperto && l.getStato() != StatoLavoro.PRONTO) add(pianificazione(l));


        // --- interventi ---
        Div interventi = new Div(new H4("Interventi"));
        interventi.addClassName("boff-card");
        if (l.getStato() == StatoLavoro.ANNUNCIATO) {
            Paragraph attesa = new Paragraph("La bici non è ancora in negozio: gli interventi si potranno segnare come "
                    + "fatti dopo \"Bici arrivata in negozio\".");
            attesa.addClassName("boff-testo-secondario");
            interventi.add(attesa);
        }
        for (RigaLavoro r : l.getRighe()) {
            interventi.add(riga(l, r));
        }
        Div totale = new Div(new Span("Da fare: " + Durate.formatta(l.minutiDaFare())),
                new Span("Totale: " + Durate.formatta(l.minutiTotali())));
        totale.addClassName("boff-totale");
        interventi.add(totale);
        if (aperto) {
            interventi.add(aggiungiRiga(l));
        }
        add(interventi);

        // --- questa bici in passato: per decidere cosa proporre (es. catena cambiata 14 mesi fa) ---
        add(passato(l));

        // --- dettagli ---
        if (l.getNoteCliente() != null) {
            Div nc = new Div(new H4("Note del cliente"), new Paragraph(l.getNoteCliente()));
            nc.addClassName("boff-card");
            add(nc);
        }
        Div schedaFoto = new Div(new H4("Foto"), new GalleriaFoto(foto, id, true,
                (nome, dati) -> foto.aggiungiDelNegozio(id, nome, dati)));
        schedaFoto.addClassName("boff-card");
        add(schedaFoto);
        add(dettagli(l, aperto));
    }

    /**
     * Quando si fa il lavoro, detto in chiaro: chi, che giorno, a che ora, se l'ha deciso il sistema o è
     * fissato a mano; e i comandi per sceglierlo o restituirlo al calcolo automatico.
     */
    private Component pianificazione(Lavoro l) {
        Div card = new Div(new H4("Quando si fa"));
        card.addClassNames("boff-card", "boff-pianificazione");
        boolean annunciato = l.getStato() == StatoLavoro.ANNUNCIATO;
        java.time.LocalDate oggiAdesso = oggi.get();
        var tutti = pianificazione.diLavoro(l.getId());
        var pezzi = tutti.stream().filter(a -> !a.getData().isBefore(oggiAdesso)).toList();
        Runnable dopo = () -> {
            mostra(lavoroId);
            dopoModifica.run();
        };
        Button scegli = new Button(pezzi.isEmpty() && annunciato ? "Fissa giorno e ora" : "Scegli giorno e ora",
                VaadinIcon.CALENDAR_CLOCK.create(), e -> PianificaDialog.perIlLavoro(pianificazione, l, pezzi,
                        personale.get(), oggiAdesso, dopo).open());
        scegli.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        if (pezzi.isEmpty()) {
            Paragraph p;
            if (annunciato && !tutti.isEmpty()) {
                var giorno = tutti.get(0).getData();
                p = new Paragraph("Era fissato per " + Formati.giorno(giorno) + " ma la bici non è arrivata: scegli un'altra data.");
                p.addClassName("boff-avviso");
            } else if (annunciato) {
                p = new Paragraph("La bici non è ancora in negozio. Puoi già fissare quando fare il lavoro: il cliente "
                        + "riceve il giorno e l'ora in cui portarla e la data in cui sarà pronta. Altrimenti va in "
                        + "calendario da sola quando premi \"Bici arrivata in negozio\".");
                p.addClassName("boff-testo-secondario");
            } else {
                p = new Paragraph(l.minutiDaFare() == 0
                        ? "Tutti gli interventi sono fatti: segna la bici come pronta."
                        : "Non c'è spazio nelle ore di lavoro dei prossimi 90 giorni. Controlla gli orari in Impostazioni → Orari, o scegli tu giorno e ora.");
                p.addClassName(l.minutiDaFare() == 0 ? "boff-testo-secondario" : "boff-avviso");
            }
            card.add(p);
            if (annunciato || l.minutiDaFare() > 0) {
                Div comandi = new Div(scegli);
                comandi.addClassName("boff-azioni");
                card.add(comandi);
            }
            return card;
        }
        if (annunciato && l.getArrivoPrevisto() != null) {
            Paragraph arrivo = new Paragraph("Il cliente porta la bici " + Formati.giorno(l.getArrivoPrevisto().toLocalDate())
                    + " entro le " + l.getArrivoPrevisto().toLocalTime()
                    + (l.getFinePrevista() != null ? " · pronta prevista " + Formati.giorno(l.getFinePrevista()) : ""));
            arrivo.addClassName("boff-info");
            card.add(arrivo);
        }
        boolean fissato = pezzi.stream().allMatch(it.boff.bike.pianificazione.Assegnazione::isBloccata);
        boolean qualcunoFissato = pezzi.stream().anyMatch(it.boff.bike.pianificazione.Assegnazione::isBloccata);
        var primo = pezzi.get(0);
        var ultimo = pezzi.get(pezzi.size() - 1);
        boolean unGiorno = pezzi.stream().allMatch(a -> a.getData().equals(primo.getData()) && a.getUtente().getId().equals(primo.getUtente().getId()));
        Paragraph riepilogo = new Paragraph();
        riepilogo.addClassName("boff-dettaglio-quando");
        if (unGiorno) {
            riepilogo.setText(AppuntamentoDialog.capitale(Formati.giorno(primo.getData())) + ", " + primo.getOraInizio() + "–"
                    + ultimo.getOraFine() + " · " + primo.getUtente().getNome());
            card.add(riepilogo);
        } else {
            riepilogo.setText("In più momenti, finisce " + Formati.giorno(ultimo.getData()) + " alle " + ultimo.getOraFine());
            card.add(riepilogo);
            com.vaadin.flow.component.html.UnorderedList elenco = new com.vaadin.flow.component.html.UnorderedList();
            elenco.addClassName("boff-dettaglio-interventi");
            pezzi.forEach(a -> elenco.add(new com.vaadin.flow.component.html.ListItem(
                    AppuntamentoDialog.capitale(Formati.giorno(a.getData())) + " " + a.getOraInizio() + "–" + a.getOraFine()
                            + " · " + a.getUtente().getNome() + " · " + a.getRiga().getDescrizione())));
            card.add(elenco);
        }
        Span chi = new Span(annunciato ? "Fissato prima dell'arrivo della bici: resta lì finché non lo sposti."
                : fissato ? "Fissato a mano: resta lì anche se il calendario cambia."
                : qualcunoFissato ? "In parte fissato a mano." : "Deciso dal sistema: può spostarsi se il calendario cambia.");
        chi.addClassName("boff-testo-secondario");
        Div stato = new Div(fissato ? VaadinIcon.LOCK.create() : VaadinIcon.MAGIC.create(), chi);
        stato.addClassName("boff-riga-icona");
        card.add(stato);

        Div comandi = new Div();
        comandi.addClassName("boff-azioni");
        comandi.add(scegli);
        if (annunciato) {
            Button togli = new Button("Togli dal calendario", e -> esegui(() -> pianificazione.togliDalCalendario(l.getId()),
                    l.getBicicletta().getProprietario().usaApp() ? "Tolto dal calendario" : "Tolto dal calendario: avvisa il cliente per telefono"));
            togli.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            comandi.add(togli);
        } else if (qualcunoFissato) {
            Button libera = new Button("Lascia decidere al sistema", VaadinIcon.UNLOCK.create(), e -> esegui(() ->
                    pezzi.stream().filter(it.boff.bike.pianificazione.Assegnazione::isBloccata)
                            .forEach(a -> pianificazione.sblocca(a.getId())), "Ora lo pianifica il sistema"));
            libera.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            comandi.add(libera);
        }
        com.vaadin.flow.router.RouterLink vedi = new com.vaadin.flow.router.RouterLink("Vedi nel calendario", CalendarioView.class);
        vedi.setQueryParameters(com.vaadin.flow.router.QueryParameters.of("data", primo.getData().toString()));
        comandi.add(vedi);
        card.add(comandi);
        return card;
    }

    private Component passato(Lavoro l) {
        var ultimi = service.ultimiInterventi(l.getBicicletta().getId(), l.getId()).stream().limit(4).toList();
        Div card = new Div(new H4("Questa bici in passato"));
        card.addClassName("boff-card");
        card.add(StoricoBiciDialog.ultimi(ultimi, oggi.get()));
        Button tutto = new Button("Storico completo", VaadinIcon.TIME_BACKWARD.create(), e -> new StoricoBiciDialog(service,
                l.getBicicletta(), l.getBicicletta().getProprietario().getNomeCompleto(), oggi.get()).open());
        tutto.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        card.add(tutto);
        return card;
    }

    private Component azioni(Lavoro l) {
        Div azioni = new Div();
        azioni.addClassName("boff-azioni");
        switch (l.getStato()) {
            case ANNUNCIATO -> azioni.add(primario("Bici arrivata in negozio", VaadinIcon.SIGN_IN,
                    () -> service.checkIn(lavoroId), "Check-in registrato"));
            case IN_NEGOZIO, PIANIFICATO -> {
                azioni.add(secondario("Inizia lavoro", () -> service.inizia(lavoroId), "Lavoro iniziato"));
                azioni.add(primario("Bici pronta", VaadinIcon.CHECK, () -> service.segnaPronto(lavoroId),
                        "Segnata come pronta: il cliente verrà avvisato"));
            }
            case IN_LAVORAZIONE -> azioni.add(primario("Bici pronta", VaadinIcon.CHECK,
                    () -> service.segnaPronto(lavoroId), "Segnata come pronta: il cliente verrà avvisato"));
            case PRONTO -> {
                azioni.add(primario("Ritirata dal cliente", VaadinIcon.HANDSHAKE, () -> service.ritira(lavoroId),
                        "Ritiro registrato"));
                azioni.add(secondario("Riapri", () -> service.riapri(lavoroId), "Lavoro riaperto"));
            }
            default -> { }
        }
        if (l.getStato().isAperto()) {
            Button annulla = new Button("Annulla lavoro", e -> {
                ConfirmDialog c = new ConfirmDialog("Annullare il lavoro?",
                        "Il lavoro verrà chiuso e tolto dal calendario.", "Annulla lavoro",
                        ev -> esegui(() -> service.annulla(lavoroId), "Lavoro annullato"));
                c.setCancelable(true);
                c.setCancelText("Indietro");
                c.setConfirmButtonTheme("error primary");
                c.open();
            });
            annulla.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            azioni.add(annulla);
        }
        return azioni;
    }

    private Button primario(String testo, VaadinIcon icona, Runnable azione, String ok) {
        Button b = new Button(testo, icona.create(), e -> esegui(azione, ok));
        b.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        return b;
    }

    private Button secondario(String testo, Runnable azione, String ok) {
        return new Button(testo, e -> esegui(azione, ok));
    }

    private void esegui(Runnable azione, String ok) {
        try {
            azione.run();
            Avvisi.ok(ok);
        } catch (ErroreDominio ex) {
            Avvisi.errore(ex.getMessage());
        } catch (ObjectOptimisticLockingFailureException ex) {
            Avvisi.errore("Il lavoro è stato modificato da qualcun altro: ricarico i dati aggiornati.");
        }
        mostra(lavoroId);
        dopoModifica.run();
    }

    private Component riga(Lavoro l, RigaLavoro r) {
        Checkbox fatto = new Checkbox(r.isCompletata());
        fatto.setAriaLabel("Fatto");
        fatto.setEnabled(StatoLavoro.IN_OFFICINA.contains(l.getStato()) && r.isValida());
        // casella spenta: si dice perché (senza bici in negozio gli interventi non si fanno)
        if (l.getStato() == StatoLavoro.ANNUNCIATO) fatto.setTooltipText("Si spunta quando la bici è in negozio");
        else if (!r.isValida()) fatto.setTooltipText("In attesa della risposta del cliente");
        fatto.addValueChangeListener(e -> {
            if (e.isFromClient()) esegui(() -> service.segnaRiga(r.getId(), e.getValue()),
                    e.getValue() ? "Intervento fatto" : "Intervento da rifare");
        });
        Span descr = new Span(r.getDescrizione());
        descr.addClassName("descr");
        Div testo = new Div(descr);
        if (r.getApprovazione() != null) {
            Span badge = new Span(r.getApprovazione().getEtichetta());
            badge.getElement().getThemeList().add("badge");
            badge.getElement().getThemeList().add(switch (r.getApprovazione()) {
                case DA_APPROVARE -> "contrast";
                case APPROVATA -> "success";
                case RIFIUTATA -> "error";
            });
            badge.addClassName("boff-badge-riga");
            testo.add(badge);
            if (r.getNotaApprovazione() != null) {
                Span nota = new Span(r.getNotaApprovazione());
                nota.addClassName("boff-testo-secondario");
                testo.add(new Div(nota));
            }
            if (r.isInAttesaDiApprovazione() && l.getStato().isAperto()) {
                // risposta data a voce o al telefono
                Button si = new Button("Ha detto sì", e -> esegui(() -> service.registraRispostaExtra(r.getId(), true),
                        "Risposta registrata"));
                Button no = new Button("Ha detto no", e -> esegui(() -> service.registraRispostaExtra(r.getId(), false),
                        "Risposta registrata"));
                si.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
                no.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
                si.setTooltipText("Il cliente ha risposto di persona o al telefono");
                testo.add(new Div(si, no));
            }
        }
        IntegerField durata = new IntegerField();
        durata.setValue(r.getDurataMin());
        durata.setSuffixComponent(new Span("min"));
        durata.setStep(5);
        durata.setAriaLabel("Durata in minuti");
        durata.setReadOnly(!l.getStato().isAperto());
        durata.addValueChangeListener(e -> {
            if (e.isFromClient() && e.getValue() != null) {
                esegui(() -> service.modificaDurata(r.getId(), e.getValue()), "Durata aggiornata");
            }
        });
        Button rimuovi = new Button(VaadinIcon.TRASH.create(), e -> esegui(() -> service.rimuoviRiga(r.getId()),
                "Intervento rimosso"));
        rimuovi.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ERROR);
        rimuovi.setAriaLabel("Rimuovi intervento");
        rimuovi.setVisible(l.getStato().isAperto() && l.getRighe().size() > 1);
        Div d = new Div(fatto, testo, durata, rimuovi);
        d.addClassName("boff-riga-lavoro");
        if (r.isCompletata()) d.addClassName("fatto");
        if (r.getApprovazione() == it.boff.bike.lavoro.Approvazione.RIFIUTATA) d.addClassName("rifiutata");
        if (r.isCompletata() && funzionalita.attiva(it.boff.bike.funzionalita.Funzionalita.TEMPO_EFFETTIVO)) {
            IntegerField reale = new IntegerField();
            reale.setPlaceholder("reali");
            reale.setValue(r.getMinutiEffettivi());
            reale.setSuffixComponent(new Span("min"));
            reale.setAriaLabel("Minuti effettivi");
            reale.setTooltipText("Quanto ci hai messo davvero");
            reale.addValueChangeListener(e -> {
                if (e.isFromClient() && e.getValue() != null) {
                    esegui(() -> service.registraTempoEffettivo(r.getId(), e.getValue()), "Tempo effettivo salvato");
                }
            });
            Div effettivo = new Div(new Span("Tempo reale"), reale);
            effettivo.addClassName("boff-tempo-reale");
            return new Div(d, effettivo);
        }
        return d;
    }

    private Component aggiungiRiga(Lavoro l) {
        boolean approvazione = funzionalita.attiva(it.boff.bike.funzionalita.Funzionalita.APPROVAZIONE_EXTRA)
                && StatoLavoro.IN_OFFICINA.contains(l.getStato());
        // un cliente senza app non può rispondere dall'app: di default si aggiunge l'intervento direttamente
        Checkbox chiedi = new Checkbox("Chiedi l'approvazione del cliente",
                approvazione && l.getBicicletta().getProprietario().usaApp());
        chiedi.setVisible(approvazione);
        TextField motivo = new TextField();
        motivo.setPlaceholder("Motivo, visibile al cliente (es. pastiglie consumate)");
        motivo.setMaxLength(300);
        motivo.setWidthFull();
        motivo.setVisible(approvazione);
        motivo.setVisible(chiedi.getValue());
        chiedi.addValueChangeListener(e -> motivo.setVisible(e.getValue()));
        ComboBox<TipoIntervento> dalCatalogo = new ComboBox<>();
        dalCatalogo.setPlaceholder("Aggiungi dal catalogo…");
        dalCatalogo.setItems(catalogo.attivi());
        dalCatalogo.setItemLabelGenerator(t -> t.getNome() + " (" + Durate.formatta(t.getDurataStandardMin()) + ")");
        dalCatalogo.setWidthFull();
        dalCatalogo.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                if (chiedi.getValue()) {
                    esegui(() -> service.proponiExtra(lavoroId, e.getValue().getId(), null, null, motivo.getValue()),
                            "Richiesta inviata al cliente");
                } else {
                    esegui(() -> service.aggiungiDaCatalogo(lavoroId, e.getValue().getId()), "Intervento aggiunto");
                }
            }
        });
        TextField libera = new TextField();
        libera.setPlaceholder("…oppure intervento libero");
        IntegerField min = new IntegerField();
        min.setPlaceholder("min");
        min.setStep(5);
        min.setWidth("6em");
        Button aggiungi = new Button(VaadinIcon.PLUS.create(), e -> {
            if (min.getValue() == null) {
                Avvisi.errore("Indica i minuti previsti");
                return;
            }
            if (chiedi.getValue()) {
                esegui(() -> service.proponiExtra(lavoroId, null, libera.getValue(), min.getValue(), motivo.getValue()),
                        "Richiesta inviata al cliente");
            } else {
                esegui(() -> service.aggiungiLibera(lavoroId, libera.getValue(), min.getValue()), "Intervento aggiunto");
            }
        });
        aggiungi.setAriaLabel("Aggiungi intervento libero");
        Div riga = new Div(libera, min, aggiungi);
        riga.addClassName("boff-riga-aggiungi");
        Div box = new Div(chiedi, motivo, dalCatalogo, riga);
        box.addClassName("boff-colonna");
        box.getStyle().set("margin-top", "var(--lumo-space-m)").set("gap", "var(--lumo-space-s)");
        return box;
    }

    private Component dettagli(Lavoro l, boolean aperto) {
        Checkbox urgente = new Checkbox("Urgente (va pianificato per primo)", l.isUrgente());
        DatePicker promessa = CampoData.crea("Data promessa al cliente");
        promessa.setValue(l.getDataPromessa());
        promessa.setHelperText("Facoltativa: la pianificazione avvisa se non viene rispettata");
        promessa.setClearButtonVisible(true);
        TextArea note = new TextArea("Note interne (non visibili al cliente)");
        note.setValue(l.getNoteInterne() == null ? "" : l.getNoteInterne());
        note.setWidthFull();
        note.setMaxLength(2000);
        Button salva = new Button("Salva", e -> esegui(() -> service.impostaDettagli(lavoroId, urgente.getValue(),
                promessa.getValue(), note.getValue()), "Dettagli salvati"));
        Div d = new Div(new H4("Dettagli"), urgente, promessa, note, salva);
        d.addClassNames("boff-card", "boff-colonna");
        d.getStyle().set("gap", "var(--lumo-space-s)");
        if (!aperto) {
            urgente.setReadOnly(true);
            promessa.setReadOnly(true);
            note.setReadOnly(true);
            salva.setVisible(false);
        }
        return d;
    }
}
