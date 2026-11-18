package it.boff.bike.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Impostazioni comuni a tutte le schermate:
 * <ul>
 *   <li>lingua italiana per date, numeri e componenti (indipendente dal browser);</li>
 *   <li>errori imprevisti durante un'azione: registrati nel log e mostrati
 *       all'utente con un messaggio comprensibile invece dello stack trace.</li>
 * </ul>
 */
@Component
public class UiConfig implements VaadinServiceInitListener {

    public static final Locale ITALIANO = Locale.ITALY;

    private static final Logger log = LoggerFactory.getLogger(UiConfig.class);

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addUIInitListener(e -> e.getUI().setLocale(ITALIANO));
        event.getSource().addSessionInitListener(e -> e.getSession().setErrorHandler(errore -> {
            Throwable causa = errore.getThrowable();
            log.error("Errore non gestito nell'interfaccia", causa);
            UI ui = UI.getCurrent();
            if (ui != null) {
                ui.access(() -> {
                    Notification n = Notification.show(messaggioPer(causa), 5000, Notification.Position.MIDDLE);
                    n.addThemeVariants(NotificationVariant.LUMO_ERROR);
                });
            }
        }));
    }

    /** Messaggio per l'utente: per gli errori "di dominio" (IllegalArgument/State) usa il testo dell'eccezione. */
    static String messaggioPer(Throwable t) {
        Throwable c = t;
        while (c != null) {
            if ((c instanceof IllegalArgumentException || c instanceof IllegalStateException
                    || c instanceof it.boff.bike.common.ErroreDominio) && c.getMessage() != null) {
                return c.getMessage();
            }
            c = c.getCause();
        }
        return "Si è verificato un errore imprevisto. Riprova; se il problema persiste avvisa il negozio.";
    }
}
