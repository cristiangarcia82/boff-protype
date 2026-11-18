package it.boff.bike.ui.views;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import it.boff.bike.security.Ruoli;
import jakarta.annotation.security.RolesAllowed;

/** Configurazione riservata al titolare. */
@Route("impostazioni")
@PageTitle("Impostazioni | Boff Bike")
@RolesAllowed(Ruoli.TITOLARE)
public class ImpostazioniView extends VerticalLayout {

    public ImpostazioniView() {
        add(new H2("Impostazioni"),
                new Segnaposto("Utenti", "Gestione degli account di collaboratore e clienti.", "fase 1"),
                new Segnaposto("Catalogo interventi", "Tipi di intervento con il loro tempo standard.", "fase 1"),
                new Segnaposto("Orari del negozio", "Orario settimanale, festività e chiusure.", "fase 3"));
    }
}
