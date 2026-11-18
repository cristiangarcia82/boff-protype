package it.boff.bike.security;

import com.vaadin.flow.spring.security.AuthenticationContext;
import it.boff.bike.utente.Utente;
import it.boff.bike.utente.UtenteRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Accesso comodo all'utente autenticato dalle viste Vaadin. */
@Component
public class UtenteCorrente {

    private final AuthenticationContext authenticationContext;
    private final UtenteRepository utenti;

    public UtenteCorrente(AuthenticationContext authenticationContext, UtenteRepository utenti) {
        this.authenticationContext = authenticationContext;
        this.utenti = utenti;
    }

    public Optional<Utente> get() {
        return authenticationContext.getAuthenticatedUser(UserDetails.class)
                .flatMap(u -> utenti.findByEmail(u.getUsername()));
    }

    public boolean isStaff() {
        return authenticationContext.hasRole(Ruoli.STAFF);
    }

    public boolean isTitolare() {
        return authenticationContext.hasRole(Ruoli.TITOLARE);
    }

    public void logout() {
        authenticationContext.logout();
    }
}
