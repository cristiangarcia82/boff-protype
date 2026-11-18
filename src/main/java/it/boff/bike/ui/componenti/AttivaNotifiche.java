package it.boff.bike.ui.componenti;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import it.boff.bike.notifiche.NotificheService;

/**
 * Riquadro "Attiva le notifiche": visibile solo se su questo dispositivo non sono attive.
 * Le notifiche push funzionano solo nell'app installata in produzione (HTTPS + service worker).
 */
public class AttivaNotifiche extends Div {

    private final NotificheService notifiche;
    private final Long utenteId;

    public AttivaNotifiche(NotificheService notifiche, Long utenteId, String perche) {
        this.notifiche = notifiche;
        this.utenteId = utenteId;
        addClassNames("boff-card", "boff-attiva-notifiche");
        setVisible(false);

        Button attiva = new Button("Attiva le notifiche", VaadinIcon.BELL.create(), e -> attiva());
        attiva.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Paragraph testo = new Paragraph(perche);
        Span suIphone = new Span("Su iPhone prima installa l'app: ");
        Paragraph aiuto = new Paragraph(suIphone, new Anchor("installa", "ecco come"));
        aiuto.addClassName("boff-testo-secondario");
        add(testo, attiva, aiuto);
    }

    @Override
    protected void onAttach(AttachEvent event) {
        UI ui = event.getUI();
        // Mostra il riquadro solo se il browser supporta le push e non c'è già un'iscrizione
        ui.getPage().executeJs("return ('serviceWorker' in navigator) && ('PushManager' in window) "
                        + "&& !!navigator.serviceWorker.controller")
                .then(Boolean.class, supportato -> {
                    if (Boolean.TRUE.equals(supportato)) {
                        notifiche.webPush().subscriptionExists(ui, esiste -> setVisible(!esiste));
                    }
                });
    }

    private void attiva() {
        UI ui = UI.getCurrent();
        notifiche.webPush().subscribe(ui, iscrizione -> {
            notifiche.iscrivi(utenteId, iscrizione);
            setVisible(false);
            Avvisi.ok("Notifiche attivate su questo dispositivo");
        });
    }
}
