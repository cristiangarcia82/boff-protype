package it.boff.bike.ui.views;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import it.boff.bike.security.Ruoli;
import jakarta.annotation.security.RolesAllowed;

/** Area del cliente: pensata per il telefono, resta una colonna centrata anche su schermi grandi. */
@Route("le-mie-bici")
@PageTitle("Le mie bici | Boff Bike")
@RolesAllowed(Ruoli.CLIENTE)
public class LeMieBiciView extends Div {

    public LeMieBiciView() {
        addClassNames("boff-pagina", "boff-pagina-cliente", "boff-colonna");
        add(new H2("Le mie bici"),
                new Segnaposto("Le tue biciclette", "Aggiungi le tue bici con marca, modello e foto.", "fase 1"),
                new Segnaposto("Annuncia un lavoro", "Indica cosa va fatto prima di portare la bici in negozio.", "fase 2"));
    }
}
