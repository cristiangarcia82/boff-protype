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
        assertThat(Festivita.nome(LocalDate.of(2026, 4, 6))).contains("Lunedì dell'Angelo");
        assertThat(Festivita.nome(LocalDate.of(2026, 12, 8))).contains("Immacolata");
        assertThat(Festivita.nome(LocalDate.of(2026, 12, 9))).isEmpty();
    }
}
