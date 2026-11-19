package it.boff.bike.ui.views;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import it.boff.bike.lavoro.Lavoro;
import it.boff.bike.lavoro.LavoroService;
import it.boff.bike.lavoro.StatoLavoro;
import it.boff.bike.security.Ruoli;
import it.boff.bike.ui.componenti.BadgeStato;
import it.boff.bike.ui.componenti.Formati;
import it.boff.bike.utente.Utente;
import it.boff.bike.utente.UtenteService;
import jakarta.annotation.security.RolesAllowed;

import java.util.EnumSet;
import java.util.List;

/** Risultati della ricerca rapida (barra in alto): clienti e lavori. */
@Route("cerca")
@PageTitle("Cerca | Boff Bike")
@RolesAllowed(Ruoli.STAFF)
public class CercaView extends Div implements BeforeEnterObserver {

    private final UtenteService utenti;
    private final LavoroService lavori;

    public CercaView(UtenteService utenti, LavoroService lavori) {
        this.utenti = utenti;
        this.lavori = lavori;
        addClassName("boff-pagina");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();
        String q = event.getLocation().getQueryParameters().getSingleParameter("q").orElse("").trim();
        add(new H2(q.isEmpty() ? "Cerca" : "Risultati per \"" + q + "\""));
        if (q.length() < 2) {
            add(new Paragraph("Scrivi almeno 2 caratteri nella barra di ricerca in alto."));
            return;
        }
        List<Utente> clienti = utenti.cercaClienti(q).stream().limit(20).toList();
        List<Lavoro> trovati = lavori.cerca(EnumSet.allOf(StatoLavoro.class), q).stream().limit(30).toList();

        Div schedaClienti = new Div(new H3("Clienti (" + clienti.size() + ")"));
        schedaClienti.addClassName("boff-card");
        if (clienti.isEmpty()) schedaClienti.add(new Paragraph("Nessun cliente."));
        clienti.forEach(c -> {
            RouterLink link = new RouterLink(ClientiView.class, new RouteParameters("id", String.valueOf(c.getId())));
            Span nome = new Span(c.getNomeCompleto());
            nome.addClassName("boff-bici-nome-piccolo");
            Span dettagli = new Span((c.getTelefono() == null ? "" : c.getTelefono())
                    + (c.usaApp() ? " · " + c.getEmail() : " · senza app"));
            dettagli.addClassName("boff-testo-secondario");
            link.add(nome, dettagli);
            link.addClassName("boff-voce-elenco");
            schedaClienti.add(link);
        });

        Div schedaLavori = new Div(new H3("Lavori (" + trovati.size() + ")"));
        schedaLavori.addClassName("boff-card");
        if (trovati.isEmpty()) schedaLavori.add(new Paragraph("Nessun lavoro."));
        trovati.forEach(l -> {
            RouterLink link = new RouterLink(LavoriView.class, LavoriView.parametri(l.getId()));
            Span bici = new Span(l.getBicicletta().getNome() + " · " + l.getBicicletta().getProprietario().getNomeCompleto());
            bici.addClassName("boff-bici-nome-piccolo");
            Span quando = new Span("Annunciato " + Formati.data(l.getAnnunciatoIl()) + " ");
            quando.addClassName("boff-testo-secondario");
            link.add(bici, new Div(quando, new BadgeStato(l.getStato())));
            link.addClassName("boff-voce-elenco");
            schedaLavori.add(link);
        });

        Div corpo = new Div(schedaClienti, schedaLavori);
        corpo.addClassName("boff-due-colonne");
        add(corpo);
    }
}
