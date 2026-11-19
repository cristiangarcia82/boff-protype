package it.boff.bike.utente;

import it.boff.bike.common.ErroreDominio;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;

/** Account e anagrafica di clienti e personale. */
@Service
@Validated
@Transactional
public class UtenteService {

    private final UtenteRepository utenti;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public UtenteService(UtenteRepository utenti, PasswordEncoder passwordEncoder, Clock clock) {
        this.utenti = utenti;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /** Il cliente si registra da solo dall'app. */
    public Utente registraCliente(@Valid DatiRegistrazione dati) {
        Utente u = nuovo(dati, passwordEncoder.encode(dati.getPassword()), Ruolo.CLIENTE);
        u.setPrivacyAccettataIl(OffsetDateTime.now(clock));
        return utenti.save(u);
    }

    /**
     * Il negozio registra un cliente al banco. L'account nasce con una password casuale
     * che nessuno conosce: il cliente la imposta con "Password dimenticata?".
     */
    public Utente creaCliente(@Valid DatiAnagrafica dati) {
        return utenti.save(nuovo(dati, passwordEncoder.encode(passwordCasuale()), Ruolo.CLIENTE));
    }

    /** Il titolare crea l'account del collaboratore. */
    public Utente creaCollaboratore(@Valid DatiRegistrazione dati) {
        return utenti.save(nuovo(dati, passwordEncoder.encode(dati.getPassword()), Ruolo.STAFF));
    }

    /**
     * Modifica nome, cognome e telefono. L'email (nome utente) non cambia, con un'eccezione:
     * a un cliente senza app si può aggiungere l'email, così potrà iniziare a usarla.
     */
    public Utente aggiornaAnagrafica(Long utenteId, @Valid DatiAnagrafica dati) {
        Utente u = trova(utenteId);
        String nuova = vuotaANull(Utente.normalizzaEmail(dati.getEmail()));
        if (!u.usaApp() && nuova != null) {
            if (utenti.existsByEmail(nuova)) {
                throw new ErroreDominio("Esiste già un account con questa email");
            }
            u.setEmail(nuova);
        }
        u.setNome(dati.getNome().trim());
        u.setCognome(dati.getCognome().trim());
        u.setTelefono(dati.getTelefono().trim());
        return u;
    }

    public void cambiaPassword(Long utenteId, String attuale,
                               @NotBlank @Size(min = DatiRegistrazione.PASSWORD_MIN, max = 100,
                                       message = "La nuova password deve avere almeno 8 caratteri") String nuova) {
        Utente u = trova(utenteId);
        if (attuale == null || !passwordEncoder.matches(attuale, u.getPasswordHash())) {
            throw new ErroreDominio("La password attuale non è corretta");
        }
        u.setPasswordHash(passwordEncoder.encode(nuova));
    }

    /** Usato dal recupero password (link via email): non richiede la password attuale. */
    public void impostaPassword(Long utenteId,
                                @NotBlank @Size(min = DatiRegistrazione.PASSWORD_MIN, max = 100,
                                        message = "La password deve avere almeno 8 caratteri") String nuova) {
        trova(utenteId).setPasswordHash(passwordEncoder.encode(nuova));
    }

    /** Il cliente sceglie se ricevere i promemoria di manutenzione. */
    public void impostaPromemoria(Long utenteId, boolean attivi) {
        trova(utenteId).setPromemoriaAttivi(attivi);
    }

    /** Attiva o disattiva un account. Il titolare non può disattivare sé stesso. */
    public void impostaAttivo(Long utenteId, boolean attivo) {
        Utente u = trova(utenteId);
        if (!attivo && u.isTitolare()) {
            throw new ErroreDominio("L'account del titolare non può essere disattivato");
        }
        u.setAttivo(attivo);
    }

    @Transactional(readOnly = true)
    public List<Utente> cercaClienti(String testo) {
        String filtro = testo == null ? "" : testo.trim();
        return utenti.cerca(Ruolo.CLIENTE, filtro);
    }

    @Transactional(readOnly = true)
    public List<Utente> personale() {
        return utenti.findByRuoloOrderByTitolareDescNomeAsc(Ruolo.STAFF);
    }

    @Transactional(readOnly = true)
    public Utente trova(Long utenteId) {
        return utenti.findById(utenteId).orElseThrow(() -> new ErroreDominio("Utente non trovato"));
    }

    private Utente nuovo(DatiAnagrafica dati, String passwordHash, Ruolo ruolo) {
        String email = vuotaANull(Utente.normalizzaEmail(dati.getEmail()));
        if (email == null && ruolo != Ruolo.CLIENTE) {
            throw new ErroreDominio("L'email è obbligatoria per il personale");
        }
        if (email != null && utenti.existsByEmail(email)) {
            throw new ErroreDominio("Esiste già un account con questa email");
        }
        Utente u = new Utente(email, passwordHash, dati.getNome().trim(), dati.getCognome().trim(), ruolo, false);
        u.setTelefono(dati.getTelefono() == null ? null : dati.getTelefono().trim());
        return u;
    }

    private static String vuotaANull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private String passwordCasuale() {
        byte[] b = new byte[24];
        random.nextBytes(b);
        return Base64.getUrlEncoder().encodeToString(b);
    }
}
