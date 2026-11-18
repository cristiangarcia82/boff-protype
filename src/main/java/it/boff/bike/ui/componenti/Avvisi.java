package it.boff.bike.ui.componenti;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;

/** Notifiche brevi e coerenti in tutta l'app. */
public final class Avvisi {

    private Avvisi() {
    }

    public static void ok(String testo) {
        Notification n = Notification.show(testo, 2500, Notification.Position.BOTTOM_CENTER);
        n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    public static void errore(String testo) {
        Notification n = Notification.show(testo, 5000, Notification.Position.MIDDLE);
        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
