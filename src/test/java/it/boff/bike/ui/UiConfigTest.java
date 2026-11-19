package it.boff.bike.ui;

import it.boff.bike.common.ErroreDominio;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UiConfigTest {

    @Test
    void all_utenteSoloMessaggiScrittiPerLui() {
        assertThat(UiConfig.messaggioPer(new ErroreDominio("Bici non trovata"))).isEqualTo("Bici non trovata");
        assertThat(UiConfig.messaggioPer(new RuntimeException(new ErroreDominio("Lavoro chiuso")))).isEqualTo("Lavoro chiuso");
        // testi interni (librerie, SQL, percorsi) mai mostrati
        assertThat(UiConfig.messaggioPer(new IllegalStateException("Connection to db:5432 refused")))
                .startsWith("Si è verificato un errore imprevisto");
        assertThat(UiConfig.messaggioPer(new IllegalArgumentException("select * from utente")))
                .startsWith("Si è verificato un errore imprevisto");
    }
}
