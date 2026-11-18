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
import it.boff.bike.lavoro.Lavoro;
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
    private final Runnable dopoModifica;
    private Long lavoroId;

    LavoroPannello(LavoroService service, CatalogoService catalogo, PianificazioneService pianificazione,
                   Runnable dopoModifica) {
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
        Div date = new Div(new Span("Annunciato " + Formati.quando(l.getAnnunciatoIl())));
        if (l.getCheckinIl() != null) date.add(new Span(" · in negozio dal " + Formati.quando(l.getCheckinIl())));
        if (l.getFinePrevista() != null) date.add(new Span(" · pronta prevista " + Formati.giorno(l.getFinePrevista())));
        date.addClassName("boff-testo-secondario");
        Div schedaTesta = new Div(testa, bici, clienteRiga, date, azioni(l));
        schedaTesta.addClassName("boff-card");
        add(schedaTesta);

        // --- interventi ---
        Div interventi = new Div(new H4("Interventi"));
        interventi.addClassName("boff-card");
        for (RigaLavoro r : l.getRighe()) {
            interventi.add(riga(l, r));
        }
        Div totale = new Div(new Span("Da fare: " + Durate.formatta(l.minutiDaFare())),
                new Span("Totale: " + Durate.formatta(l.minutiTotali())));
        totale.addClassName("boff-totale");
        interventi.add(totale);
        if (aperto) {
            interventi.add(aggiungiRiga());
        }
        add(interventi);

        // --- calendario ---
        if (StatoLavoro.IN_OFFICINA.contains(l.getStato())) {
            Div cal = new Div(new H4("In calendario"));
            cal.addClassName("boff-card");
            var pezzi = pianificazione.diLavoro(id);
            if (pezzi.isEmpty()) {
                Paragraph p = new Paragraph("Non ancora in calendario (nessuna ora di officina disponibile?).");
                p.addClassName("boff-testo-secondario");
                cal.add(p);
            }
            pezzi.forEach(a -> {
                Div v = new Div(new Span(Formati.giorno(a.getData()) + " " + a.getOraInizio() + "–" + a.getOraFine()
                        + " · " + a.getUtente().getNome() + " · " + a.getRiga().getDescrizione()));
                if (a.isBloccata()) v.add(VaadinIcon.LOCK.create());
                v.addClassName("boff-riga-icona");
                cal.add(v);
            });
            add(cal);
        }

        // --- dettagli ---
        if (l.getNoteCliente() != null) {
            Div nc = new Div(new H4("Note del cliente"), new Paragraph(l.getNoteCliente()));
            nc.addClassName("boff-card");
            add(nc);
        }
        add(dettagli(l, aperto));
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
        fatto.setEnabled(StatoLavoro.IN_OFFICINA.contains(l.getStato()));
        fatto.addValueChangeListener(e -> {
            if (e.isFromClient()) esegui(() -> service.segnaRiga(r.getId(), e.getValue()),
                    e.getValue() ? "Intervento fatto" : "Intervento da rifare");
        });
        Span descr = new Span(r.getDescrizione());
        descr.addClassName("descr");
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
        Div d = new Div(fatto, descr, durata, rimuovi);
        d.addClassName("boff-riga-lavoro");
        if (r.isCompletata()) d.addClassName("fatto");
        return d;
    }

    private Component aggiungiRiga() {
        ComboBox<TipoIntervento> dalCatalogo = new ComboBox<>();
        dalCatalogo.setPlaceholder("Aggiungi dal catalogo…");
        dalCatalogo.setItems(catalogo.attivi());
        dalCatalogo.setItemLabelGenerator(t -> t.getNome() + " (" + Durate.formatta(t.getDurataStandardMin()) + ")");
        dalCatalogo.setWidthFull();
        dalCatalogo.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                esegui(() -> service.aggiungiDaCatalogo(lavoroId, e.getValue().getId()), "Intervento aggiunto");
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
            esegui(() -> service.aggiungiLibera(lavoroId, libera.getValue(), min.getValue()), "Intervento aggiunto");
        });
        aggiungi.setAriaLabel("Aggiungi intervento libero");
        Div riga = new Div(libera, min, aggiungi);
        riga.addClassName("boff-riga-aggiungi");
        Div box = new Div(dalCatalogo, riga);
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
