package it.boff.bike.config;

import it.boff.bike.bicicletta.Bicicletta;
import it.boff.bike.bicicletta.BiciclettaService;
import it.boff.bike.bicicletta.DatiBicicletta;
import it.boff.bike.bicicletta.Tipologia;
import it.boff.bike.calendario.CalendarioService;
import it.boff.bike.catalogo.CatalogoService;
import it.boff.bike.lavoro.Lavoro;
import it.boff.bike.lavoro.LavoroService;
import it.boff.bike.lavoro.StatoLavoro;
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
import java.time.LocalDate;
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
    private final BiciclettaService biciclette;
    private final LavoroService lavori;
    private final CatalogoService catalogo;
    private final it.boff.bike.appuntamento.AppuntamentoService appuntamenti;

    public DatiIniziali(UtenteRepository utenti, PasswordEncoder passwordEncoder, BoffProperties properties,
                        CalendarioService calendario, BiciclettaService biciclette, LavoroService lavori,
                        CatalogoService catalogo, it.boff.bike.appuntamento.AppuntamentoService appuntamenti) {
        this.appuntamenti = appuntamenti;
        this.calendario = calendario;
        this.biciclette = biciclette;
        this.lavori = lavori;
        this.catalogo = catalogo;
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
        Utente giulia = creaSeAssente("cliente@demo.it", "Giulia", "Verdi", Ruolo.CLIENTE, false);
        Utente paolo = creaSeAssente("paolo.ferri@demo.it", "Paolo", "Ferri", Ruolo.CLIENTE, false);
        Utente sara = creaSeAssente("sara.conti@demo.it", "Sara", "Conti", Ruolo.CLIENTE, false);
        lavoriDemo(giulia, paolo, sara);
        appuntamentiDemo(giulia, paolo);
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

    /** Qualche bici e lavoro di esempio, solo se il database non ha ancora lavori. */
    private void lavoriDemo(Utente giulia, Utente paolo, Utente sara) {
        if (lavori.conta(java.util.EnumSet.allOf(StatoLavoro.class)) > 0
                || !biciclette.elencoDi(giulia.getId()).isEmpty()) {
            return;
        }
        Bicicletta impulso = biciclette.aggiungi(giulia.getId(), DatiBicicletta.di("Bianchi", "Impulso", Tipologia.STRADA));
        Bicicletta trail = biciclette.aggiungi(paolo.getId(), DatiBicicletta.di("Cannondale", "Trail 5", Tipologia.MTB));
        Bicicletta haibike = biciclette.aggiungi(sara.getId(), DatiBicicletta.di("Haibike", "Trekking 6", Tipologia.EBIKE));
        Bicicletta graziella = biciclette.aggiungi(sara.getId(), DatiBicicletta.di("Atala", "Graziella", Tipologia.CITTA));

        lavori.annuncia(giulia.getId(), impulso.getId(), tipi("Regolazione cambio", "Sostituzione catena"),
                "Il cambio salta sul pignone più grande");
        Lavoro mtb = lavori.creaAlBanco(trail.getId(), tipi("Tagliando completo (smontaggio, pulizia, regolazioni)",
                "Spurgo freni idraulici"), "Gara domenica prossima");
        lavori.impostaDettagli(mtb.getId(), true, null, null);
        Lavoro ebike = lavori.creaAlBanco(haibike.getId(), tipi("Diagnosi e aggiornamento e-bike", "Regolazione freni"), null);
        lavori.impostaDettagli(ebike.getId(), false, calendario.oggi().plusDays(4), null);
        lavori.creaAlBanco(graziella.getId(), tipi("Riparazione foratura", "Lavaggio"), null);
    }

    /** Un appuntamento da confermare (richiesto dall'app) e uno confermato, nei prossimi giorni. */
    private void appuntamentiDemo(Utente giulia, Utente paolo) {
        LocalDate oggi = calendario.oggi();
        if (!appuntamenti.tra(oggi.minusDays(30), oggi.plusDays(60)).isEmpty()) return;
        var tipi = appuntamenti.tipiAttivi();
        if (tipi.size() < 3) return;
        var liberi = appuntamenti.orariLiberi(tipi.get(0).getId(), oggi.plusDays(1), oggi.plusDays(14));
        liberi.entrySet().stream().findFirst().ifPresent(e -> appuntamenti.richiedi(paolo.getId(), tipi.get(0).getId(),
                e.getKey(), e.getValue().get(0), null, "Vorrei passare a una gravel, budget 2.000 €"));
        var liberi2 = appuntamenti.orariLiberi(tipi.get(2).getId(), oggi.plusDays(2), oggi.plusDays(14));
        liberi2.entrySet().stream().skip(1).findFirst().ifPresent(e -> appuntamenti.crea(giulia.getId(), tipi.get(2).getId(),
                null, e.getKey(), e.getValue().get(e.getValue().size() / 2), "Porta le scarpe con le tacchette"));
    }

    private List<Long> tipi(String... nomi) {
        List<String> elenco = List.of(nomi);
        return catalogo.attivi().stream().filter(t -> elenco.contains(t.getNome())).map(t -> t.getId()).toList();
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
