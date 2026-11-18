package it.boff.bike.security;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protezione semplice contro i tentativi ripetuti di indovinare una password:
 * dopo {@value #MAX_TENTATIVI} errori consecutivi sulla stessa email l'accesso
 * viene bloccato per {@link #BLOCCO}. In memoria: si azzera al riavvio (accettabile
 * per un'app con una sola istanza).
 */
@Component
public class TentativiLogin {

    static final int MAX_TENTATIVI = 5;
    static final Duration BLOCCO = Duration.ofMinutes(15);

    private record Stato(int errori, Instant ultimo) {
    }

    private final Map<String, Stato> stati = new ConcurrentHashMap<>();
    private final Clock clock;

    public TentativiLogin(Clock clock) {
        this.clock = clock;
    }

    public boolean isBloccato(String email) {
        Stato s = stati.get(chiave(email));
        if (s == null || s.errori() < MAX_TENTATIVI) {
            return false;
        }
        if (s.ultimo().plus(BLOCCO).isBefore(clock.instant())) {
            stati.remove(chiave(email));
            return false;
        }
        return true;
    }

    @EventListener
    public void suErrore(AuthenticationFailureBadCredentialsEvent event) {
        stati.merge(chiave(event.getAuthentication().getName()), new Stato(1, clock.instant()),
                (vecchio, nuovo) -> new Stato(vecchio.errori() + 1, nuovo.ultimo()));
    }

    @EventListener
    public void suSuccesso(AuthenticationSuccessEvent event) {
        stati.remove(chiave(event.getAuthentication().getName()));
    }

    private static String chiave(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
