package it.boff.bike.notifiche;

import it.boff.bike.common.ErroreDominio;
import it.boff.bike.config.BoffProperties;
import it.boff.bike.utente.Utente;
import it.boff.bike.utente.UtenteRepository;
import it.boff.bike.utente.UtenteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * "Password dimenticata?": link monouso via email, valido {@link #VALIDITA}.
 * Nel database si salva solo l'hash del token. La risposta all'utente è sempre la stessa,
 * che l'email esista o no (non si rivela chi è registrato).
 */
@Service
@Transactional
public class RecuperoPasswordService {

    static final Duration VALIDITA = Duration.ofHours(2);
    static final int MAX_RICHIESTE_ORA = 3;

    private final TokenPasswordRepository tokens;
    private final UtenteRepository utenti;
    private final UtenteService utenteService;
    private final EmailService email;
    private final BoffProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RecuperoPasswordService(TokenPasswordRepository tokens, UtenteRepository utenti, UtenteService utenteService,
                                   EmailService email, BoffProperties properties, Clock clock) {
        this.tokens = tokens;
        this.utenti = utenti;
        this.utenteService = utenteService;
        this.email = email;
        this.properties = properties;
        this.clock = clock;
    }

    /** Invia il link se l'email corrisponde a un account attivo; altrimenti non fa nulla. */
    public void richiedi(String indirizzo) {
        Optional<Utente> u = utenti.findByEmail(Utente.normalizzaEmail(indirizzo)).filter(Utente::isAttivo);
        u.ifPresent(utente -> inviaLink(utente, false));
    }

    /** Il negozio invia a un cliente creato al banco il link per scegliere la password. */
    public void invitaCliente(Long utenteId) {
        Utente u = utenti.findById(utenteId)
                .filter(x -> x.getRuolo() == it.boff.bike.utente.Ruolo.CLIENTE && x.isAttivo())
                .orElseThrow(() -> new ErroreDominio("Invito possibile solo per un cliente attivo"));
        if (!u.usaApp()) {
            throw new ErroreDominio("Il cliente non ha un'email: aggiungila con \"Modifica\" e poi invia il link");
        }
        inviaLink(u, true);
    }

    /** @return il token in chiaro (solo per i test) */
    String inviaLink(Utente utente, boolean invito) {
        OffsetDateTime adesso = OffsetDateTime.now(clock);
        if (tokens.countByUtenteIdAndCreatoIlAfter(utente.getId(), adesso.minusHours(1)) >= MAX_RICHIESTE_ORA) {
            return null; // troppe richieste: silenzioso
        }
        byte[] b = new byte[32];
        random.nextBytes(b);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        tokens.save(new TokenPassword(utente, hash(token), adesso, adesso.plus(VALIDITA)));
        String link = properties.urlPubblico() + "/reimposta-password?token=" + token;
        String testo = invito
                ? "Ciao " + utente.getNome() + ",\n\nil negozio ti ha registrato su " + properties.nomeNegozio()
                + ": da qui potrai seguire i lavori sulla tua bici e ricevere un avviso quando è pronta.\n\n"
                + "Scegli la tua password da questo link (valido 2 ore):\n" + link
                : "Ciao " + utente.getNome() + ",\n\nper impostare una nuova password apri questo link "
                + "(valido 2 ore):\n" + link + "\n\nSe non l'hai chiesto tu, ignora questa email.";
        String oggetto = invito ? "Benvenuto in " + properties.nomeNegozio() : "Nuova password";
        // invio in un altro thread: la risposta arriva subito, con o senza account
        // (i tempi non rivelano se un'email è registrata)
        String destinatario = utente.getEmail();
        java.util.concurrent.CompletableFuture.runAsync(() -> email.invia(destinatario, oggetto, testo));
        return token;
    }

    @Transactional(readOnly = true)
    public boolean tokenValido(String token) {
        return token != null && tokens.findByTokenHash(hash(token)).filter(t -> t.valido(OffsetDateTime.now(clock))).isPresent();
    }

    public void reimposta(String token, String nuovaPassword) {
        OffsetDateTime adesso = OffsetDateTime.now(clock);
        TokenPassword t = tokens.findByTokenHash(hash(token == null ? "" : token))
                .filter(x -> x.valido(adesso))
                .orElseThrow(() -> new ErroreDominio("Il link non è più valido: richiedine uno nuovo"));
        utenteService.impostaPassword(t.getUtente().getId(), nuovaPassword);
        // un link usato rende inutili anche gli altri ancora validi dello stesso utente
        tokens.invalidaTutti(t.getUtente().getId(), adesso);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
