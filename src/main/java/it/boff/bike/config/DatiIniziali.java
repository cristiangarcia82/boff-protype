package it.boff.bike.config;

import it.boff.bike.utente.Ruolo;
import it.boff.bike.utente.Utente;
import it.boff.bike.utente.UtenteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * All'avvio crea l'account del titolare (se configurato e non ancora presente)
 * e, solo se {@code boff.demo=true}, alcuni utenti di prova.
 */
@Component
public class DatiIniziali implements ApplicationRunner {

    static final String PASSWORD_DEMO = "demo1234";

    private static final Logger log = LoggerFactory.getLogger(DatiIniziali.class);

    private final UtenteRepository utenti;
    private final PasswordEncoder passwordEncoder;
    private final BoffProperties properties;

    public DatiIniziali(UtenteRepository utenti, PasswordEncoder passwordEncoder, BoffProperties properties) {
        this.utenti = utenti;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        creaTitolare();
        if (properties.demo()) {
            creaUtentiDemo();
        }
    }

    private void creaTitolare() {
        if (utenti.existsByTitolareTrue()) {
            return;
        }
        BoffProperties.Titolare t = properties.titolare();
        if (!StringUtils.hasText(t.email()) || !StringUtils.hasText(t.password())) {
            log.warn("Nessun titolare presente: impostare BOFF_TITOLARE_EMAIL e BOFF_TITOLARE_PASSWORD per crearlo.");
            return;
        }
        utenti.save(new Utente(t.email(), passwordEncoder.encode(t.password()),
                t.nome(), t.cognome(), Ruolo.STAFF, true));
        log.info("Creato l'account del titolare {}", Utente.normalizzaEmail(t.email()));
    }

    private void creaUtentiDemo() {
        creaSeAssente("titolare@demo.it", "Mario", "Rossi", Ruolo.STAFF, true);
        creaSeAssente("collaboratore@demo.it", "Luca", "Bianchi", Ruolo.STAFF, false);
        creaSeAssente("cliente@demo.it", "Giulia", "Verdi", Ruolo.CLIENTE, false);
        creaSeAssente("paolo.ferri@demo.it", "Paolo", "Ferri", Ruolo.CLIENTE, false);
        creaSeAssente("sara.conti@demo.it", "Sara", "Conti", Ruolo.CLIENTE, false);
        log.warn("Utenti demo attivi (password '{}'). Non usare boff.demo=true in produzione!", PASSWORD_DEMO);
    }

    private Utente creaSeAssente(String email, String nome, String cognome, Ruolo ruolo, boolean titolare) {
        return utenti.findByEmail(email).orElseGet(() -> {
            Utente u = new Utente(email, passwordEncoder.encode(PASSWORD_DEMO), nome, cognome, ruolo, titolare);
            u.setTelefono("+39 333 000 00" + (utenti.count() % 100));
            return utenti.save(u);
        });
    }
}
