package it.boff.bike.ui.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.config.BoffProperties;
import it.boff.bike.lavoro.Lavoro;
import it.boff.bike.lavoro.LavoroService;
import it.boff.bike.lavoro.RigaLavoro;
import it.boff.bike.lavoro.StatoLavoro;
import it.boff.bike.pianificazione.Assegnazione;
import it.boff.bike.pianificazione.PianificazioneService;
import it.boff.bike.security.Ruoli;
import it.boff.bike.ui.componenti.Durate;
import it.boff.bike.ui.componenti.Formati;
import it.boff.bike.ui.componenti.SchedaBici;
import it.boff.bike.utente.Utente;
import it.boff.bike.utente.UtenteService;
import jakarta.annotation.security.RolesAllowed;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Foglio lavori di un lavoro: chi, quale bici, quando è stata annunciata, consegnata, pronta e ritirata,
 * e per ogni intervento quando è stato fatto e da chi. Pagina a sé (senza menu), pronta da stampare o
 * salvare in PDF dal browser.
 */
@Route(value = "foglio-lavoro/:id", autoLayout = false)
@PageTitle("Foglio lavori | Boff Bike")
@RolesAllowed(Ruoli.STAFF)
public class FoglioLavoroView extends Div implements BeforeEnterObserver {

    private final LavoroService lavori;
    private final PianificazioneService pianificazione;
    private final UtenteService utenti;
    private final BoffProperties properties;
    private final it.boff.bike.calendario.CalendarioService calendario;

    public FoglioLavoroView(LavoroService lavori, PianificazioneService pianificazione, UtenteService utenti,
                            BoffProperties properties, it.boff.bike.calendario.CalendarioService calendario) {
        this.lavori = lavori;
        this.pianificazione = pianificazione;
        this.utenti = utenti;
        this.properties = properties;
        this.calendario = calendario;
        addClassName("boff-foglio");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Long id = event.getRouteParameters().getLong("id").orElse(null);
        Lavoro l;
        try {
            l = lavori.dettaglio(id);
        } catch (ErroreDominio | NullPointerException e) {
            event.forwardTo(LavoriView.class);
            return;
        }
        mostra(l);
    }

    private void mostra(Lavoro l) {
        removeAll();
        Map<Long, String> persone = utenti.personale().stream()
                .collect(Collectors.toMap(Utente::getId, Utente::getNomeCompleto, (a, b) -> a));
        Map<Long, List<Assegnazione>> pianificato = pianificazione.diLavoro(l.getId()).stream()
                .collect(Collectors.groupingBy(a -> a.getRiga().getId()));

        // comandi (non si stampano)
        RouterLink torna = new RouterLink("← Torna al lavoro", LavoriView.class, LavoriView.parametri(l.getId()));
        Button stampa = new Button("Stampa o salva PDF", VaadinIcon.PRINT.create(),
                e -> getElement().executeJs("window.print()"));
        stampa.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Div comandi = new Div(torna, stampa);
        comandi.addClassNames("boff-foglio-comandi", "boff-no-print");

        // intestazione
        Image logo = new Image("icons/logo.png", "");
        logo.addClassName("boff-foglio-logo");
        Div negozio = new Div(logo, new Span(properties.nomeNegozio()));
        negozio.addClassName("boff-foglio-negozio");
        H1 titolo = new H1("Foglio lavori n. " + l.getId());
        Span stato = new Span(l.getStato().getEtichetta());
        stato.addClassName("boff-foglio-stato");
        Div testa = new Div(negozio, new Div(titolo, stato));
        testa.addClassName("boff-foglio-testa");

        // chi e cosa
        Utente cliente = l.getBicicletta().getProprietario();
        Div schedaCliente = blocco("Cliente", cliente.getNomeCompleto(),
                cliente.getTelefono(), cliente.getEmail());
        var b = l.getBicicletta();
        Div schedaBici = blocco("Bicicletta", b.getNome(), SchedaBici.descrizione(b),
                b.getNumeroTelaio() == null ? null : "Telaio n. " + b.getNumeroTelaio());
        Div chiCosa = new Div(schedaCliente, schedaBici);
        chiCosa.addClassName("boff-foglio-due");

        add(comandi, testa, chiCosa, new H2("Cronologia"), cronologia(l), new H2("Interventi"),
                interventi(l, persone, pianificato));

        if (l.getNoteCliente() != null || l.getNoteInterne() != null) {
            add(new H2("Note"));
            if (l.getNoteCliente() != null) add(new Paragraph("Del cliente: " + l.getNoteCliente()));
            if (l.getNoteInterne() != null) add(new Paragraph("Del negozio: " + l.getNoteInterne()));
        }
        Paragraph piede = new Paragraph("Foglio generato il " + Formati.dataOra(calendario.adesso().atZone(Formati.zona())
                .toOffsetDateTime()) + " · " + properties.nomeNegozio());
        piede.addClassName("boff-foglio-piede");
        add(piede);
    }

    private static Div blocco(String titolo, String principale, String... righe) {
        Span t = new Span(titolo);
        t.addClassName("etichetta");
        Span p = new Span(principale);
        p.addClassName("principale");
        Div d = new Div(t, p);
        for (String r : righe) if (r != null && !r.isBlank()) d.add(new Div(new Span(r)));
        d.addClassName("boff-foglio-blocco");
        return d;
    }

    /** Le tappe del lavoro con data e ora; quelle non ancora avvenute restano con un trattino. */
    private Component cronologia(Lavoro l) {
        // lavoro aperto al banco: nasce già "in negozio", con annuncio e consegna nello stesso istante
        boolean alBanco = l.getCheckinIl() != null && l.getCheckinIl().isEqual(l.getAnnunciatoIl());
        Div t = tabella("Tappa", "Data e ora");
        riga(t, alBanco ? "Lavoro aperto al banco" : "Annunciato dal cliente", Formati.dataOra(l.getAnnunciatoIl()));
        if (l.getArrivoPrevisto() != null && l.getCheckinIl() == null) {
            riga(t, "Consegna della bici concordata", Formati.dataOra(l.getArrivoPrevisto().atZone(Formati.zona()).toOffsetDateTime()));
        }
        riga(t, "Bici consegnata in negozio", Formati.dataOra(l.getCheckinIl()));
        if (l.getDataPromessa() != null) riga(t, "Promessa al cliente per", Formati.giornoAnno(l.getDataPromessa()));
        OffsetDateTime primo = l.getRighe().stream().map(RigaLavoro::getCompletataIl).filter(java.util.Objects::nonNull)
                .min(OffsetDateTime::compareTo).orElse(null);
        riga(t, "Primo intervento fatto", Formati.dataOra(primo));
        riga(t, "Bici pronta", Formati.dataOra(l.getProntoIl()));
        if (l.getStato() == StatoLavoro.ANNULLATO) riga(t, "Lavoro annullato", Formati.dataOra(l.getChiusoIl()));
        else riga(t, "Ritirata dal cliente", Formati.dataOra(l.getChiusoIl()));
        return t;
    }

    private Component interventi(Lavoro l, Map<Long, String> persone, Map<Long, List<Assegnazione>> pianificato) {
        Div t = tabella("Intervento", "Tempo", "In calendario", "Fatto il", "Da");
        t.addClassName("cinque");
        long previsti = 0, reali = 0;
        for (RigaLavoro r : l.getRighe()) {
            String nome = r.getDescrizione() + (r.getApprovazione() == null ? ""
                    : " (extra: " + r.getApprovazione().getEtichetta().toLowerCase() + ")");
            String tempo = Durate.formatta(r.getDurataMin())
                    + (r.getMinutiEffettivi() == null ? "" : " · reale " + Durate.formatta(r.getMinutiEffettivi()));
            String calendarioTesto = pianificato.getOrDefault(r.getId(), List.of()).stream()
                    .map(a -> Formati.giorno(a.getData()) + " " + a.getOraInizio() + "–" + a.getOraFine() + " " + a.getUtente().getNome())
                    .collect(Collectors.joining("; "));
            String fatto = r.isCompletata() ? Formati.dataOra(r.getCompletataIl()) : "non fatto";
            String chi = r.getCompletataDa() == null ? (r.isCompletata() ? "–" : "") : persone.getOrDefault(r.getCompletataDa(), "–");
            riga(t, nome, tempo, calendarioTesto.isEmpty() ? "–" : calendarioTesto, fatto, chi);
            if (r.isValida()) {
                previsti += r.getDurataMin();
                reali += r.getMinutiEffettivi() == null ? r.getDurataMin() : r.getMinutiEffettivi();
            }
        }
        Paragraph totale = new Paragraph("Totale previsto " + Durate.formatta(previsti)
                + (previsti == reali ? "" : " · reale " + Durate.formatta(reali)));
        totale.addClassName("boff-foglio-totale");
        return new Div(t, totale);
    }

    private static Div tabella(String... intestazioni) {
        Div t = new Div();
        t.addClassName("boff-foglio-tabella");
        t.getElement().setAttribute("role", "table");
        Div testa = new Div();
        testa.addClassName("intestazione");
        for (String h : intestazioni) testa.add(new Span(h));
        t.add(testa);
        return t;
    }

    private static void riga(Div tabella, String... celle) {
        Div r = new Div();
        r.addClassName("riga");
        for (String c : celle) r.add(new Span(c));
        tabella.add(r);
    }

}
