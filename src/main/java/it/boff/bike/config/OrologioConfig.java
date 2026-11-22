package it.boff.bike.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Orologio dell'applicazione nel fuso orario del negozio.
 * Tutto il codice usa questo bean invece di {@code LocalDate.now()}, così i test
 * possono fissare la data e il server può girare in UTC senza sbagliare "oggi".
 */
@Configuration
public class OrologioConfig {

    /**
     * Solo per le demo ({@code boff.demo=true}): {@code boff.orologio-demo=2026-11-24T10:30} fa partire l'app da
     * quel momento (poi il tempo scorre normalmente). Utile per mostrare un giorno lavorativo anche di domenica.
     * Ignorato senza demo: in produzione l'ora è sempre quella vera.
     */
    @Bean
    Clock clock(BoffProperties properties,
                @org.springframework.beans.factory.annotation.Value("${boff.orologio-demo:}") String orologioDemo) {
        Clock vero = Clock.system(properties.fusoOrario());
        if (!properties.demo() || orologioDemo == null || orologioDemo.isBlank()) return vero;
        java.time.Instant partenza = java.time.LocalDateTime.parse(orologioDemo.trim()).atZone(properties.fusoOrario()).toInstant();
        return Clock.offset(vero, java.time.Duration.between(vero.instant(), partenza));
    }
}
