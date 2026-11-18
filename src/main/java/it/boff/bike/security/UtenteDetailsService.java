package it.boff.bike.security;

import it.boff.bike.utente.Utente;
import it.boff.bike.utente.UtenteRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Carica gli utenti dal database per il login (username = email). */
@Service
public class UtenteDetailsService implements UserDetailsService {

    private final UtenteRepository utenti;

    public UtenteDetailsService(UtenteRepository utenti) {
        this.utenti = utenti;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Utente utente = utenti.findByEmail(Utente.normalizzaEmail(email))
                .orElseThrow(() -> new UsernameNotFoundException("Utente non trovato"));
        return User.withUsername(utente.getEmail())
                .password(utente.getPasswordHash())
                .authorities(autorizzazioni(utente))
                .disabled(!utente.isAttivo())
                .build();
    }

    static List<GrantedAuthority> autorizzazioni(Utente utente) {
        List<GrantedAuthority> ruoli = new ArrayList<>();
        ruoli.add(new SimpleGrantedAuthority("ROLE_" + utente.getRuolo().name()));
        if (utente.isTitolare()) {
            ruoli.add(new SimpleGrantedAuthority("ROLE_" + Ruoli.TITOLARE));
        }
        return ruoli;
    }
}
