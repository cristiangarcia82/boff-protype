package it.boff.bike.accesso;

import it.boff.bike.config.BoffProperties;
import it.boff.bike.notifiche.EmailService;
import it.boff.bike.utente.Utente;
import it.boff.bike.utente.UtenteRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Accesso senza password: un codice di 6 cifre inviato via email, da digitare nell'app.
 * <ul>
 *   <li>Un codice si digita dentro l'app, a differenza di un link: sull'iPhone l'app installata e
 *       Safari non condividono l'accesso, e un link si aprirebbe in Safari.</li>
 *   <li>Valido {@link #VALIDITA}, al massimo {@value #MAX_TENTATIVI} tentativi, un solo codice valido
 *       per utente (il nuovo annulla il precedente), al massimo {@value #MAX_INVII_ORA} invii l'ora.</li>
 *   <li>Nel database solo l'hash. La risposta all'utente è sempre la stessa, che l'email esista o no.</li>
 * </ul>
 * I tentativi sbagliati contano anche per il blocco generale dell'accesso (5 errori = 15 minuti).
 */
@Service
@Transactional
public class CodiceAccessoService {

    public static final Duration VALIDITA = Duration.ofMinutes(10);
    public static final int MAX_TENTATIVI = 5;
    public static final int MAX_INVII_ORA = 5;
    static final Duration PAUSA_TRA_INVII = Duration.ofSeconds(30);
    private static final Pattern CODICE = Pattern.compile("\\d{6}");

    private final JdbcTemplate jdbc;
    private final UtenteRepository utenti;
    private final EmailService email;
    private final BoffProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public CodiceAccessoService(JdbcTemplate jdbc, UtenteRepository utenti, EmailService email,
                                BoffProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.utenti = utenti;
        this.email = email;
        this.properties = properties;
        this.clock = clock;
    }

    /** Un testo di 6 cifre non può essere una password (minimo 8 caratteri): è un codice. */
    public static boolean sembraUnCodice(String s) {
        return s != null && CODICE.matcher(s.trim()).matches();
    }

    /** Invia il codice se l'email appartiene a un account attivo; altrimenti non fa nulla. */
    public void richiedi(String indirizzo) {
        utenti.findByEmail(Utente.normalizzaEmail(indirizzo)).filter(Utente::isAttivo).ifPresent(this::invia);
    }

    /** @return il codice in chiaro (solo per i test), o vuoto se l'invio è stato limitato */
    Optional<String> invia(Utente utente) {
        OffsetDateTime adesso = OffsetDateTime.now(clock);
        List<OffsetDateTime> recenti = jdbc.queryForList(
                "SELECT creato_il FROM codice_accesso WHERE utente_id = ? AND creato_il > ? ORDER BY creato_il DESC",
                OffsetDateTime.class, utente.getId(), adesso.minusHours(1));
        if (recenti.size() >= MAX_INVII_ORA
                || (!recenti.isEmpty() && recenti.get(0).isAfter(adesso.minus(PAUSA_TRA_INVII)))) {
            return Optional.empty(); // troppe richieste: silenzioso
        }
        String codice = String.format("%06d", random.nextInt(1_000_000));
        // il nuovo codice annulla quelli precedenti (le righe restano per contare gli invii)
        jdbc.update("UPDATE codice_accesso SET tentativi = ? WHERE utente_id = ?", MAX_TENTATIVI, utente.getId());
        jdbc.update("INSERT INTO codice_accesso (utente_id, hash, scade_il, creato_il) VALUES (?, ?, ?, ?)",
                utente.getId(), hash(utente.getId(), codice), adesso.plus(VALIDITA), adesso);
        String destinatario = utente.getEmail();
        String testo = "Ciao " + utente.getNome() + ",\n\nil tuo codice per accedere è:\n\n    " + codice
                + "\n\nScrivilo nell'app entro " + VALIDITA.toMinutes() + " minuti. "
                + "Non comunicarlo a nessuno: il negozio non te lo chiederà mai.\n\n"
                + "Se non l'hai chiesto tu, ignora questa email.";
        String oggetto = codice + " è il tuo codice di accesso a " + properties.nomeNegozio();
        // invio in un altro thread: la risposta arriva subito, con o senza account
        CompletableFuture.runAsync(() -> email.invia(destinatario, oggetto, testo));
        return Optional.of(codice);
    }

    /**
     * Verifica il codice e, se giusto, lo consuma. Ogni errore consuma un tentativo
     * (salvato anche se l'accesso fallisce: il metodo non lancia eccezioni).
     */
    public boolean verifica(String indirizzo, String codice) {
        if (!sembraUnCodice(codice)) return false;
        Optional<Utente> u = utenti.findByEmail(Utente.normalizzaEmail(indirizzo)).filter(Utente::isAttivo);
        if (u.isEmpty()) return false;
        Long utenteId = u.get().getId();
        OffsetDateTime adesso = OffsetDateTime.now(clock);
        List<Long> validi = jdbc.queryForList(
                "SELECT id FROM codice_accesso WHERE utente_id = ? AND scade_il > ? AND tentativi < ? "
                        + "ORDER BY creato_il DESC LIMIT 1 FOR UPDATE",
                Long.class, utenteId, adesso, MAX_TENTATIVI);
        if (validi.isEmpty()) return false;
        Long id = validi.get(0);
        String atteso = jdbc.queryForObject("SELECT hash FROM codice_accesso WHERE id = ?", String.class, id);
        boolean giusto = MessageDigest.isEqual(atteso.getBytes(StandardCharsets.US_ASCII),
                hash(utenteId, codice.trim()).getBytes(StandardCharsets.US_ASCII));
        // giusto: consumato (non si può riusare); sbagliato: un tentativo in meno
        if (giusto) {
            jdbc.update("UPDATE codice_accesso SET tentativi = ? WHERE id = ?", MAX_TENTATIVI, id);
        } else {
            jdbc.update("UPDATE codice_accesso SET tentativi = tentativi + 1 WHERE id = ?", id);
        }
        return giusto;
    }

    /** Cancella i codici di un utente (es. cancellazione dei dati personali). */
    public void eliminaDi(Long utenteId) {
        jdbc.update("DELETE FROM codice_accesso WHERE utente_id = ?", utenteId);
    }

    static String hash(Long utenteId, String codice) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((utenteId + ":" + codice).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
