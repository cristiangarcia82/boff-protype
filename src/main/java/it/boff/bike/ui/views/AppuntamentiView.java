package it.boff.bike.ui.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import it.boff.bike.appuntamento.Appuntamento;
import it.boff.bike.appuntamento.AppuntamentoService;
import it.boff.bike.appuntamento.StatoAppuntamento;
import it.boff.bike.calendario.CalendarioService;
import it.boff.bike.security.Ruoli;
import it.boff.bike.ui.componenti.Formati;
import it.boff.bike.utente.Utente;
import it.boff.bike.utente.UtenteService;
import jakarta.annotation.security.RolesAllowed;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Appuntamenti di consulenza. A sinistra cosa richiede un'azione (richieste da confermare, appuntamenti
 * passati da chiudere) con i pulsanti direttamente sulla scheda; a destra l'agenda dei prossimi 14 giorni
 * divisa per giorno. Un tocco sulla scheda apre il dettaglio.
 */
@Route("appuntamenti")
@PageTitle("Appuntamenti | Boff Bike")
@RolesAllowed(Ruoli.STAFF)
public class AppuntamentiView extends Div {

    private final AppuntamentoService service;
    private final CalendarioService calendario;
    private final List<Utente> personale;
    private final Select<Utente> filtro = new Select<>();
    private final H3 titoloRichieste = new H3();
    private final Div richieste = new Div();
    private final Div daChiudere = new Div();
    private final Div agenda = new Div();

    public AppuntamentiView(AppuntamentoService service, CalendarioService calendario, UtenteService utenti) {
        this.service = service;
        this.calendario = calendario;
        this.personale = utenti.personale().stream().filter(Utente::isAttivo).toList();
        addClassNames("boff-pagina", "boff-colonna", "boff-appuntamenti");

        Button nuovo = new Button("Nuovo appuntamento", VaadinIcon.PLUS.create(), e ->
                new NuovoAppuntamentoDialog(service, utenti, personale, calendario.oggi(), this::aggiorna).open());
        nuovo.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Div intestazione = new Div(new H2("Appuntamenti"), nuovo);
        intestazione.addClassName("boff-intestazione");

        filtro.setLabel("Di chi");
        filtro.setItems(personale);
        filtro.setItemLabelGenerator(u -> u == null ? "Tutti" : u.getNome());
        filtro.setEmptySelectionAllowed(true);
        filtro.setEmptySelectionCaption("Tutti");
        filtro.addValueChangeListener(e -> aggiorna());
        filtro.setWidth("12em");

        Div sinistra = new Div(scheda(titoloRichieste, richieste), daChiudere);
        sinistra.addClassName("boff-colonna");
        Div destra = scheda(new H3("Prossimi 14 giorni"), agenda);
        Div colonne = new Div(sinistra, destra);
        colonne.addClassName("boff-due-colonne");
        add(intestazione, filtro, colonne);
        aggiorna();
    }

    private static Div scheda(H3 titolo, Div contenuto) {
        Div d = new Div(titolo, contenuto);
        d.addClassName("boff-card");
        return d;
    }

    private void aggiorna() {
        LocalDate oggi = calendario.oggi();
        Predicate<Appuntamento> diChi = a -> filtro.getValue() == null || a.getUtente().getId().equals(filtro.getValue().getId());

        List<Appuntamento> daConfermare = service.daConfermare().stream().filter(diChi).toList();
        titoloRichieste.setText(daConfermare.isEmpty() ? "Richieste da confermare" : "Richieste da confermare (" + daConfermare.size() + ")");
        richieste.removeAll();
        if (daConfermare.isEmpty()) richieste.add(vuoto("Nessuna richiesta da confermare."));
        daConfermare.forEach(a -> richieste.add(richiesta(a)));

        List<Appuntamento> passati = service.daChiudere().stream().filter(diChi).toList();
        daChiudere.removeAll();
        if (!passati.isEmpty()) {
            Div box = new Div();
            passati.forEach(a -> box.add(voceAgenda(a, true)));
            Paragraph spiega = vuoto("Già passati: segnali come svolti, così non restano in sospeso.");
            Div card = scheda(new H3("Da chiudere (" + passati.size() + ")"), box);
            card.addComponentAtIndex(1, spiega);
            daChiudere.add(card);
        }

        agenda.removeAll();
        Map<LocalDate, List<Appuntamento>> perGiorno = new LinkedHashMap<>();
        service.tra(oggi, oggi.plusDays(13)).stream()
                .filter(a -> a.getStato() == StatoAppuntamento.CONFERMATO).filter(diChi)
                .forEach(a -> perGiorno.computeIfAbsent(a.getData(), k -> new java.util.ArrayList<>()).add(a));
        if (perGiorno.isEmpty()) agenda.add(vuoto("Nessun appuntamento confermato."));
        perGiorno.forEach((giorno, elenco) -> {
            H4 titolo = new H4(etichettaGiorno(giorno, oggi));
            titolo.addClassName("boff-giorno-agenda");
            agenda.add(titolo);
            elenco.forEach(a -> agenda.add(voceAgenda(a, false)));
        });
    }

    /** Scheda di una richiesta: chi, cosa, quando, note, e i pulsanti per decidere subito. */
    private Component richiesta(Appuntamento a) {
        boolean passato = service.iniziato(a);
        Utente cliente = a.getCliente();

        Span quando = new Span(AppuntamentoDialog.capitale(Formati.giorno(a.getData())) + " · " + a.getOraInizio() + "–" + a.getOraFine());
        quando.addClassName("boff-voce-quando");
        Span chi = new Span(cliente.getNomeCompleto());
        chi.addClassName("boff-voce-chi");
        Div riga1 = new Div(quando);
        Div riga2 = new Div(chi, new Span(" "), AppuntamentoDialog.telefono(cliente));
        Div riga3 = new Div(new Span(a.getTipo().getNome() + " · con " + a.getUtente().getNome()
                + (a.getBicicletta() != null ? " · " + a.getBicicletta().getNome() : "")));
        riga3.addClassName("boff-testo-secondario");
        Div testo = new Div(riga1, riga2, riga3);
        if (a.getNoteCliente() != null) {
            String nota = a.getNoteCliente();
            Paragraph p = new Paragraph("«" + (nota.length() > 140 ? nota.substring(0, 140) + "…" : nota) + "»");
            p.addClassName("boff-voce-nota");
            testo.add(p);
        }
        if (a.getCreatoIl() != null) {
            Span quandoRichiesto = new Span("Richiesta " + fa(a.getCreatoIl()));
            quandoRichiesto.addClassName("boff-testo-secondario");
            testo.add(new Div(quandoRichiesto));
        }
        apriDettaglioAlClic(testo, a, false);

        Div azioni = new Div();
        azioni.addClassName("boff-azioni-voce");
        if (passato) {
            Paragraph avviso = new Paragraph("L'orario è già passato: proponine un altro o rifiuta.");
            avviso.addClassName("boff-avviso");
            azioni.add(avviso);
        } else {
            Button conferma = new Button("Conferma", VaadinIcon.CHECK.create(),
                    e -> AppuntamentoDialog.conferma(service, a, this::aggiorna));
            conferma.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_SMALL);
            azioni.add(conferma);
        }
        Button cambia = new Button("Cambia orario", VaadinIcon.CLOCK.create(), e -> apriDettaglio(a, true));
        cambia.addThemeVariants(ButtonVariant.LUMO_SMALL);
        Button rifiuta = new Button("Rifiuta", e -> AppuntamentoDialog.chiediAnnullamento(service, a, this::aggiorna));
        rifiuta.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        azioni.add(cambia, rifiuta);

        Div voce = new Div(testo, azioni);
        voce.addClassNames("boff-voce-appuntamento", "richiesto", "boff-voce-richiesta");
        return voce;
    }

    /** Riga compatta dell'agenda: orario, cliente, tipo, persona; "Svolto" quando è già iniziato. */
    private Component voceAgenda(Appuntamento a, boolean conGiorno) {
        Span ora = new Span((conGiorno ? AppuntamentoDialog.capitale(Formati.giorno(a.getData())) + " " : "")
                + a.getOraInizio() + "–" + a.getOraFine());
        ora.addClassName("boff-voce-quando");
        Span chi = new Span(a.getCliente().getNomeCompleto());
        chi.addClassName("boff-voce-chi");
        Span cosa = new Span(a.getTipo().getNome() + " · con " + a.getUtente().getNome());
        cosa.addClassName("boff-testo-secondario");
        Div testo = new Div(ora, new Div(chi), new Div(cosa));
        apriDettaglioAlClic(testo, a, false);
        Div voce = new Div(testo);
        voce.addClassNames("boff-voce-appuntamento", "boff-voce-agenda");
        if (service.iniziato(a)) {
            Button svolto = new Button("Svolto", VaadinIcon.CHECK.create(), e -> AppuntamentoDialog.segnaSvolto(service, a, this::aggiorna));
            svolto.addThemeVariants(ButtonVariant.LUMO_SMALL);
            Div azioni = new Div(svolto);
            azioni.addClassName("boff-azioni-voce");
            voce.add(azioni);
        }
        return voce;
    }

    private void apriDettaglioAlClic(Div testo, Appuntamento a, boolean cambioOrario) {
        testo.addClassName("boff-voce-testo");
        testo.getElement().setAttribute("role", "button");
        testo.getElement().setAttribute("tabindex", "0");
        testo.getElement().setAttribute("title", "Apri il dettaglio");
        testo.addClickListener(e -> apriDettaglio(a, cambioOrario));
    }

    private void apriDettaglio(Appuntamento a, boolean cambioOrario) {
        new AppuntamentoDialog(service, personale, calendario.oggi(), a.getId(), this::aggiorna, cambioOrario).open();
    }

    private static Paragraph vuoto(String testo) {
        Paragraph p = new Paragraph(testo);
        p.addClassName("boff-testo-secondario");
        return p;
    }

    static String etichettaGiorno(LocalDate giorno, LocalDate oggi) {
        String data = Formati.giorno(giorno);
        if (giorno.equals(oggi)) return "Oggi · " + data;
        if (giorno.equals(oggi.plusDays(1))) return "Domani · " + data;
        return AppuntamentoDialog.capitale(data);
    }

    private String fa(OffsetDateTime t) {
        Duration d = Duration.between(t.toInstant(), calendario.adesso().atZone(Formati.zona()).toInstant());
        if (d.toMinutes() < 60) return "pochi minuti fa";
        if (d.toHours() < 24) return d.toHours() == 1 ? "un'ora fa" : d.toHours() + " ore fa";
        long giorni = d.toDays();
        return giorni == 1 ? "ieri" : giorni + " giorni fa";
    }
}
