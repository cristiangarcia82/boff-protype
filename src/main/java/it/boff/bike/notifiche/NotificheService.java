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

    @Transactional
    public void iscrivi(Long utenteId, WebPushSubscription s) {
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
