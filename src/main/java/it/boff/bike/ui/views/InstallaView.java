package it.boff.bike.ui.views;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.OrderedList;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/** Come installare l'app sulla schermata Home (necessario su iPhone per le notifiche). */
@Route(value = "installa", autoLayout = false)
@PageTitle("Installa l'app | Boff Bike")
@AnonymousAllowed
public class InstallaView extends Div {

    public InstallaView() {
        addClassNames("boff-pagina", "boff-pagina-cliente", "boff-colonna");
        add(new com.vaadin.flow.router.RouterLink("← Torna all'app", HomeView.class),
                new H2("Installa l'app"),
                new Paragraph("Boff Bike non si scarica dagli store: si aggiunge alla schermata Home dal browser, "
                        + "in pochi secondi. Così si apre come un'app e può mandarti le notifiche."),
                scheda("iPhone e iPad",
                        "Apri questa pagina con Safari.",
                        "Tocca il pulsante Condividi (il quadrato con la freccia in su).",
                        "Scegli \"Aggiungi alla schermata Home\" e conferma.",
                        "Apri Boff Bike dall'icona sulla Home e attiva le notifiche quando richiesto.",
                        "Serve iOS 16.4 o successivo: sulle versioni precedenti le notifiche non sono disponibili."),
                scheda("Android",
                        "Apri questa pagina con Chrome.",
                        "Tocca i tre puntini in alto a destra.",
                        "Scegli \"Installa app\" (o \"Aggiungi a schermata Home\")."),
                scheda("Mac",
                        "Safari (macOS Sonoma o successivo): menu File → \"Aggiungi al Dock\".",
                        "Chrome o Edge: icona di installazione nella barra degli indirizzi, oppure menu → \"Installa Boff Bike\".",
                        "L'app si apre in una finestra sua e le notifiche arrivano come quelle di macOS."));
    }

    private static Div scheda(String titolo, String... passi) {
        OrderedList ol = new OrderedList();
        for (String p : passi) ol.add(new ListItem(p));
        Div d = new Div(new H3(titolo), ol);
        d.addClassName("boff-card");
        return d;
    }
}
