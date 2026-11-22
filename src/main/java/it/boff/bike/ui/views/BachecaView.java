package it.boff.bike.ui.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.H4;
import it.boff.bike.calendario.CalendarioService;
import it.boff.bike.calendario.Disponibilita;
import it.boff.bike.calendario.TipoFascia;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.lavoro.Lavoro;
import it.boff.bike.pianificazione.Assegnazione;
import it.boff.bike.pianificazione.PianificazioneService;
import it.boff.bike.notifiche.NotificheService;
import it.boff.bike.security.UtenteCorrente;
import it.boff.bike.ui.componenti.AttivaNotifiche;
import it.boff.bike.ui.componenti.Avvisi;
import it.boff.bike.ui.componenti.Durate;
import it.boff.bike.utente.Utente;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.Map;
import it.boff.bike.lavoro.LavoroService;
import it.boff.bike.lavoro.StatoLavoro;
import it.boff.bike.security.Ruoli;
import it.boff.bike.ui.componenti.Formati;
import jakarta.annotation.security.RolesAllowed;

import java.util.EnumSet;
import java.util.List;

/**
 * Bacheca del personale (titolare e collaboratore).
 * Pensata per il monitor del Mac: indicatori in riga, pannello principale largo
 * e colonna laterale; su telefono gli stessi blocchi si impilano.
 */
@Route("bacheca")
@PageTitle("Bacheca | Boff Bike")
@RolesAllowed(Ruoli.STAFF)
public class BachecaView extends Div {

    public BachecaView(LavoroService lavori, PianificazioneService pianificazione, CalendarioService calendario,
                       NotificheService notifiche, UtenteCorrente utenteCorrente,
                       it.boff.bike.accesso.PasskeyService passkey,
                       it.boff.bike.appuntamento.AppuntamentoService appuntamenti) {
        addClassName("boff-pagina");
        LocalDate oggi = calendario.oggi();
        LocalDate lunedi = oggi.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sabatoDomenica = lunedi.plusDays(6);
        Disponibilita disp = calendario.disponibilita(lunedi, sabatoDomenica);
        long capacita = 0;
        for (Long p : disp.persone()) {
            for (LocalDate g = oggi; !g.isAfter(sabatoDomenica); g = g.plusDays(1)) {
                capacita += disp.minuti(p, g, TipoFascia.MANUTENZIONE);
            }
        }
        long assegnati = pianificazione.tra(oggi, sabatoDomenica).stream().mapToLong(a -> a.intervallo().minuti()).sum();
        String carico = capacita == 0 ? "–" : Math.round(100.0 * assegnati / capacita) + "%";

        Div kpi = new Div(
                indicatore(String.valueOf(lavori.conta(StatoLavoro.IN_OFFICINA)), "Bici in officina",
                        LavoriView.class, LavoriView.scheda("officina")),
                indicatore(String.valueOf(lavori.conta(EnumSet.of(StatoLavoro.PRONTO))), "Pronte da ritirare",
                        LavoriView.class, LavoriView.scheda("pronti")),
                indicatore(String.valueOf(lavori.conta(EnumSet.of(StatoLavoro.ANNUNCIATO))), "Annunci in attesa della bici",
                        LavoriView.class, LavoriView.scheda("annunciati")),
                indicatore(carico, "Officina occupata da oggi a fine settimana ("
                        + Durate.formatta(assegnati) + " su " + Durate.formatta(capacita) + ")",
                        CalendarioView.class, com.vaadin.flow.router.QueryParameters.empty()));
        kpi.addClassName("boff-kpi");

        Div principale = new Div(
                oggiInOfficina(pianificazione.tra(oggi, oggi), lavori, disp, oggi),
                elencoLavori("In officina", lavori.cerca(StatoLavoro.IN_OFFICINA, ""),
                        "Nessuna bici in officina."));
        principale.addClassName("boff-colonna");

        Div laterale = new Div(
                appuntamenti(appuntamenti, oggi),
                elencoLavori("Nuovi annunci", lavori.cerca(EnumSet.of(StatoLavoro.ANNUNCIATO), ""),
                        "Nessun annuncio in attesa."),
                elencoLavori("Pronte da ritirare", lavori.cerca(EnumSet.of(StatoLavoro.PRONTO), ""),
                        "Nessuna bici in attesa di ritiro."));
        laterale.addClassName("boff-colonna");

        Div corpo = new Div(principale, laterale);
        corpo.addClassName("boff-bacheca-corpo");

        add(new H2("Bacheca"), new AttivaNotifiche(notifiche, utenteCorrente.get().orElseThrow().getId(),
                "Ricevi un avviso quando un cliente annuncia un nuovo lavoro."),
                new it.boff.bike.ui.componenti.ProponiPasskey(passkey, utenteCorrente.get().orElseThrow().getEmail()),
                kpi, corpo);
    }

    /** Interventi di oggi per persona, con la casella per segnarli come fatti. */
    /** Richieste di appuntamento da confermare e appuntamenti di oggi. */
    private Component appuntamenti(it.boff.bike.appuntamento.AppuntamentoService service, LocalDate oggi) {
        var daConfermare = service.daConfermare();
        var diOggi = service.tra(oggi, oggi);
        Div box = new Div(new com.vaadin.flow.component.html.H3("Appuntamenti"));
        box.addClassName("boff-card");
        if (!daConfermare.isEmpty()) {
            com.vaadin.flow.router.RouterLink link = new com.vaadin.flow.router.RouterLink(
                    daConfermare.size() == 1 ? "1 richiesta da confermare" : daConfermare.size() + " richieste da confermare",
                    AppuntamentiView.class);
            link.addClassName("boff-urgente");
            box.add(new Div(link));
        }
        if (diOggi.isEmpty()) {
            com.vaadin.flow.component.html.Paragraph p = new com.vaadin.flow.component.html.Paragraph("Nessun appuntamento oggi.");
            p.addClassName("boff-testo-secondario");
            box.add(p);
        }
        for (var a : diOggi) {
            Span riga = new Span(a.getOraInizio() + " " + a.getCliente().getNomeCompleto() + " · " + a.getTipo().getNome()
                    + " (" + a.getUtente().getNome() + ")");
            Div d = new Div(riga);
            d.addClassName("boff-voce-appuntamento");
            if (a.getStato() == it.boff.bike.appuntamento.StatoAppuntamento.RICHIESTO) d.addClassName("richiesto");
            box.add(d);
        }
        return box;
    }

    private Component oggiInOfficina(List<Assegnazione> oggi, LavoroService lavori, Disponibilita disp, LocalDate giorno) {
        Div card = new Div(new H3("Oggi in officina"));
        card.addClassName("boff-card");
        if (oggi.isEmpty()) {
            Paragraph p = new Paragraph("Nessun intervento pianificato per oggi.");
            p.addClassName("boff-testo-secondario");
            card.add(p);
        }
        Map<Utente, List<Assegnazione>> perPersona = new LinkedHashMap<>();
        for (Utente u : disp.personale()) {
            List<Assegnazione> sue = oggi.stream().filter(a -> a.getUtente().getId().equals(u.getId())).toList();
            if (!sue.isEmpty()) perPersona.put(u, sue);
        }
        perPersona.forEach((persona, elenco) -> {
            H4 nome = new H4(persona.getNomeCompleto());
            card.add(nome);
            for (Assegnazione a : elenco) {
                var riga = a.getRiga();
                Checkbox fatto = new Checkbox(riga.isCompletata());
                fatto.setAriaLabel("Fatto");
                fatto.addValueChangeListener(e -> {
                    if (!e.isFromClient()) return;
                    try {
                        lavori.segnaRiga(riga.getId(), e.getValue());
                        Avvisi.ok(e.getValue() ? "Intervento fatto" : "Intervento da rifare");
                    } catch (ErroreDominio ex) {
                        Avvisi.errore(ex.getMessage());
                        fatto.setValue(!e.getValue());
                    }
                });
                RouterLink bici = new RouterLink(riga.getLavoro().getBicicletta().getNome(), LavoriView.class,
                        LavoriView.parametri(riga.getLavoro().getId()));
                Span orario = new Span(a.getOraInizio() + "–" + a.getOraFine());
                orario.addClassName("boff-bici-nome-piccolo");
                Span descr = new Span(riga.getDescrizione());
                descr.addClassName("boff-testo-secondario");
                Div testi = new Div(new Div(orario, new Span(" "), bici), descr);
                Div voce = new Div(fatto, testi);
                voce.addClassName("boff-voce-oggi");
                card.add(voce);
            }
        });
        return card;
    }

    private static Component elencoLavori(String titolo, List<Lavoro> elenco, String vuoto) {
        Div card = new Div(new H3(titolo));
        card.addClassName("boff-card");
        if (elenco.isEmpty()) {
            Paragraph p = new Paragraph(vuoto);
            p.addClassName("boff-testo-secondario");
            card.add(p);
        }
        elenco.stream().limit(8).forEach(l -> {
            RouterLink link = new RouterLink(LavoriView.class, LavoriView.parametri(l.getId()));
            Span bici = new Span(l.getBicicletta().getNome());
            bici.addClassName("boff-bici-nome-piccolo");
            Span dettaglio = new Span(l.getBicicletta().getProprietario().getNomeCompleto()
                    + (l.getFinePrevista() != null ? " · prevista " + Formati.giorno(l.getFinePrevista()) : ""));
            dettaglio.addClassName("boff-testo-secondario");
            link.add(bici, dettaglio);
            link.addClassName("boff-voce-elenco");
            card.add(link);
        });
        if (elenco.size() > 8) {
            RouterLink tutti = new RouterLink("Vedi tutti (" + elenco.size() + ")", LavoriView.class);
            tutti.setQueryParameters(LavoriView.scheda(switch (titolo) {
                case "Nuovi annunci" -> "annunciati";
                case "Pronte da ritirare" -> "pronti";
                default -> "officina";
            }));
            card.add(tutti);
        }
        return card;
    }

    /** Contatore cliccabile: porta alla pagina con l'elenco corrispondente. */
    private static RouterLink indicatore(String valore, String etichetta,
                                         Class<? extends com.vaadin.flow.component.Component> pagina,
                                         com.vaadin.flow.router.QueryParameters parametri) {
        Span v = new Span(valore);
        v.addClassName("valore");
        Span e = new Span(etichetta);
        e.addClassName("etichetta");
        Span vai = new Span(pagina == CalendarioView.class ? "Apri il calendario →" : "Apri l'elenco →");
        vai.addClassName("vai");
        RouterLink card = new RouterLink(pagina);
        card.setQueryParameters(parametri);
        card.add(v, e, vai);
        card.addClassNames("boff-card", "boff-kpi-link");
        return card;
    }
}
