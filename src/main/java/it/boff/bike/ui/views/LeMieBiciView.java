package it.boff.bike.ui.views;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import it.boff.bike.bicicletta.Bicicletta;
import it.boff.bike.bicicletta.BiciclettaService;
import it.boff.bike.bicicletta.DatiBicicletta;
import it.boff.bike.bicicletta.Tipologia;
import it.boff.bike.security.Ruoli;
import it.boff.bike.security.UtenteCorrente;
import jakarta.annotation.security.RolesAllowed;

import java.util.ArrayList;
import java.util.List;

/** Area del cliente: le sue biciclette. Pensata per il telefono. */
@Route("le-mie-bici")
@PageTitle("Le mie bici | Boff Bike")
@RolesAllowed(Ruoli.CLIENTE)
public class LeMieBiciView extends Div {

    private final BiciclettaService service;
    private final Long utenteId;
    private final Div elenco = new Div();

    public LeMieBiciView(BiciclettaService service, UtenteCorrente utenteCorrente) {
        this.service = service;
        this.utenteId = utenteCorrente.get().orElseThrow().getId();
        addClassNames("boff-pagina", "boff-pagina-cliente", "boff-colonna");

        Button aggiungi = new Button("Aggiungi bici", VaadinIcon.PLUS.create(), e -> apriNuova());
        aggiungi.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        aggiungi.addClickShortcut(Key.KEY_N, KeyModifier.ALT);

        Div intestazione = new Div(new H2("Le mie bici"), aggiungi);
        intestazione.addClassName("boff-intestazione");

        elenco.addClassName("boff-colonna");
        add(intestazione, elenco,
                new Segnaposto("Annuncia un lavoro", "Indica cosa va fatto prima di portare la bici in negozio.", "fase 2"));
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
        } else {
            bici.forEach(b -> elenco.add(scheda(b)));
        }
    }

    private Div scheda(Bicicletta b) {
        Image icona = new Image("icons/icon.png", "");
        icona.addClassName("boff-bici-icona");

        Span nome = new Span(b.getNome());
        nome.addClassName("boff-bici-nome");

        List<String> dettagli = new ArrayList<>();
        dettagli.add(b.getTipologia().getEtichetta());
        if (b.isElettrica() && b.getTipologia() != Tipologia.EBIKE) {
            dettagli.add("elettrica");
        }
        if (b.getTaglia() != null) dettagli.add("taglia " + b.getTaglia());
        if (b.getColore() != null) dettagli.add(b.getColore());
        if (b.getAnno() != null) dettagli.add(String.valueOf(b.getAnno()));
        Span info = new Span(String.join(" · ", dettagli));
        info.addClassName("boff-bici-info");

        Div testi = new Div(nome, info);
        testi.addClassName("boff-bici-testi");

        Div scheda = new Div(icona, testi, VaadinIcon.ANGLE_RIGHT.create());
        scheda.addClassNames("boff-card", "boff-bici");
        scheda.getElement().setAttribute("role", "button");
        scheda.getElement().setAttribute("tabindex", "0");
        scheda.getElement().setAttribute("aria-label", "Modifica " + b.getNome());
        scheda.addClickListener(e -> apriModifica(b));
        return scheda;
    }

    private void apriNuova() {
        new BiciclettaDialog(new DatiBicicletta(), true, dati -> {
            service.aggiungi(utenteId, dati);
            avvisa("Bici aggiunta");
            aggiorna();
        }, null).open();
    }

    private void apriModifica(Bicicletta b) {
        new BiciclettaDialog(DatiBicicletta.da(b), false, dati -> {
            service.modifica(utenteId, b.getId(), dati);
            avvisa("Modifiche salvate");
            aggiorna();
        }, () -> {
            service.rimuovi(utenteId, b.getId());
            avvisa("Bici rimossa");
            aggiorna();
        }).open();
    }

    private static void avvisa(String testo) {
        Notification n = Notification.show(testo, 2500, Notification.Position.BOTTOM_CENTER);
        n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }
}
