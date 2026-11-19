package it.boff.bike.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;

/**
 * Sul Mac condiviso una scheda lasciata aperta terrebbe viva la sessione all'infinito (Vaadin la
 * mantiene attiva). Per il personale la sessione dura al massimo {@link AccessoPerRuolo#DURATA_PERSONALE}
 * dall'accesso, come il cookie "resta collegato": poi si rientra.
 */
public class ScadenzaAccessoPersonale extends OncePerRequestFilter {

    static final String INIZIO = ScadenzaAccessoPersonale.class.getName() + ".inizio";

    private final Clock clock;

    public ScadenzaAccessoPersonale(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        HttpSession sessione = request.getSession(false);
        if (auth != null && auth.isAuthenticated() && sessione != null
                && AccessoPerRuolo.durata(auth).equals(AccessoPerRuolo.DURATA_PERSONALE)) {
            Instant adesso = clock.instant();
            Object inizio = sessione.getAttribute(INIZIO);
            if (inizio == null) {
                sessione.setAttribute(INIZIO, adesso);
            } else if (((Instant) inizio).plus(AccessoPerRuolo.DURATA_PERSONALE).isBefore(adesso)) {
                sessione.invalidate();
                SecurityContextHolder.clearContext();
                response.sendRedirect(request.getContextPath() + "/login");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
