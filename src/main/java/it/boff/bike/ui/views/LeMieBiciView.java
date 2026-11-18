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
import it.boff.bike.bicicletta.Bicicletta;
import it.boff.bike.bicicletta.BiciclettaService;
import it.boff.bike.bicicletta.DatiBicicletta;
import it.boff.bike.security.Ruoli;
import it.boff.bike.security.UtenteCorrente;
import it.boff.bike.ui.componenti.Avvisi;
import it.boff.bike.ui.componenti.SchedaBici;
import jakarta.annotation.security.RolesAllowed;

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
            bici.forEach(b -> elenco.add(new SchedaBici(b, () -> apriModifica(b))));
        }
    }

    private void apriNuova() {
        new BiciclettaDialog(new DatiBicicletta(), true, dati -> {
            service.aggiungi(utenteId, dati);
            Avvisi.ok("Bici aggiunta");
            aggiorna();
        }, null).open();
    }

    private void apriModifica(Bicicletta b) {
        new BiciclettaDialog(DatiBicicletta.da(b), false, dati -> {
            service.modifica(utenteId, b.getId(), dati);
            Avvisi.ok("Modifiche salvate");
            aggiorna();
        }, () -> {
            service.rimuovi(utenteId, b.getId());
            Avvisi.ok("Bici rimossa");
            aggiorna();
        }).open();
    }

}
