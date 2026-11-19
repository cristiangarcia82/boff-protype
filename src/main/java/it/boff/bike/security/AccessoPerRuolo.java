package it.boff.bike.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;

import java.time.Duration;

/**
 * "Resta collegato" con durata diversa per ruolo:
 * <ul>
 *   <li>clienti: 30 giorni, come ci si aspetta da un'app sul telefono;</li>
 *   <li>personale: {@link #DURATA_PERSONALE}, perché il Mac del negozio è condiviso fra titolare e
 *       collaboratore: a fine giornata l'accesso scade e il giorno dopo ciascuno entra col proprio account
 *       (con Touch ID bastano due secondi).</li>
 * </ul>
 */
public class AccessoPerRuolo extends TokenBasedRememberMeServices {

    public static final Duration DURATA_CLIENTI = Duration.ofDays(30);
    public static final Duration DURATA_PERSONALE = Duration.ofHours(12);

    public AccessoPerRuolo(String chiave, UserDetailsService userDetailsService) {
        super(chiave, userDetailsService);
        setAlwaysRemember(true);
        setTokenValiditySeconds((int) DURATA_CLIENTI.toSeconds());
    }

    @Override
    protected int calculateLoginLifetime(HttpServletRequest request, Authentication authentication) {
        return (int) durata(authentication).toSeconds();
    }

    /** Con le passkey il "principal" non è uno UserDetails: il nome (email) si prende sempre da getName(). */
    @Override
    protected String retrieveUserName(Authentication authentication) {
        return authentication.getName();
    }

    public static Duration durata(Authentication authentication) {
        boolean personale = authentication.getAuthorities().stream()
                .anyMatch(a -> ("ROLE_" + Ruoli.STAFF).equals(a.getAuthority()));
        return personale ? DURATA_PERSONALE : DURATA_CLIENTI;
    }
}
