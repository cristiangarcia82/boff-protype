package it.boff.bike.notifiche;

import com.vaadin.flow.server.webpush.WebPush;
import com.vaadin.flow.server.webpush.WebPushException;
import com.vaadin.flow.server.webpush.WebPushKeys;
import com.vaadin.flow.server.webpush.WebPushMessage;
import com.vaadin.flow.server.webpush.WebPushSubscription;
import it.boff.bike.utente.Utente;
import it.boff.bike.utente.UtenteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Notifiche all'utente: push su tutti i dispositivi su cui le ha attivate; se non ne ha
 * (o l'invio fallisce ovunque) e il messaggio lo prevede, email di riserva.
 */
@Service
public class NotificheService {

    private static final Logger log = LoggerFactory.getLogger(NotificheService.class);

    private final IscrizionePushRepository iscrizioni;
    private final UtenteRepository utenti;
    private final WebPush webPush;
    private final EmailService email;

    public NotificheService(IscrizionePushRepository iscrizioni, UtenteRepository utenti, WebPush webPush,
                            EmailService email) {
        this.iscrizioni = iscrizioni;
        this.utenti = utenti;
        this.webPush = webPush;
        this.email = email;
    }

    public WebPush webPush() {
        return webPush;
    }

    /**
     * Servizi push dei browser. L'indirizzo dell'iscrizione arriva dal browser: senza questo
     * controllo un utente potrebbe far inviare richieste dal server verso indirizzi interni (SSRF).
     */
    static final List<String> SERVIZI_PUSH = List.of(
            "fcm.googleapis.com", "android.googleapis.com",            // Chrome, Edge su Android
            "updates.push.services.mozilla.com",                        // Firefox
            "web.push.apple.com", ".push.apple.com",                    // Safari, iPhone
            ".notify.windows.com");                                     // Edge su Windows

    static boolean endpointAmmesso(String endpoint) {
        try {
            java.net.URI u = java.net.URI.create(endpoint);
            String host = u.getHost();
            return "https".equals(u.getScheme()) && host != null && u.getPort() == -1 && u.getUserInfo() == null
                    && SERVIZI_PUSH.stream().anyMatch(s -> s.startsWith(".") ? host.endsWith(s) : host.equals(s));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Transactional
    public void iscrivi(Long utenteId, WebPushSubscription s) {
        if (s == null || s.endpoint() == null || s.keys() == null || !endpointAmmesso(s.endpoint())
                || s.endpoint().length() > 1000) {
            throw new it.boff.bike.common.ErroreDominio("Servizio di notifica del browser non riconosciuto");
        }
        Utente u = utenti.getReferenceById(utenteId);
        iscrizioni.findByEndpoint(s.endpoint()).ifPresentOrElse(
                i -> i.aggiorna(u, s.keys().p256dh(), s.keys().auth()),
                () -> iscrizioni.save(new IscrizionePush(u, s.endpoint(), s.keys().p256dh(), s.keys().auth())));
    }

    @Transactional
    public void disiscrivi(String endpoint) {
        iscrizioni.deleteByEndpoint(endpoint);
    }

    @Transactional(readOnly = true)
    public boolean haDispositivi(Long utenteId) {
        return !iscrizioni.findByUtenteId(utenteId).isEmpty();
    }

    /**
     * @param emailDiRiserva se true e nessuna push arriva, si invia un'email
     * @return true se almeno una push è stata consegnata
     */
    @Transactional
    public boolean notifica(Long utenteId, String titolo, String testo, boolean emailDiRiserva) {
        Utente u = utenti.findById(utenteId).orElse(null);
        if (u == null || !u.isAttivo()) return false;
        boolean consegnata = false;
        List<IscrizionePush> dispositivi = iscrizioni.findByUtenteId(utenteId);
        for (IscrizionePush i : dispositivi) {
            if (!endpointAmmesso(i.getEndpoint())) {
                iscrizioni.delete(i); // salvata prima del controllo: non si contatta
                continue;
            }
            try {
                webPush.sendNotification(new WebPushSubscription(i.getEndpoint(), new WebPushKeys(i.getP256dh(), i.getAuth())),
                        new WebPushMessage(titolo, testo));
                consegnata = true;
            } catch (WebPushException e) {
                if (e.getMessage() != null && e.getMessage().contains("404 or 410")) {
                    log.info("Dispositivo non più valido per l'utente {}: rimosso", utenteId);
                    iscrizioni.delete(i);
                } else {
                    log.warn("Notifica push all'utente {} non riuscita: {}", utenteId, e.getMessage());
                }
            }
        }
        if (!consegnata && emailDiRiserva) {
            email.invia(u.getEmail(), titolo, "Ciao " + u.getNome() + ",\n\n" + testo);
        }
        return consegnata;
    }
}
