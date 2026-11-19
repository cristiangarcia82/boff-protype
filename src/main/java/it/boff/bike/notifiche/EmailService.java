package it.boff.bike.notifiche;

import it.boff.bike.config.BoffProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Invio email. Se il server SMTP non è configurato (sviluppo), il messaggio viene solo
 * scritto nel log: così i link di recupero password sono utilizzabili anche in locale.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final BoffProperties properties;

    public EmailService(ObjectProvider<JavaMailSender> mailSender, BoffProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    public boolean isConfigurata() {
        return mailSender.getIfAvailable() != null;
    }

    /** @return true se l'email è stata consegnata al server SMTP */
    public boolean invia(String a, String oggetto, String testo) {
        if (a == null || a.isBlank()) {
            return false; // cliente senza email: lo avvisa il negozio per telefono
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            if (properties.demo()) {
                // solo in sviluppo (dati demo): il testo, con eventuali link, finisce nel log
                log.info("[EMAIL NON CONFIGURATA] A: {} | Oggetto: {}\n{}", a, oggetto, testo);
            } else {
                // in produzione i link di recupero password non devono mai finire nei log
                log.warn("Email \"{}\" non inviata: server SMTP non configurato (SPRING_MAIL_HOST)", oggetto);
            }
            return false;
        }
        try {
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(properties.nomeNegozio() + " <" + properties.emailMittente() + ">");
            m.setTo(a);
            m.setSubject(oggetto);
            m.setText(testo + "\n\n— " + properties.nomeNegozio() + "\n" + properties.urlPubblico());
            sender.send(m);
            return true;
        } catch (RuntimeException e) {
            log.warn("Invio email a {} non riuscito: {}", a, e.getMessage());
            return false;
        }
    }
}
