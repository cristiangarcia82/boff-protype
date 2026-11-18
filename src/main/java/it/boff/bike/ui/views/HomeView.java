package it.boff.bike.ui.views;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import it.boff.bike.security.UtenteCorrente;
import jakarta.annotation.security.PermitAll;

/** Pagina iniziale: smista l'utente sulla vista adatta al suo ruolo. */
@Route("")
@PermitAll
public class HomeView extends Div implements BeforeEnterObserver {

    private final UtenteCorrente utenteCorrente;

    public HomeView(UtenteCorrente utenteCorrente) {
        this.utenteCorrente = utenteCorrente;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        event.forwardTo(utenteCorrente.isStaff() ? BachecaView.class : LeMieBiciView.class);
    }
}
