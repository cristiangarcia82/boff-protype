package it.boff.bike.accesso;

import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Accesso con il codice ricevuto via email, dallo stesso modulo di login (campi "username" e "password").
 * Se la "password" è di 6 cifre è un codice (le password hanno almeno 8 caratteri); altrimenti questo
 * provider non risponde e si passa al controllo della password.
 */
public class CodiceEmailAuthenticationProvider implements AuthenticationProvider {

    private final CodiceAccessoService codici;
    private final UserDetailsService userDetailsService;
    private final AccountStatusUserDetailsChecker controlli = new AccountStatusUserDetailsChecker();

    public CodiceEmailAuthenticationProvider(CodiceAccessoService codici, UserDetailsService userDetailsService) {
        this.codici = codici;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        String email = authentication.getName();
        Object credenziali = authentication.getCredentials();
        String codice = credenziali == null ? null : credenziali.toString();
        if (!CodiceAccessoService.sembraUnCodice(codice)) {
            return null; // è una password: se ne occupa il provider standard
        }
        UserDetails utente;
        try {
            utente = userDetailsService.loadUserByUsername(email);
        } catch (UsernameNotFoundException e) {
            throw new BadCredentialsException("Codice non valido");
        }
        controlli.check(utente); // disattivato o bloccato per troppi tentativi
        if (!codici.verifica(email, codice)) {
            throw new BadCredentialsException("Codice non valido");
        }
        return UsernamePasswordAuthenticationToken.authenticated(utente, null, utente.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
