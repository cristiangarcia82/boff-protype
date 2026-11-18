package it.boff.bike;

import it.boff.bike.utente.Ruolo;
import it.boff.bike.utente.Utente;
import it.boff.bike.utente.UtenteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class AutenticazioneIT {

    @Autowired
    UtenteRepository utenti;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    AuthenticationConfiguration authenticationConfiguration;

    private Authentication login(String email, String password) throws Exception {
        AuthenticationManager manager = authenticationConfiguration.getAuthenticationManager();
        return manager.authenticate(new UsernamePasswordAuthenticationToken(email, password));
    }

    /** Solo i ruoli (Spring Security aggiunge anche autorità tecniche come FACTOR_PASSWORD). */
    private static Iterable<String> ruoli(Authentication auth) {
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_")).toList();
    }

    @Test
    void titolareCreatoAllAvvioConEmailNormalizzata() throws Exception {
        Utente titolare = utenti.findByEmail("titolare@negozio.it").orElseThrow();
        assertThat(titolare.isTitolare()).isTrue();
        assertThat(titolare.getRuolo()).isEqualTo(Ruolo.STAFF);

        Authentication auth = login("TITOLARE@negozio.it", "segreta123");
        assertThat(ruoli(auth)).containsExactlyInAnyOrder("ROLE_STAFF", "ROLE_TITOLARE");
    }

    @Test
    void utentiDemoHannoIRuoliCorretti() throws Exception {
        assertThat(ruoli(login("collaboratore@demo.it", "demo1234"))).containsExactly("ROLE_STAFF");
        assertThat(ruoli(login("cliente@demo.it", "demo1234"))).containsExactly("ROLE_CLIENTE");
    }

    @Test
    void passwordErrataRifiutata() {
        assertThatThrownBy(() -> login("cliente@demo.it", "sbagliata"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void utenteDisattivatoNonPuoAccedere() {
        Utente u = utenti.save(new Utente("ex@cliente.it", passwordEncoder.encode("pwd12345"),
                "Ex", "Cliente", Ruolo.CLIENTE, false));
        u.setAttivo(false);
        utenti.save(u);

        assertThatThrownBy(() -> login("ex@cliente.it", "pwd12345"))
                .isInstanceOf(DisabledException.class);
    }

    @Test
    void dopoCinqueTentativiErratiLAccountEBloccato() {
        utenti.save(new Utente("bersaglio@cliente.it", passwordEncoder.encode("giusta123"),
                "B", "C", Ruolo.CLIENTE, false));
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> login("bersaglio@cliente.it", "sbagliata"))
                    .isInstanceOf(BadCredentialsException.class);
        }
        assertThatThrownBy(() -> login("bersaglio@cliente.it", "giusta123"))
                .isInstanceOf(LockedException.class);
    }
}
