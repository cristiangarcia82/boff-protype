package it.boff.bike.calendario;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntervalloTest {

    static Intervallo i(String da, String a) {
        return new Intervallo(LocalTime.parse(da), LocalTime.parse(a));
    }

    @Test
    void sottrazioneDiIntervalliOccupati() {
        Intervallo mattina = i("09:00", "12:30");
        assertThat(mattina.meno(List.of())).containsExactly(mattina);
        assertThat(mattina.meno(List.of(i("10:00", "11:00")))).containsExactly(i("09:00", "10:00"), i("11:00", "12:30"));
        assertThat(mattina.meno(List.of(i("08:00", "09:30"), i("12:00", "13:00")))).containsExactly(i("09:30", "12:00"));
        assertThat(mattina.meno(List.of(i("09:00", "12:30")))).isEmpty();
        assertThat(mattina.meno(List.of(i("13:00", "14:00")))).containsExactly(mattina);
        assertThat(mattina.minuti()).isEqualTo(210);
    }

    @Test
    void intervalloVuotoNonAmmesso() {
        assertThatThrownBy(() -> i("10:00", "10:00")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pasquaEFestivita() {
        assertThat(Festivita.pasqua(2025)).isEqualTo(LocalDate.of(2025, 4, 20));
        assertThat(Festivita.pasqua(2026)).isEqualTo(LocalDate.of(2026, 4, 5));
        assertThat(Festivita.pasqua(2027)).isEqualTo(LocalDate.of(2027, 3, 28));
        // Canton Grigioni, 2026 (Pasqua il 5 aprile)
        assertThat(Festivita.nome(LocalDate.of(2026, 1, 1))).contains("Capodanno");
        assertThat(Festivita.nome(LocalDate.of(2026, 4, 3))).contains("Venerdì Santo");
        assertThat(Festivita.nome(LocalDate.of(2026, 4, 6))).contains("Lunedì di Pasqua");
        assertThat(Festivita.nome(LocalDate.of(2026, 5, 14))).contains("Ascensione");
        assertThat(Festivita.nome(LocalDate.of(2026, 5, 25))).contains("Lunedì di Pentecoste");
        assertThat(Festivita.nome(LocalDate.of(2026, 8, 1))).contains("Festa nazionale");
        assertThat(Festivita.nome(LocalDate.of(2026, 12, 26))).contains("Santo Stefano");
        // feste italiane che nei Grigioni non sono festive per legge cantonale
        assertThat(Festivita.nome(LocalDate.of(2026, 1, 6))).isEmpty();
        assertThat(Festivita.nome(LocalDate.of(2026, 4, 25))).isEmpty();
        assertThat(Festivita.nome(LocalDate.of(2026, 5, 1))).isEmpty();
        assertThat(Festivita.nome(LocalDate.of(2026, 6, 2))).isEmpty();
        assertThat(Festivita.nome(LocalDate.of(2026, 8, 15))).isEmpty();
        assertThat(Festivita.nome(LocalDate.of(2026, 11, 1))).isEmpty();
        assertThat(Festivita.nome(LocalDate.of(2026, 12, 8))).isEmpty();
    }
}
