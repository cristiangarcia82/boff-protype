package it.boff.bike.ui;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import it.boff.bike.IntegrationTest;
import it.boff.bike.bicicletta.BiciclettaService;
import it.boff.bike.bicicletta.DatiBicicletta;
import it.boff.bike.bicicletta.Tipologia;
import it.boff.bike.catalogo.CatalogoService;
import it.boff.bike.catalogo.TipoIntervento;
import it.boff.bike.lavoro.Lavoro;
import it.boff.bike.lavoro.LavoroService;
import it.boff.bike.lavoro.StatoLavoro;
import it.boff.bike.ui.componenti.SchedaBici;
import it.boff.bike.ui.views.BachecaView;
import it.boff.bike.ui.views.BiciView;
import it.boff.bike.ui.views.CalendarioView;
import it.boff.bike.ui.views.LavoriView;
import it.boff.bike.ui.views.LeMieBiciView;
import it.boff.bike.ui.views.RegistrazioneView;
import it.boff.bike.utente.UtenteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test delle schermate senza browser (girano in CI): navigazione, permessi e
 * i percorsi principali di cliente e negozio.
 */
@IntegrationTest
@ViewPackages(packages = "it.boff.bike.ui")
class InterfacciaIT extends SpringBrowserlessTest {

    @Autowired UtenteRepository utenti;
    @Autowired BiciclettaService biciclette;
    @Autowired LavoroService lavori;
    @Autowired CatalogoService catalogo;
    @Autowired it.boff.bike.pianificazione.PianificazioneService pianificazione;

    @Test
    @WithMockUser(username = "titolare@demo.it", roles = {"STAFF", "TITOLARE"})
    void bachecaDelTitolare() {
        navigate(BachecaView.class);
        assertThat($(H2.class).first().getText()).isEqualTo("Bacheca");
    }

    @Test
    @WithMockUser(username = "cliente@demo.it", roles = "CLIENTE")
    void ilClienteVedeLeSueBici() {
        navigate(LeMieBiciView.class);
        assertThat($(SchedaBici.class).all()).isNotEmpty();
        assertThat($(Button.class).withText("Aggiungi bici").exists()).isTrue();
    }

    @Test
    @WithMockUser(username = "cliente@demo.it", roles = "CLIENTE")
    void ilClienteNonApreLePagineDelNegozio() {
        navigate(LeMieBiciView.class);
        try {
            navigate(LavoriView.class);
        } catch (RuntimeException atteso) {
            // accesso negato: va bene anche un'eccezione
        }
        assertThat(getCurrentView()).isNotInstanceOf(LavoriView.class);
    }

    @Test
    void registrazioneDalTelefono() {
        navigate(RegistrazioneView.class);
        String email = "ui-" + UUID.randomUUID() + "@test.it";
        test($(TextField.class).withLabel("Nome").single()).setValue("Ugo");
        test($(TextField.class).withLabel("Cognome").single()).setValue("Interfaccia");
        test($(EmailField.class).withLabel("Email").single()).setValue(email);
        test($(TextField.class).withLabel("Telefono").single()).setValue("333 4445556");
        test($(PasswordField.class).withLabel("Password").single()).setValue("password123");
        test($(PasswordField.class).withLabel("Ripeti la password").single()).setValue("password123");
        test($(Checkbox.class).single()).click();
        test($(Button.class).withText("Crea account").single()).click();
        assertThat(utenti.findByEmail(email)).isPresent();
    }

    @Test
    @WithMockUser(username = "cliente@demo.it", roles = "CLIENTE")
    void ilClienteAnnunciaUnLavoro() {
        Long cliente = utenti.findByEmail("cliente@demo.it").orElseThrow().getId();
        Long bici = biciclette.aggiungi(cliente, DatiBicicletta.di("Pinarello", "Dogma " + UUID.randomUUID(),
                Tipologia.STRADA)).getId();
        navigate("le-mie-bici/" + bici, BiciView.class);
        test($(Button.class).withText("Annuncia un lavoro").single()).click();

        @SuppressWarnings("unchecked")
        CheckboxGroup<TipoIntervento> scelte = $(CheckboxGroup.class).single();
        TipoIntervento primo = catalogo.attivi().get(0);
        scelte.setValue(java.util.Set.of(scelte.getListDataView().getItems()
                .filter(t -> t.getId().equals(primo.getId())).findFirst().orElseThrow()));
        test($(TextArea.class).single()).setValue("Rumore dalla ruota posteriore");
        test($(Button.class).withText("Invia").single()).click();

        List<Lavoro> suoi = lavori.storicoBiciDelCliente(cliente, bici);
        assertThat(suoi).singleElement().satisfies(l -> {
            assertThat(l.getStato()).isEqualTo(StatoLavoro.ANNUNCIATO);
            assertThat(l.getNoteCliente()).isEqualTo("Rumore dalla ruota posteriore");
        });
    }

    @Test
    @WithMockUser(username = "collaboratore@demo.it", roles = "STAFF")
    void ilNegozioFaIlCheckInDalPannello() {
        Long cliente = utenti.findByEmail("paolo.ferri@demo.it").orElseThrow().getId();
        Long bici = biciclette.aggiungi(cliente, DatiBicicletta.di("Scott", "Spark " + UUID.randomUUID(),
                Tipologia.MTB)).getId();
        Long id = lavori.annuncia(cliente, bici, List.of(catalogo.attivi().get(0).getId()), null).getId();

        navigate("lavori/" + id, LavoriView.class);
        test($(Button.class).withText("Bici arrivata in negozio").single()).click();
        assertThat(lavori.dettaglio(id).getStato()).isIn(StatoLavoro.IN_NEGOZIO, StatoLavoro.PIANIFICATO);
    }

    @Test
    @WithMockUser(username = "titolare@demo.it", roles = {"STAFF", "TITOLARE"})
    void ilCalendarioMostraLePersone() {
        navigate(CalendarioView.class);
        assertThat($(H2.class).first().getText()).isEqualTo("Calendario");
        assertThat($(com.vaadin.flow.component.html.Div.class).withClassName("boff-nome-persona").all())
                .extracting(d -> d.getElement().getTextRecursively())
                .contains("Mattia Bettagno", "Diego \"Cucciolo\" Castelli");
    }

    @Test
    @WithMockUser(username = "titolare@demo.it", roles = {"STAFF", "TITOLARE"})
    void ricercaRapida() {
        navigate("cerca?q=Ferri", it.boff.bike.ui.views.CercaView.class);
        String testo = getCurrentView().getElement().getTextRecursively();
        assertThat(testo).contains("Paolo Ferri", "Cannondale Trail 5");
    }

    @Test
    @WithMockUser(username = "titolare@demo.it", roles = {"STAFF", "TITOLARE"})
    void statisticheDelTitolare() {
        navigate(it.boff.bike.ui.views.StatisticheView.class);
        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("Lavori ricevuti", "Interventi più richiesti");
    }

    @Test
    @WithMockUser(username = "titolare@demo.it", roles = {"STAFF", "TITOLARE"})
    void ilTitolareDefinisceUnaNuovaAttivitaNelCatalogo() {
        String nome = "Montaggio portapacchi " + UUID.randomUUID().toString().substring(0, 6);
        navigate(it.boff.bike.ui.views.CatalogoView.class);
        test($(Button.class).withText("Nuovo intervento").single()).click();
        test($(TextField.class).withLabel("Nome").single()).setValue(nome);
        test($(com.vaadin.flow.component.textfield.IntegerField.class).withLabel("Tempo standard (minuti)").single())
                .setValue(40);
        test($(Button.class).withText("Salva").single()).click();
        assertThat(catalogo.attivi()).anySatisfy(t -> {
            assertThat(t.getNome()).isEqualTo(nome);
            assertThat(t.getDurataStandardMin()).isEqualTo(40);
        });
    }

    @Test
    @WithMockUser(username = "cliente@demo.it", roles = "CLIENTE")
    void ilClienteVedeLeAttivitaDelCatalogoEPuoScrivereTestoLibero() {
        String attiva = "Montaggio parafanghi " + UUID.randomUUID().toString().substring(0, 6);
        String disattivata = "Verniciatura " + UUID.randomUUID().toString().substring(0, 6);
        catalogo.salva(new TipoIntervento(attiva, 25));
        TipoIntervento vecchia = catalogo.salva(new TipoIntervento(disattivata, 60));
        vecchia.setAttivo(false);
        catalogo.salva(vecchia);

        Long cliente = utenti.findByEmail("cliente@demo.it").orElseThrow().getId();
        Long bici = biciclette.aggiungi(cliente, DatiBicicletta.di("Olmo", "Gran Sport " + UUID.randomUUID(),
                Tipologia.STRADA)).getId();
        navigate("le-mie-bici/" + bici, BiciView.class);
        test($(Button.class).withText("Annuncia un lavoro").single()).click();

        @SuppressWarnings("unchecked")
        CheckboxGroup<TipoIntervento> scelte = $(CheckboxGroup.class).single();
        List<String> proposte = scelte.getListDataView().getItems().map(TipoIntervento::getNome).toList();
        assertThat(proposte).contains(attiva).doesNotContain(disattivata);

        // solo testo libero, nessuna voce del catalogo
        test($(TextArea.class).withLabel("Altro o descrizione del problema").single())
                .setValue("Montare un supporto per il seggiolino bimbo");
        test($(Button.class).withText("Invia").single()).click();
        assertThat(lavori.storicoBiciDelCliente(cliente, bici)).singleElement()
                .satisfies(l -> assertThat(l.getNoteCliente()).isEqualTo("Montare un supporto per il seggiolino bimbo"));
    }

    /** Durate come "20 min", "1 h", "1 h 30 min": sono informazioni interne al negozio. */
    private static final java.util.regex.Pattern DURATA = java.util.regex.Pattern.compile("\\b\\d+\\s*(min|h)\\b");

    @Test
    @WithMockUser(username = "cliente@demo.it", roles = "CLIENTE")
    void ilClienteNonVedeMaiLeDuratePreviste() {
        Long cliente = utenti.findByEmail("cliente@demo.it").orElseThrow().getId();
        Long bici = biciclette.aggiungi(cliente, DatiBicicletta.di("Colnago", "V4 " + UUID.randomUUID(),
                Tipologia.STRADA)).getId();

        // modulo di annuncio: solo i nomi delle attività
        navigate("le-mie-bici/" + bici, BiciView.class);
        test($(Button.class).withText("Annuncia un lavoro").single()).click();
        String modulo = $(it.boff.bike.ui.views.AnnuncioDialog.class).single().getElement().getTextRecursively();
        assertThat(DURATA.matcher(modulo).find()).as("durate nel modulo di annuncio: " + modulo).isFalse();
        @SuppressWarnings("unchecked")
        CheckboxGroup<TipoIntervento> scelte = $(CheckboxGroup.class).single();
        List<String> etichette = scelte.getListDataView().getItems()
                .map(t -> scelte.getItemLabelGenerator().apply(t)).toList();
        assertThat(etichette).contains("Regolazione cambio")
                .noneMatch(e -> DURATA.matcher(e).find());

        // lavoro in corso, anche dopo che il negozio ha modificato i tempi
        Long id = lavori.annuncia(cliente, bici, List.of(catalogo.attivi().get(0).getId()), "Controllo").getId();
        lavori.checkIn(id);
        lavori.modificaDurata(lavori.dettaglio(id).getRighe().get(0).getId(), 95);
        navigate("le-mie-bici/" + bici, BiciView.class);
        String pagina = getCurrentView().getElement().getTextRecursively();
        assertThat(pagina).contains("Lavoro in corso");
        assertThat(DURATA.matcher(pagina).find()).as("durate nella pagina della bici: " + pagina).isFalse();

        navigate(LeMieBiciView.class);
        String elenco = getCurrentView().getElement().getTextRecursively();
        assertThat(DURATA.matcher(elenco).find()).as("durate nell'elenco bici").isFalse();
    }

    @Autowired it.boff.bike.funzionalita.FunzionalitaService funzionalita;

    @org.junit.jupiter.api.AfterEach
    void spegniFunzionalita() {
        for (var f : it.boff.bike.funzionalita.Funzionalita.values()) funzionalita.imposta(f, false);
    }

    @Test
    @WithMockUser(username = "titolare@demo.it", roles = {"STAFF", "TITOLARE"})
    void ilTitolareAttivaUnaFunzionalita() {
        navigate(it.boff.bike.ui.views.FunzionalitaView.class);
        String testo = getCurrentView().getElement().getTextRecursively();
        assertThat(testo).contains("Approvazione dei lavori extra", "Promemoria di manutenzione",
                "Tempo effettivo degli interventi", "Avviso di officina piena", "Ricalcolo automatico del calendario");
        List<Checkbox> interruttori = $(Checkbox.class).withCaption("Attiva").all();
        assertThat(interruttori).hasSize(5).allMatch(c -> !c.getValue());
        test(interruttori.get(0)).click();
        assertThat(funzionalita.attiva(it.boff.bike.funzionalita.Funzionalita.APPROVAZIONE_EXTRA)).isTrue();
    }

    @Test
    @WithMockUser(username = "cliente@demo.it", roles = "CLIENTE")
    void ilClienteApprovaUnInterventoExtraSenzaVedereITempi() {
        funzionalita.imposta(it.boff.bike.funzionalita.Funzionalita.APPROVAZIONE_EXTRA, true);
        Long cliente = utenti.findByEmail("cliente@demo.it").orElseThrow().getId();
        Long bici = biciclette.aggiungi(cliente, DatiBicicletta.di("Cervelo", "R5 " + UUID.randomUUID(),
                Tipologia.STRADA)).getId();
        Long id = lavori.creaAlBanco(bici, List.of(catalogo.attivi().get(0).getId()), null).getId();
        Long extra = lavori.proponiExtra(id, null, "Sostituzione pastiglie", 25, "Consumate al 90%").getId();

        navigate("le-mie-bici/" + bici, BiciView.class);
        String pagina = getCurrentView().getElement().getTextRecursively();
        assertThat(pagina).contains("Serve il tuo ok: Sostituzione pastiglie", "Consumate al 90%");
        assertThat(DURATA.matcher(pagina).find()).as("nessuna durata nella richiesta: " + pagina).isFalse();

        test($(Button.class).withText("Approva").single()).click();
        assertThat(lavori.dettaglio(id).getRighe().stream().filter(r -> r.getId().equals(extra)).findFirst().orElseThrow()
                .getApprovazione()).isEqualTo(it.boff.bike.lavoro.Approvazione.APPROVATA);
        assertThat(getCurrentView().getElement().getTextRecursively()).doesNotContain("Serve il tuo ok");
    }

    @Test
    @WithMockUser(username = "cliente@demo.it", roles = "CLIENTE")
    void avvisoDiOfficinaPienaSoloSeAttivo() {
        Long cliente = utenti.findByEmail("cliente@demo.it").orElseThrow().getId();
        Long bici = biciclette.aggiungi(cliente, DatiBicicletta.di("Orbea", "Orca " + UUID.randomUUID(),
                Tipologia.STRADA)).getId();
        // officina piena: un lavoro enorme per ogni persona in officina
        try (var pieno = new it.boff.bike.OfficinaPiena(cliente, biciclette, lavori, catalogo, pianificazione)) {
            navigate("le-mie-bici/" + bici, BiciView.class);
            assertThat(getCurrentView().getElement().getTextRecursively()).doesNotContain("molto impegnata");

            funzionalita.imposta(it.boff.bike.funzionalita.Funzionalita.AVVISO_CARICO, true);
            funzionalita.impostaSogliaCarico(80);
            navigate("le-mie-bici/" + bici, BiciView.class);
            String pagina = getCurrentView().getElement().getTextRecursively();
            assertThat(pagina).contains("l'officina è molto impegnata");
            assertThat(pagina).doesNotContain("%");
        }
    }

    @Test
    @WithMockUser(username = "collaboratore@demo.it", roles = "STAFF")
    void lavoroAlBancoPerUnClienteSenzaApp() {
        String cognome = "Banco" + UUID.randomUUID().toString().substring(0, 8);
        navigate(LavoriView.class);
        test($(Button.class).withText("Nuovo lavoro").single()).click();

        // nuovo cliente senza email, creato senza uscire dalla finestra
        test($(Button.class).withText("Nuovo cliente").single()).click();
        test($(TextField.class).withLabel("Nome").single()).setValue("Gino");
        test($(TextField.class).withLabel("Cognome").single()).setValue(cognome);
        test($(TextField.class).withLabel("Telefono").single()).setValue("0461 123456");
        test($(Button.class).withText("Salva").single()).click();
        assertThat($(Button.class).withText("Nuovo cliente").all()).as("senza conferma dell'informativa non salva")
                .hasSize(1);
        test($(Checkbox.class).withCaption("Ho mostrato o consegnato al cliente l'informativa privacy").single()).click();
        test($(Button.class).withText("Salva").single()).click();

        // nuova bici, sempre dalla stessa finestra
        test($(Button.class).withText("Nuova bici").single()).click();
        test($(TextField.class).withLabel("Marca").single()).setValue("Atala");
        test($(TextField.class).withLabel("Modello").single()).setValue("Città");
        test($(com.vaadin.flow.component.select.Select.class).withLabel("Tipologia").single())
                .selectItem(Tipologia.CITTA.getEtichetta());
        test($(Button.class).withText("Salva").single()).click();

        test($(TextArea.class).single()).setValue("Freni che fischiano");
        test($(Button.class).withText("Crea lavoro").single()).click();

        var cliente = utenti.findAll().stream().filter(u -> cognome.equals(u.getCognome())).findFirst().orElseThrow();
        assertThat(cliente.usaApp()).isFalse();
        var biciCliente = biciclette.elencoDi(cliente.getId());
        assertThat(biciCliente).singleElement().satisfies(b -> assertThat(b.getMarca()).isEqualTo("Atala"));
        assertThat(lavori.storicoBici(biciCliente.get(0).getId())).singleElement()
                .satisfies(l -> assertThat(l.getStato()).isIn(StatoLavoro.IN_OFFICINA.toArray()));
        assertThat($(com.vaadin.flow.component.html.Span.class).withText("Senza app").exists()).isTrue();
    }

    @Test
    @WithMockUser(username = "collaboratore@demo.it", roles = "STAFF")
    void ilNegozioEliminaIDatiDiUnClienteSuRichiesta() {
        String cognome = "Privacy" + UUID.randomUUID().toString().substring(0, 8);
        navigate(it.boff.bike.ui.views.ClientiView.class);
        test($(Button.class).withText("Nuovo cliente").single()).click();
        test($(TextField.class).withLabel("Nome").single()).setValue("Rita");
        test($(TextField.class).withLabel("Cognome").single()).setValue(cognome);
        test($(TextField.class).withLabel("Telefono").single()).setValue("0461 654321");
        test($(Checkbox.class).withCaption("Ho mostrato o consegnato al cliente l'informativa privacy").single()).click();
        test($(Button.class).withText("Salva").single()).click();

        assertThat($(com.vaadin.flow.component.html.Anchor.class).withText("Scarica i dati del cliente").exists()).isTrue();
        test($(Button.class).withText("Elimina i dati del cliente").single()).click();
        test($(com.vaadin.flow.component.confirmdialog.ConfirmDialog.class).single()).confirm();

        assertThat(utenti.findAll()).noneMatch(u -> cognome.equals(u.getCognome()));
    }

    @Test
    void accessoConCodiceViaEmail() {
        navigate(it.boff.bike.ui.views.AccessoCodiceView.class);
        test($(EmailField.class).withLabel("Email").single()).setValue("cliente@demo.it");
        test($(Button.class).withText("Inviami il codice").single()).click();
        assertThat($(H2.class).first().getText()).isEqualTo("Scrivi il codice");
        // modulo classico verso /login con l'email nascosta e il campo del codice
        String html = getCurrentView().getElement().getOuterHTML();
        assertThat(html).contains("action=\"login\"", "name=\"username\"", "value=\"cliente@demo.it\"",
                "autocomplete=\"one-time-code\"");
    }

    @Test
    @WithMockUser(username = "titolare@demo.it", roles = {"STAFF", "TITOLARE"})
    void ilCalendarioHaTreVisualizzazioniEDueSettimane() {
        navigate(CalendarioView.class);
        assertThat($(com.vaadin.flow.component.html.Div.class).withClassName("boff-etichetta-settimana").all())
                .as("due settimane, una sotto l'altra").hasSize(2);
        test($(Button.class).withText("Linea del tempo").single()).click();
        assertThat($(com.vaadin.flow.component.html.Div.class).withClassName("boff-tela").all()).isNotEmpty();
        test($(Button.class).withText("Dettagliata").single()).click();
        assertThat($(com.vaadin.flow.component.html.Div.class).withClassName("boff-tela").all()).isEmpty();
        test($(Button.class).withText("Compatta").single()).click();
        test($(Button.class).withText("1").single()).click();
        assertThat($(com.vaadin.flow.component.html.Div.class).withClassName("boff-etichetta-settimana").all()).hasSize(1);
        test($(Button.class).withText("2").single()).click();
    }

    @Autowired it.boff.bike.appuntamento.AppuntamentoService appuntamentiService;

    @Test
    @WithMockUser(username = "sara.conti@demo.it", roles = "CLIENTE")
    void ilClientePrenotaUnAppuntamento() {
        navigate(it.boff.bike.ui.views.IMieiAppuntamentiView.class);
        test($(Button.class).withText("Prenota un appuntamento").single()).click();
        @SuppressWarnings("unchecked")
        com.vaadin.flow.component.radiobutton.RadioButtonGroup<it.boff.bike.appuntamento.TipoAppuntamento> tipo =
                $(com.vaadin.flow.component.radiobutton.RadioButtonGroup.class).single();
        tipo.setValue(appuntamentiService.tipiAttivi().get(1));
        // primo giorno libero, primo orario, invio
        List<Button> scelte = $(Button.class).all().stream()
                .filter(b -> b.getParent().map(x -> x.getElement().getClassList().contains("boff-scelte")).orElse(false))
                .toList();
        assertThat(scelte).as("giorni liberi proposti").isNotEmpty();
        test(scelte.get(0)).click();
        List<Button> ore = $(Button.class).all().stream()
                .filter(b -> b.getText().matches("\\d{2}:\\d{2}")).toList();
        assertThat(ore).as("orari liberi proposti").isNotEmpty();
        test(ore.get(0)).click();
        test($(Button.class).withText("Invia la richiesta").single()).click();

        Long sara = utenti.findByEmail("sara.conti@demo.it").orElseThrow().getId();
        assertThat(appuntamentiService.delCliente(sara)).anySatisfy(a ->
                assertThat(a.getStato()).isEqualTo(it.boff.bike.appuntamento.StatoAppuntamento.RICHIESTO));
        assertThat(getCurrentView().getElement().getTextRecursively()).contains("In attesa di conferma");
    }

    @Test
    @WithMockUser(username = "collaboratore@demo.it", roles = "STAFF")
    void ilNegozioConfermaUnaRichiesta() {
        Long giulia = utenti.findByEmail("cliente@demo.it").orElseThrow().getId();
        var tipo = appuntamentiService.tipiAttivi().get(3);
        var liberi = appuntamentiService.orariLiberi(tipo.getId(), java.time.LocalDate.now().plusDays(3),
                java.time.LocalDate.now().plusDays(20));
        var giorno = liberi.keySet().iterator().next();
        var a = appuntamentiService.richiedi(giulia, tipo.getId(), giorno, liberi.get(giorno).get(0), null, null);

        navigate(it.boff.bike.ui.views.AppuntamentiView.class);
        var voci = $(com.vaadin.flow.component.html.Div.class).withClassName("boff-voce-appuntamento").all();
        var voce = voci.stream().filter(v -> v.getElement().getTextRecursively().contains("Giulia Verdi")
                && v.getElement().getClassList().contains("richiesto")).findFirst().orElseThrow();
        // la richiesta si conferma direttamente dalla sua scheda, senza aprire il dettaglio
        Button conferma = discendenti(voce).filter(c -> c instanceof Button b && "Conferma".equals(b.getText()))
                .map(Button.class::cast).findFirst().orElseThrow();
        test(conferma).click();
        assertThat(appuntamentiService.dettaglio(a.getId()).getStato())
                .isEqualTo(it.boff.bike.appuntamento.StatoAppuntamento.CONFERMATO);

        navigate(CalendarioView.class);
        assertThat($(com.vaadin.flow.component.html.Div.class).withClassName("appuntamento").all())
                .as("gli appuntamenti compaiono nel calendario").isNotEmpty();
        appuntamentiService.annulla(a.getId());
    }

    @Test
    @WithMockUser(username = "titolare@demo.it", roles = {"STAFF", "TITOLARE"})
    void iContatoriDellaBachecaApronoLElenco() {
        navigate(BachecaView.class);
        var contatori = $(com.vaadin.flow.router.RouterLink.class).withClassName("boff-kpi-link").all();
        assertThat(contatori).hasSize(4);
        assertThat(contatori.get(0).getHref()).isEqualTo("lavori?scheda=officina");
        assertThat(contatori.get(1).getHref()).isEqualTo("lavori?scheda=pronti");
        assertThat(contatori.get(2).getHref()).isEqualTo("lavori?scheda=annunciati");
        assertThat(contatori.get(3).getHref()).isEqualTo("calendario");

        navigate("lavori?scheda=pronti", LavoriView.class);
        assertThat($(com.vaadin.flow.component.tabs.Tabs.class).single().getSelectedTab().getLabel()).isEqualTo("Pronti");
    }

    @Test
    @WithMockUser(username = "titolare@demo.it", roles = {"STAFF", "TITOLARE"})
    void dalCalendarioSiRegistraLArrivoDellaBici() {
        Long cliente = utenti.findByEmail("cliente@demo.it").orElseThrow().getId();
        Long mario = utenti.findByEmail("titolare@demo.it").orElseThrow().getId();
        Long bici = biciclette.aggiungi(cliente, DatiBicicletta.di("Look", "765", Tipologia.STRADA)).getId();
        Long lavoro = lavori.annuncia(cliente, bici, List.of(catalogo.attivi().get(0).getId()), null).getId();
        // il primo giorno utile delle prossime settimane
        java.time.LocalDate g = java.time.LocalDate.now().plusDays(3);
        for (int i = 0; i < 30; i++, g = g.plusDays(1)) {
            try {
                pianificazione.fissaLavoro(lavoro, mario, g, null);
                break;
            } catch (it.boff.bike.common.ErroreDominio e) {
                // giorno senza posto: si prova il successivo
            }
        }
        navigate("calendario?data=" + g, CalendarioView.class);
        var blocco = $(com.vaadin.flow.component.html.Div.class).withClassName("in-arrivo").all().stream()
                .filter(d -> d.getElement().getTextRecursively().contains("Look 765")).findFirst().orElseThrow();
        test(blocco).click();
        test($(Button.class).withText("Bici arrivata in negozio").single()).click();
        assertThat(lavori.dettaglio(lavoro).getStato()).isNotEqualTo(it.boff.bike.lavoro.StatoLavoro.ANNUNCIATO);
        assertThat(pianificazione.diLavoro(lavoro)).as("resta dov'era").allMatch(a -> a.isBloccata());
        lavori.annulla(lavoro);
    }

    private static java.util.stream.Stream<com.vaadin.flow.component.Component> discendenti(com.vaadin.flow.component.Component c) {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(c), c.getChildren().flatMap(InterfacciaIT::discendenti));
    }
}
