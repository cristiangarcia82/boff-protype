package it.boff.bike.config;

import it.boff.bike.calendario.CalendarioService;
import it.boff.bike.calendario.NuovaFascia;
import it.boff.bike.calendario.TipoFascia;
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

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;

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
    private final CalendarioService calendario;

    public DatiIniziali(UtenteRepository utenti, PasswordEncoder passwordEncoder, BoffProperties properties,
                        CalendarioService calendario) {
        this.calendario = calendario;
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
        Utente mario = creaSeAssente("titolare@demo.it", "Mario", "Rossi", Ruolo.STAFF, true);
        Utente luca = creaSeAssente("collaboratore@demo.it", "Luca", "Bianchi", Ruolo.STAFF, false);
        orariDemo(mario, luca);
        creaSeAssente("cliente@demo.it", "Giulia", "Verdi", Ruolo.CLIENTE, false);
        creaSeAssente("paolo.ferri@demo.it", "Paolo", "Ferri", Ruolo.CLIENTE, false);
        creaSeAssente("sara.conti@demo.it", "Sara", "Conti", Ruolo.CLIENTE, false);
        log.warn("Utenti demo attivi (password '{}'). Non usare boff.demo=true in produzione!", PASSWORD_DEMO);
    }

    /**
     * Orario tipico di un negozio di bici (chiuso domenica e lunedì mattina):
     * il titolare la mattina in officina e il pomeriggio al banco, il collaboratore il contrario.
     * Il sabato si vende soprattutto.
     */
    private void orariDemo(Utente mario, Utente luca) {
        if (calendario.orarioDi(mario.getId()).isEmpty()) {
            List<NuovaFascia> f = new ArrayList<>();
            for (DayOfWeek g : List.of(TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)) {
                f.add(fascia(g, "09:00", "12:30", TipoFascia.MANUTENZIONE));
                f.add(fascia(g, "15:00", "19:30", TipoFascia.VENDITA));
            }
            f.add(fascia(SATURDAY, "09:00", "12:30", TipoFascia.VENDITA));
            f.add(fascia(SATURDAY, "15:00", "19:00", TipoFascia.VENDITA));
            calendario.salvaOrario(mario.getId(), f);
        }
        if (calendario.orarioDi(luca.getId()).isEmpty()) {
            List<NuovaFascia> f = new ArrayList<>();
            f.add(fascia(MONDAY, "15:00", "19:30", TipoFascia.MANUTENZIONE));
            for (DayOfWeek g : List.of(TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)) {
                f.add(fascia(g, "09:00", "12:30", TipoFascia.VENDITA));
                f.add(fascia(g, "15:00", "19:30", TipoFascia.MANUTENZIONE));
            }
            f.add(fascia(SATURDAY, "09:00", "12:30", TipoFascia.MANUTENZIONE));
            calendario.salvaOrario(luca.getId(), f);
        }
    }

    private static NuovaFascia fascia(DayOfWeek g, String da, String a, TipoFascia tipo) {
        return new NuovaFascia(g, LocalTime.parse(da), LocalTime.parse(a), tipo);
    }

    private Utente creaSeAssente(String email, String nome, String cognome, Ruolo ruolo, boolean titolare) {
        return utenti.findByEmail(email).orElseGet(() -> {
            Utente u = new Utente(email, passwordEncoder.encode(PASSWORD_DEMO), nome, cognome, ruolo, titolare);
            u.setTelefono("+39 333 000 00" + (utenti.count() % 100));
            return utenti.save(u);
        });
    }
}
