package it.boff.bike.ui.views;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import it.boff.bike.bicicletta.Bicicletta;
import it.boff.bike.bicicletta.BiciclettaService;
import it.boff.bike.bicicletta.DatiBicicletta;
import com.vaadin.flow.router.RouterLink;
import it.boff.bike.lavoro.Lavoro;
import it.boff.bike.lavoro.LavoroService;
import it.boff.bike.notifiche.RecuperoPasswordService;
import it.boff.bike.privacy.PrivacyService;
import it.boff.bike.security.Ruoli;
import it.boff.bike.ui.componenti.BadgeStato;
import it.boff.bike.ui.componenti.Formati;
import it.boff.bike.security.UtenteCorrente;
import it.boff.bike.ui.componenti.Avvisi;
import it.boff.bike.ui.componenti.SchedaBici;
import it.boff.bike.utente.DatiAnagrafica;
import it.boff.bike.utente.Utente;
import it.boff.bike.utente.UtenteService;
import jakarta.annotation.security.RolesAllowed;

import java.util.List;

/**
 * Clienti e loro bici, per il negozio. Sul Mac: elenco a sinistra, dettaglio a destra.
 * Scorciatoie: Alt+N nuovo cliente, Alt+F cerca.
 */
@Route("clienti/:id?")
@PageTitle("Clienti | Boff Bike")
@RolesAllowed(Ruoli.STAFF)
public class ClientiView extends Div implements com.vaadin.flow.router.BeforeEnterObserver {

    private final UtenteService utenti;
    private final BiciclettaService biciclette;
    private final LavoroService lavori;
    private final RecuperoPasswordService recupero;
    private final PrivacyService privacy;
    private final boolean titolare;
    private final Grid<Utente> griglia = new Grid<>();
    private final TextField cerca = new TextField();
    private final Div dettaglio = new Div();

    private final it.boff.bike.calendario.CalendarioService calendario;

    public ClientiView(UtenteService utenti, BiciclettaService biciclette, LavoroService lavori,
                       UtenteCorrente utenteCorrente, RecuperoPasswordService recupero, PrivacyService privacy,
                       it.boff.bike.calendario.CalendarioService calendario) {
        this.calendario = calendario;
        this.recupero = recupero;
        this.privacy = privacy;
        this.utenti = utenti;
        this.lavori = lavori;
        this.biciclette = biciclette;
        this.titolare = utenteCorrente.isTitolare();
        addClassName("boff-pagina");

        cerca.setPlaceholder("Cerca per nome, email o telefono");
        cerca.setPrefixComponent(VaadinIcon.SEARCH.create());
        cerca.setClearButtonVisible(true);
        cerca.setValueChangeMode(ValueChangeMode.LAZY);
        cerca.addValueChangeListener(e -> aggiornaElenco());
        cerca.setWidthFull();
        cerca.addFocusShortcut(Key.KEY_F, KeyModifier.ALT);

        Button nuovo = new Button("Nuovo cliente", VaadinIcon.PLUS.create(), e -> nuovoCliente());
        nuovo.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        nuovo.addClickShortcut(Key.KEY_N, KeyModifier.ALT);
        nuovo.setTooltipText("Alt+N");

        griglia.addColumn(Utente::getNomeCompleto).setHeader("Cliente").setAutoWidth(true).setFlexGrow(1);
        griglia.addColumn(Utente::getTelefono).setHeader("Telefono").setAutoWidth(true);
        griglia.addColumn(u -> u.usaApp() ? u.getEmail() : "senza app").setHeader("Email").setAutoWidth(true);
        griglia.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        griglia.setHeight("min(70vh, 640px)");
        griglia.asSingleSelect().addValueChangeListener(e -> mostra(e.getValue()));

        Div intestazione = new Div(new H2("Clienti"), nuovo);
        intestazione.addClassName("boff-intestazione");
        Div elenco = new Div(cerca, griglia);
        elenco.addClassName("boff-colonna");
        dettaglio.addClassName("boff-colonna");
        Div corpo = new Div(elenco, dettaglio);
        corpo.addClassName("boff-master-detail");

        add(intestazione, corpo);
        aggiornaElenco();
        mostra(null);
    }

    @Override
    public void beforeEnter(com.vaadin.flow.router.BeforeEnterEvent event) {
        event.getRouteParameters().getLong("id").ifPresent(id -> {
            Utente c;
            try {
                c = utenti.trova(id);
            } catch (it.boff.bike.common.ErroreDominio e) {
                return;
            }
            if (c.getRuolo() != it.boff.bike.utente.Ruolo.CLIENTE) {
                return; // la pagina Clienti mostra solo clienti
            }
            cerca.setValue(c.getNomeCompleto());
            aggiornaElenco();
            // le entità non ridefiniscono equals: si seleziona l'elemento della griglia con lo stesso id
            griglia.getListDataView().getItems().filter(u -> u.getId().equals(id)).findFirst()
                    .ifPresent(griglia::select);
            mostra(c);
        });
    }

    private void aggiornaElenco() {
        griglia.setItems(utenti.cercaClienti(cerca.getValue()));
    }

    private void mostra(Utente cliente) {
        dettaglio.removeAll();
        if (cliente == null) {
            Div vuoto = new Div(new Paragraph("Seleziona un cliente per vedere i dati e le sue bici."));
            vuoto.addClassNames("boff-card", "boff-vuoto");
            dettaglio.add(vuoto);
            return;
        }
        Utente c = utenti.trova(cliente.getId());
        if (c.isEliminato()) {
            Div eliminato = new Div(new H3("Cliente eliminato"),
                    new Paragraph("I dati personali sono stati cancellati il "
                            + Formati.data(c.getEliminatoIl())
                            + ". Lo storico dei lavori resta in forma anonima."));
            eliminato.addClassNames("boff-card", "boff-vuoto");
            dettaglio.add(eliminato);
            return;
        }

        Button modifica = new Button("Modifica", VaadinIcon.EDIT.create(), e -> modificaCliente(c));
        modifica.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Div titolo = new Div(new H3(c.getNomeCompleto()), modifica);
        titolo.addClassName("boff-intestazione");

        Div contatti = new Div(
                riga(VaadinIcon.PHONE, c.getTelefono() == null ? "–" : null,
                        c.getTelefono() == null ? null : new Anchor("tel:" + c.getTelefono(), c.getTelefono())));
        if (c.usaApp()) {
            contatti.add(riga(VaadinIcon.ENVELOPE, null, new Anchor("mailto:" + c.getEmail(), c.getEmail())));
        } else {
            Span senzaApp = new Span("Senza app: avvisatelo per telefono");
            senzaApp.getElement().getThemeList().add("badge");
            senzaApp.getElement().getThemeList().add("contrast");
            contatti.add(senzaApp);
        }
        if (!c.isAttivo()) {
            Span disattivato = new Span("Account disattivato");
            disattivato.getElement().getThemeList().add("badge error");
            contatti.add(disattivato);
        }
        Div schedaCliente = new Div(titolo, contatti);
        schedaCliente.addClassName("boff-card");

        Button invito = new Button("Invia link per la password", VaadinIcon.ENVELOPE_O.create(), e -> {
            recupero.invitaCliente(c.getId());
            Avvisi.ok("Email inviata a " + c.getEmail());
        });
        invito.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        invito.setTooltipText("Per i clienti registrati al banco: ricevono un link per scegliere la password");
        invito.setVisible(c.usaApp());
        schedaCliente.add(invito);
        if (titolare) {
            Button attiva = new Button(c.isAttivo() ? "Disattiva account" : "Riattiva account", e -> {
                utenti.impostaAttivo(c.getId(), !c.isAttivo());
                Avvisi.ok(c.isAttivo() ? "Account disattivato" : "Account riattivato");
                mostra(c);
            });
            attiva.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            if (c.isAttivo()) attiva.addThemeVariants(ButtonVariant.LUMO_ERROR);
            schedaCliente.add(attiva);
        }
        schedaCliente.add(schedaPrivacy(c));

        Button aggiungiBici = new Button("Aggiungi bici", VaadinIcon.PLUS.create(), e -> nuovaBici(c));
        aggiungiBici.addThemeVariants(ButtonVariant.LUMO_SMALL);
        Div titoloBici = new Div(new H3("Biciclette"), aggiungiBici);
        titoloBici.addClassName("boff-intestazione");

        Div elencoBici = new Div();
        elencoBici.addClassName("boff-colonna");
        List<Bicicletta> bici = biciclette.elencoDi(c.getId());
        if (bici.isEmpty()) {
            elencoBici.add(new Paragraph("Nessuna bici registrata."));
        }
        bici.forEach(b -> {
            // tocco sulla scheda = modifica; sotto, lo storico degli interventi fatti su quella bici
            Button storico = new Button("Storico interventi", VaadinIcon.TIME_BACKWARD.create(),
                    e -> new StoricoBiciDialog(lavori, b, c.getNomeCompleto(), calendario.oggi()).open());
            storico.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            Div voce = new Div(new SchedaBici(b, () -> modificaBici(c, b)), storico);
            voce.addClassName("boff-bici-con-storico");
            elencoBici.add(voce);
        });

        Div elencoLavori = new Div(new H3("Lavori"));
        elencoLavori.addClassName("boff-card");
        List<Lavoro> storico = lavori.lavoriDelCliente(c.getId());
        if (storico.isEmpty()) {
            elencoLavori.add(new Paragraph("Nessun lavoro."));
        }
        storico.stream().limit(15).forEach(l -> {
            RouterLink link = new RouterLink(LavoriView.class, LavoriView.parametri(l.getId()));
            link.add(new Span(l.getBicicletta().getNome() + " · " + Formati.data(l.getAnnunciatoIl()) + " "),
                    new BadgeStato(l.getStato()));
            link.addClassName("boff-voce-elenco");
            elencoLavori.add(link);
        });

        dettaglio.add(schedaCliente, titoloBici, elencoBici, elencoLavori);
    }

    private static Div riga(VaadinIcon icona, String testo, com.vaadin.flow.component.Component contenuto) {
        Div d = new Div(icona.create());
        d.addClassName("boff-riga-icona");
        d.add(contenuto != null ? contenuto : new Span(testo));
        return d;
    }

    /**
     * Diritti privacy esercitati in negozio: copia dei dati e cancellazione, su richiesta del cliente.
     * Indispensabile per chi non usa l'app (gli altri possono farlo anche dal proprio profilo).
     */
    private Div schedaPrivacy(Utente c) {
        Anchor scarica = new Anchor(com.vaadin.flow.server.streams.DownloadHandler.fromInputStream(evento -> {
            byte[] dati = privacy.esporta(c.getId()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return new com.vaadin.flow.server.streams.DownloadResponse(new java.io.ByteArrayInputStream(dati),
                    "dati-cliente-" + c.getId() + ".json", "application/json", dati.length);
        }), "Scarica i dati del cliente");
        scarica.getElement().setAttribute("download", true);
        scarica.addClassName("boff-testo-secondario");

        Button elimina = new Button("Elimina i dati del cliente", VaadinIcon.TRASH.create(), e -> {
            com.vaadin.flow.component.confirmdialog.ConfirmDialog conferma =
                    new com.vaadin.flow.component.confirmdialog.ConfirmDialog(
                            "Eliminare i dati di " + c.getNomeCompleto() + "?",
                            "Da fare solo se il cliente lo ha chiesto. Nome, contatti, bici, note e foto vengono "
                                    + "cancellati e l'account non potrà più essere usato. I lavori passati restano "
                                    + "nello storico in forma anonima. L'operazione non si può annullare.",
                            "Elimina definitivamente", ev -> {
                                try {
                                    privacy.eliminaSuRichiesta(c.getId());
                                    Avvisi.ok("Dati del cliente eliminati");
                                    aggiornaElenco();
                                    mostra(null);
                                } catch (it.boff.bike.common.ErroreDominio ex) {
                                    Avvisi.errore(ex.getMessage());
                                }
                            });
            conferma.setCancelable(true);
            conferma.setCancelText("Annulla");
            conferma.setConfirmButtonTheme("error primary");
            conferma.open();
        });
        elimina.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
        elimina.setTooltipText("Su richiesta del cliente (diritto alla cancellazione)");

        Span titolo = new Span("Privacy");
        titolo.addClassName("boff-testo-secondario");
        Div d = new Div(titolo, scarica, elimina);
        d.addClassName("boff-privacy-cliente");
        return d;
    }

    private void nuovoCliente() {
        new ClienteDialog(new DatiAnagrafica(), true, dati -> {
            Utente nuovo = utenti.creaCliente(dati, true);
            Avvisi.ok("Cliente creato");
            cerca.clear();
            aggiornaElenco();
            griglia.select(nuovo);
        }).open();
    }

    private void modificaCliente(Utente c) {
        new ClienteDialog(DatiAnagrafica.da(c), false, dati -> {
            utenti.aggiornaAnagrafica(c.getId(), dati);
            Avvisi.ok("Dati salvati");
            aggiornaElenco();
            mostra(c);
        }).open();
    }

    private void nuovaBici(Utente c) {
        new BiciclettaDialog(new DatiBicicletta(), true, dati -> {
            biciclette.aggiungi(c.getId(), dati);
            Avvisi.ok("Bici aggiunta");
            mostra(c);
        }, null).open();
    }

    private void modificaBici(Utente c, Bicicletta b) {
        new BiciclettaDialog(DatiBicicletta.da(b), false, dati -> {
            biciclette.modifica(c.getId(), b.getId(), dati);
            Avvisi.ok("Modifiche salvate");
            mostra(c);
        }, () -> {
            biciclette.rimuovi(c.getId(), b.getId());
            Avvisi.ok("Bici rimossa");
            mostra(c);
        }).open();
    }
}
