package it.boff.bike.ui.views;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import it.boff.bike.bicicletta.Bicicletta;
import it.boff.bike.bicicletta.BiciclettaService;
import it.boff.bike.bicicletta.DatiBicicletta;
import it.boff.bike.lavoro.Lavoro;
import it.boff.bike.lavoro.LavoroService;
import it.boff.bike.security.Ruoli;
import it.boff.bike.security.UtenteCorrente;
import it.boff.bike.notifiche.NotificheService;
import it.boff.bike.ui.componenti.AttivaNotifiche;
import it.boff.bike.ui.componenti.Avvisi;
import it.boff.bike.ui.componenti.BadgeStato;
import it.boff.bike.ui.componenti.SchedaBici;
import jakarta.annotation.security.RolesAllowed;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Area del cliente: le sue biciclette con lo stato dell'eventuale lavoro in corso. */
@Route("le-mie-bici")
@PageTitle("Le mie bici | Boff Bike")
@RolesAllowed(Ruoli.CLIENTE)
public class LeMieBiciView extends Div {

    private final BiciclettaService service;
    private final LavoroService lavori;
    private final Long utenteId;
    private final Div elenco = new Div();

    public LeMieBiciView(BiciclettaService service, LavoroService lavori, UtenteCorrente utenteCorrente,
                         NotificheService notifiche) {
        this.service = service;
        this.lavori = lavori;
        this.utenteId = utenteCorrente.get().orElseThrow().getId();
        addClassNames("boff-pagina", "boff-pagina-cliente", "boff-colonna");

        Button aggiungi = new Button("Aggiungi bici", VaadinIcon.PLUS.create(), e -> apriNuova());
        aggiungi.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        aggiungi.addClickShortcut(Key.KEY_N, KeyModifier.ALT);

        Div intestazione = new Div(new H2("Le mie bici"), aggiungi);
        intestazione.addClassName("boff-intestazione");

        elenco.addClassName("boff-colonna");
        add(intestazione, new AttivaNotifiche(notifiche, utenteId,
                "Ti avvisiamo quando la bici è arrivata, quando prevediamo di finirla e quando è pronta."), elenco);
        aggiorna();
    }

    private void aggiorna() {
        elenco.removeAll();
        List<Bicicletta> bici = service.elencoDi(utenteId);
        if (bici.isEmpty()) {
            Div vuoto = new Div(new Paragraph("Non hai ancora registrato nessuna bici."),
                    new Paragraph("Aggiungila ora: quando la porterai in negozio potrai indicarci cosa fare."));
            vuoto.addClassNames("boff-card", "boff-vuoto");
            elenco.add(vuoto);
            return;
        }
        Map<Long, Lavoro> aperti = lavori.lavoriDelCliente(utenteId).stream()
                .filter(l -> l.getStato().isAperto())
                .collect(Collectors.toMap(l -> l.getBicicletta().getId(), Function.identity(), (a, b) -> a));
        bici.forEach(b -> {
            Lavoro l = aperti.get(b.getId());
            com.vaadin.flow.component.Component badge = null;
            if (l != null && l.haApprovazioniInAttesa()) {
                com.vaadin.flow.component.html.Span ok = new com.vaadin.flow.component.html.Span("Serve il tuo ok");
                ok.getElement().getThemeList().add("badge");
                ok.getElement().getThemeList().add("primary");
                badge = ok;
            } else if (l != null) {
                badge = new BadgeStato(l.getStato());
            }
            elenco.add(new SchedaBici(b, badge, () -> apri(b)));
        });
        Paragraph suggerimento = new Paragraph("Tocca una bici per annunciare un lavoro o vedere lo storico.");
        suggerimento.addClassName("boff-testo-secondario");
        elenco.add(suggerimento);
    }

    private void apri(Bicicletta b) {
        getUI().ifPresent(ui -> ui.navigate(BiciView.class, new RouteParameters("id", String.valueOf(b.getId()))));
    }

    private void apriNuova() {
        new BiciclettaDialog(new DatiBicicletta(), true, dati -> {
            service.aggiungi(utenteId, dati);
            Avvisi.ok("Bici aggiunta");
            aggiorna();
        }, null).open();
    }
}
