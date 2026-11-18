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
    private final String perche;
    private final Button attiva;
    private final Paragraph testo;
    private final Paragraph aiuto;

    public AttivaNotifiche(NotificheService notifiche, Long utenteId, String perche) {
        this.notifiche = notifiche;
        this.utenteId = utenteId;
        this.perche = perche;
        addClassNames("boff-card", "boff-attiva-notifiche");
        setVisible(false);

        attiva = new Button("Attiva le notifiche", VaadinIcon.BELL.create(), e -> attiva());
        attiva.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        testo = new Paragraph(perche);
        Span suIphone = new Span("Su iPhone prima installa l'app: ");
        aiuto = new Paragraph(suIphone, new Anchor("installa", "ecco come"));
        aiuto.addClassName("boff-testo-secondario");
        add(testo, attiva, aiuto);
    }

    /** Stato del browser: "ok", "installa" (push non disponibili finché l'app non è installata), altro = nascondi. */
    private static final String JS_STATO = "const standalone = window.matchMedia('(display-mode: standalone)').matches"
            + " || navigator.standalone === true;"
            + "if (('serviceWorker' in navigator) && ('PushManager' in window)) {"
            + "  return navigator.serviceWorker.controller ? 'ok' : 'sviluppo'; }"
            + "return standalone ? 'nonsupportate' : 'installa';";

    @Override
    protected void onAttach(AttachEvent event) {
        UI ui = event.getUI();
        // - push supportate e non ancora attive: pulsante "Attiva"
        // - non supportate e app non installata (es. iPhone con Safari): invito a installarla
        // - già attive, o sviluppo senza service worker: nessun riquadro
        ui.getPage().executeJs(JS_STATO).then(String.class, stato -> {
            switch (stato) {
                case "ok" -> notifiche.webPush().subscriptionExists(ui, esiste -> setVisible(!esiste));
                case "installa" -> {
                    testo.setText(perche + " Per riceverle aggiungi l'app alla schermata Home.");
                    attiva.setVisible(false);
                    aiuto.removeAll();
                    aiuto.add(new Anchor("installa", "Come installare l'app"));
                    setVisible(true);
                }
                default -> setVisible(false);
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
