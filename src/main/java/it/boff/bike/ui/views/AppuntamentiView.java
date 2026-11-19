package it.boff.bike.ui.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
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

import java.time.LocalDate;
import java.util.List;

/** Appuntamenti di consulenza: richieste da confermare e prossimi appuntamenti. */
@Route("appuntamenti")
@PageTitle("Appuntamenti | Boff Bike")
@RolesAllowed(Ruoli.STAFF)
public class AppuntamentiView extends Div {

    private final AppuntamentoService service;
    private final CalendarioService calendario;
    private final List<Utente> personale;
    private final Div daConfermare = new Div();
    private final Div prossimi = new Div();

    public AppuntamentiView(AppuntamentoService service, CalendarioService calendario, UtenteService utenti) {
        this.service = service;
        this.calendario = calendario;
        this.personale = utenti.personale().stream().filter(Utente::isAttivo).toList();
        addClassNames("boff-pagina", "boff-colonna");

        Button nuovo = new Button("Nuovo appuntamento", VaadinIcon.PLUS.create(), e ->
                new NuovoAppuntamentoDialog(service, utenti, personale, calendario.oggi(), this::aggiorna).open());
        nuovo.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Div intestazione = new Div(new H2("Appuntamenti"), nuovo);
        intestazione.addClassName("boff-intestazione");
        Paragraph spiegazione = new Paragraph("Consulenze nell'orario di lavoro (tolti i turni di vendita). Le richieste dei clienti tengono già "
                + "l'orario: confermale, oppure spostale o annullale (il cliente riceve un avviso).");
        spiegazione.addClassName("boff-testo-secondario");

        Div colonne = new Div(scheda("Da confermare", daConfermare), scheda("Prossimi 14 giorni", prossimi));
        colonne.addClassName("boff-due-colonne");
        add(intestazione, spiegazione, colonne);
        aggiorna();
    }

    private static Div scheda(String titolo, Div contenuto) {
        Div d = new Div(new H3(titolo), contenuto);
        d.addClassName("boff-card");
        return d;
    }

    private void aggiorna() {
        LocalDate oggi = calendario.oggi();
        riempi(daConfermare, service.daConfermare(), "Nessuna richiesta da confermare.");
        riempi(prossimi, service.tra(oggi, oggi.plusDays(13)).stream()
                .filter(a -> a.getStato() == StatoAppuntamento.CONFERMATO).toList(), "Nessun appuntamento confermato.");
    }

    private void riempi(Div box, List<Appuntamento> elenco, String vuoto) {
        box.removeAll();
        if (elenco.isEmpty()) {
            Paragraph p = new Paragraph(vuoto);
            p.addClassName("boff-testo-secondario");
            box.add(p);
        }
        elenco.forEach(a -> box.add(voce(a)));
    }

    private Component voce(Appuntamento a) {
        Span quando = new Span(Formati.giorno(a.getData()) + " " + a.getOraInizio());
        quando.addClassName("boff-voce-quando");
        Span chi = new Span(a.getCliente().getNomeCompleto() + " · " + a.getTipo().getNome());
        Span con = new Span("con " + a.getUtente().getNome());
        con.addClassName("boff-testo-secondario");
        Div testo = new Div(quando, new Div(chi), new Div(con));
        Div voce = new Div(testo);
        voce.addClassNames("boff-voce-appuntamento");
        if (a.getStato() == StatoAppuntamento.RICHIESTO) voce.addClassName("richiesto");
        voce.getElement().setAttribute("role", "button");
        voce.getElement().setAttribute("tabindex", "0");
        voce.addClickListener(e -> new AppuntamentoDialog(service, personale, calendario.oggi(), a.getId(), this::aggiorna).open());
        return voce;
    }
}
