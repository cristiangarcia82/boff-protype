package it.boff.bike.ui.views;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import it.boff.bike.security.Ruoli;
import jakarta.annotation.security.RolesAllowed;

/** Bacheca del personale (titolare e collaboratore). */
@Route("bacheca")
@PageTitle("Bacheca | Boff Bike")
@RolesAllowed(Ruoli.STAFF)
public class BachecaView extends VerticalLayout {

    public BachecaView() {
        add(new H2("Bacheca"),
                new Segnaposto("Lavori in negozio", "Le bici consegnate e lo stato di ogni lavoro.", "fase 2"),
                new Segnaposto("Oggi in officina", "Cosa c'è da fare oggi, per te e per il collaboratore.", "fase 4"),
                new Segnaposto("Calendario", "Turni, ore di vendita e di officina, giorni liberi.", "fase 3"));
    }
}
