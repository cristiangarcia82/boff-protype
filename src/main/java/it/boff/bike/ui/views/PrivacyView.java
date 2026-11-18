package it.boff.bike.ui.views;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * Informativa privacy. ATTENZIONE: testo di BOZZA, da far verificare e completare
 * (titolare del trattamento, indirizzo, contatti) prima della messa online.
 */
@Route(value = "privacy", autoLayout = false)
@PageTitle("Informativa privacy | Boff Bike")
@AnonymousAllowed
public class PrivacyView extends Div {

    public PrivacyView() {
        addClassNames("boff-pagina", "boff-pagina-cliente");
        add(new H2("Informativa privacy"),
                new Paragraph("Bozza da completare a cura del negozio prima della pubblicazione."),
                new H3("Quali dati raccogliamo"),
                new Paragraph("Nome, cognome, email, telefono e i dati delle biciclette che registri, "
                        + "insieme ai lavori richiesti e svolti."),
                new H3("Perché"),
                new Paragraph("Per gestire l'assistenza alle tue biciclette, organizzare i lavori di officina "
                        + "e avvisarti quando la bici è pronta (base giuridica: esecuzione del servizio richiesto)."),
                new H3("Per quanto tempo"),
                new Paragraph("Finché il tuo account è attivo; puoi chiederne la cancellazione in qualsiasi momento."),
                new H3("I tuoi diritti"),
                new Paragraph("Accesso, rettifica, cancellazione, limitazione e portabilità dei dati, "
                        + "scrivendo al negozio. I dati non sono ceduti a terzi né usati per pubblicità."));
    }
}
