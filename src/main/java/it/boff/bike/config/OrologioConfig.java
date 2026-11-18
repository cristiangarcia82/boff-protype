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

    @Bean
    Clock clock(BoffProperties properties) {
        return Clock.system(properties.fusoOrario());
    }
}
