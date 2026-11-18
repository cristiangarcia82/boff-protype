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

    /** Durata del "resta collegato": un mese, comodo per l'app installata sul telefono. */
    static final int DURATA_ACCESSO_SECONDI = 60 * 60 * 24 * 30;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, VaadinDefaultRequestCache requestCache,
                                            UserDetailsService userDetailsService,
                                            BoffProperties properties) throws Exception {
        // Dopo il login torna alla pagina richiesta senza aggiungere "?continue" all'indirizzo
        HttpSessionRequestCache sessionCache = new HttpSessionRequestCache();
        sessionCache.setMatchingRequestParameterName(null);
        requestCache.setDelegateRequestCache(sessionCache);

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/icons/**", "/images/**", "/styles.css", "/actuator/health").permitAll());
        // Il modulo di login Vaadin non ha la casella "ricordami": l'accesso resta sempre
        // attivo per 30 giorni (o fino a "Esci"), come ci si aspetta da un'app sul telefono.
        http.rememberMe(rm -> rm
                .key(chiaveRememberMe(properties))
                .alwaysRemember(true)
                .userDetailsService(userDetailsService)
                .tokenValiditySeconds(DURATA_ACCESSO_SECONDI));
        http.with(VaadinSecurityConfigurer.vaadin(), vaadin -> vaadin.loginView(LoginView.class));
        return http.build();
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
