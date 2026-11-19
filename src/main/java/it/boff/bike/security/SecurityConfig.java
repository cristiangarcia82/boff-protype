package it.boff.bike.security;

import com.vaadin.flow.spring.security.VaadinDefaultRequestCache;
import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import it.boff.bike.config.BoffProperties;
import it.boff.bike.ui.views.LoginView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.HexFormat;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, VaadinDefaultRequestCache requestCache,
                                            UserDetailsService userDetailsService,
                                            BoffProperties properties,
                                            it.boff.bike.accesso.CodiceAccessoService codici,
                                            java.time.Clock clock,
                                            org.springframework.security.core.context.SecurityContextHolderStrategy strategia)
            throws Exception {
        // I filtri WebAuthn leggono l'utente collegato dalla strategia globale, gli altri da quella di Vaadin:
        // senza allinearle la registrazione di una passkey risponde "non autenticato".
        org.springframework.security.core.context.SecurityContextHolder.setContextHolderStrategy(strategia);
        // Dopo il login torna alla pagina richiesta senza aggiungere "?continue" all'indirizzo
        HttpSessionRequestCache sessionCache = new HttpSessionRequestCache();
        sessionCache.setMatchingRequestParameterName(null);
        requestCache.setDelegateRequestCache(sessionCache);

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/icons/**", "/images/**", "/styles.css", "/actuator/health").permitAll()
                // registrazione di una passkey: solo per chi è già collegato
                .requestMatchers("/webauthn/register", "/webauthn/register/**").authenticated());
        // Il modulo di login Vaadin non ha la casella "ricordami": l'accesso resta sempre attivo
        // (o fino a "Esci"): 30 giorni per i clienti, 12 ore per il personale (Mac condiviso).
        String chiave = chiaveRememberMe(properties);
        AccessoPerRuolo resta = new AccessoPerRuolo(chiave, userDetailsService);
        http.rememberMe(rm -> rm.key(chiave).rememberMeServices(resta));
        http.addFilterBefore(new ScadenzaAccessoPersonale(clock),
                org.springframework.security.web.access.intercept.AuthorizationFilter.class);

        // Codice di 6 cifre via email, dallo stesso modulo di login
        http.authenticationProvider(new it.boff.bike.accesso.CodiceEmailAuthenticationProvider(codici, userDetailsService));

        // Passkey (Face ID / Touch ID). Il provider WebAuthn non controlla se l'account è attivo:
        // gli si passa un UserDetailsService che rifiuta account disattivati o bloccati.
        http.setSharedObject(UserDetailsService.class, soloAccountUtilizzabili(userDetailsService));
        java.net.URI pubblico = java.net.URI.create(properties.urlPubblico());
        http.webAuthn(w -> w
                .rpName(properties.nomeNegozio())
                .rpId(pubblico.getHost())
                .allowedOrigins(origine(pubblico))
                .disableDefaultRegistrationPage(true));

        http.with(VaadinSecurityConfigurer.vaadin(), vaadin -> vaadin.loginView(LoginView.class));
        SecurityFilterChain catena = http.build();
        // il filtro WebAuthn nasce senza "resta collegato" né cambio dell'id di sessione: si allinea al login normale
        catena.getFilters().stream()
                .filter(f -> f instanceof org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationFilter)
                .map(f -> (org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationFilter) f)
                .forEach(f -> {
                    f.setRememberMeServices(resta);
                    f.setSessionAuthenticationStrategy(
                            new org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy());
                    f.setSecurityContextRepository(
                            new org.springframework.security.web.context.HttpSessionSecurityContextRepository());
                });
        return catena;
    }

    static String origine(java.net.URI uri) {
        int porta = uri.getPort();
        return uri.getScheme() + "://" + uri.getHost() + (porta == -1 ? "" : ":" + porta);
    }

    private static UserDetailsService soloAccountUtilizzabili(UserDetailsService uds) {
        org.springframework.security.authentication.AccountStatusUserDetailsChecker controlli =
                new org.springframework.security.authentication.AccountStatusUserDetailsChecker();
        return username -> {
            var u = uds.loadUserByUsername(username);
            controlli.check(u);
            return u;
        };
    }

    /** Elenco e rimozione delle passkey salvate nel database (schema standard di Spring Security). */
    @Bean
    org.springframework.security.web.webauthn.management.UserCredentialRepository credenzialiPasskey(
            org.springframework.jdbc.core.JdbcOperations jdbc) {
        return new org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository(jdbc);
    }

    @Bean
    org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository utentiPasskey(
            org.springframework.jdbc.core.JdbcOperations jdbc) {
        return new org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository(jdbc);
    }

    private static String chiaveRememberMe(BoffProperties properties) {
        if (StringUtils.hasText(properties.rememberMeKey())) {
            return properties.rememberMeKey();
        }
        log.warn("BOFF_REMEMBER_ME_KEY non impostata: uso una chiave casuale, gli accessi salvati "
                + "non sopravvivono al riavvio.");
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        return HexFormat.of().formatHex(random);
    }

    /**
     * Encoder "delegante": le nuove password sono salvate come {bcrypt}…, quelle già
     * esistenti senza prefisso sono verificate con BCrypt. Permette di cambiare
     * algoritmo in futuro senza invalidare le password.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        DelegatingPasswordEncoder encoder =
                (DelegatingPasswordEncoder) PasswordEncoderFactories.createDelegatingPasswordEncoder();
        encoder.setDefaultPasswordEncoderForMatches(new BCryptPasswordEncoder());
        return encoder;
    }
}
