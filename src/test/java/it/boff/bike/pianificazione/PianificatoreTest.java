package it.boff.bike.pianificazione;

import it.boff.bike.calendario.Intervallo;
import it.boff.bike.pianificazione.Pianificatore.LavoroDaPianificare;
import it.boff.bike.pianificazione.Pianificatore.Occupato;
import it.boff.bike.pianificazione.Pianificatore.Pezzo;
import it.boff.bike.pianificazione.Pianificatore.RigaDaPianificare;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PianificatoreTest {

    static final long MARIO = 1L, LUCA = 2L;
    static final LocalDate LUNEDI = LocalDate.of(2031, 3, 10);

    static Intervallo i(String a, String b) {
        return new Intervallo(LocalTime.parse(a), LocalTime.parse(b));
    }

    /** Mario: officina 9–12:30 da lunedì a venerdì. Luca: officina 15–19 da lunedì a venerdì. */
    static final OreOfficina ORE = new OreOfficina() {
        public List<Long> persone() { return List.of(MARIO, LUCA); }
        public List<Intervallo> officina(Long u, LocalDate g) {
            if (g.getDayOfWeek().getValue() >= DayOfWeek.SATURDAY.getValue()) return List.of();
            return u == MARIO ? List.of(i("09:00", "12:30")) : List.of(i("15:00", "19:00"));
        }
    };

    static LavoroDaPianificare lavoro(long id, boolean urgente, LocalDate promessa, String arrivo, int... minuti) {
        List<RigaDaPianificare> righe = new java.util.ArrayList<>();
        for (int k = 0; k < minuti.length; k++) righe.add(new RigaDaPianificare(id * 100 + k, minuti[k]));
        return new LavoroDaPianificare(id, false, urgente, promessa, LocalDateTime.parse(arrivo), righe);
    }

    static Pianificatore.Risultato pianifica(LocalDateTime adesso, List<Occupato> occupati, LavoroDaPianificare... lavori) {
        return new Pianificatore(ORE, adesso, occupati).pianifica(List.of(lavori), Map.of());
    }

    @Test
    void interventiInSequenzaNelPrimoSpazioDiOfficina() {
        var r = pianifica(LUNEDI.atTime(8, 0), List.of(), lavoro(1, false, null, "2031-03-10T08:00", 60, 30));
        assertThat(r.pezzi()).extracting(Pezzo::utenteId, Pezzo::data, Pezzo::intervallo).containsExactly(
                org.assertj.core.groups.Tuple.tuple(MARIO, LUNEDI, i("09:00", "10:00")),
                org.assertj.core.groups.Tuple.tuple(MARIO, LUNEDI, i("10:00", "10:30")));
        assertThat(r.finePerLavoro()).containsEntry(1L, LUNEDI);
    }

    @Test
    void siPartedallOraAttualeArrotondataAlQuartoDora() {
        var r = pianifica(LUNEDI.atTime(10, 7), List.of(), lavoro(1, false, null, "2031-03-10T08:00", 30));
        assertThat(r.pezzi().get(0).intervallo()).isEqualTo(i("10:15", "10:45"));
    }

    @Test
    void unInterventoLungoVieneSpezzatoTraPersoneEGiorni() {
        // 3h30 di Mario + 4h di Luca = 7h30 lunedì; un tagliando da 8 ore finisce martedì
        var r = pianifica(LUNEDI.atTime(8, 0), List.of(), lavoro(1, false, null, "2031-03-10T08:00", 480));
        assertThat(r.pezzi()).extracting(Pezzo::utenteId, Pezzo::data, Pezzo::intervallo).containsExactly(
                org.assertj.core.groups.Tuple.tuple(MARIO, LUNEDI, i("09:00", "12:30")),
                org.assertj.core.groups.Tuple.tuple(LUCA, LUNEDI, i("15:00", "19:00")),
                org.assertj.core.groups.Tuple.tuple(MARIO, LUNEDI.plusDays(1), i("09:00", "09:30")));
        assertThat(r.finePerLavoro()).containsEntry(1L, LUNEDI.plusDays(1));
    }

    @Test
    void urgentiEDatePromessePrimaDellOrdineDiArrivo() {
        var normale = lavoro(1, false, null, "2031-03-03T08:00", 210);
        var promesso = lavoro(2, false, LUNEDI, "2031-03-09T08:00", 210);
        var urgente = lavoro(3, true, null, "2031-03-09T18:00", 60);
        var r = pianifica(LUNEDI.atTime(8, 0), List.of(), normale, promesso, urgente);
        assertThat(r.pezzi().get(0).lavoroId()).isEqualTo(3L);
        assertThat(r.pezzi().get(0).intervallo()).isEqualTo(i("09:00", "10:00"));
        assertThat(r.pezzi()).filteredOn(p -> p.lavoroId() == 2L).first()
                .satisfies(p -> assertThat(p.intervallo().inizio()).isEqualTo(LocalTime.of(10, 0)));
    }

    @Test
    void gliSpaziOccupatiVengonoRispettatiENonSiFannoPezziTroppoPiccoli() {
        // Mario occupato 9:00–12:20: restano 10 minuti, troppo pochi per un intervento da 30
        var occupato = List.of(new Occupato(MARIO, LUNEDI, i("09:00", "12:20"), 99L));
        var r = pianifica(LUNEDI.atTime(8, 0), occupato, lavoro(1, false, null, "2031-03-10T08:00", 30));
        assertThat(r.pezzi()).singleElement().satisfies(p -> {
            assertThat(p.utenteId()).isEqualTo(LUCA);
            assertThat(p.intervallo()).isEqualTo(i("15:00", "15:30"));
        });
    }

    @Test
    void nonSiLavoraPrimaDellArrivoDellaBiciNeNelWeekend() {
        // arriva venerdì sera: si lavora da lunedì
        var r = pianifica(LUNEDI.minusDays(3).atTime(8, 0), List.of(),
                lavoro(1, false, null, "2031-03-07T19:30", 30));
        assertThat(r.pezzi().get(0).data()).isEqualTo(LUNEDI);
    }

    @Test
    void senzaOreDiOfficinaIlLavoroNonEPianificabile() {
        OreOfficina nessuna = new OreOfficina() {
            public List<Long> persone() { return List.of(MARIO); }
            public List<Intervallo> officina(Long u, LocalDate g) { return List.of(); }
        };
        var r = new Pianificatore(nessuna, LUNEDI.atTime(8, 0), List.of())
                .pianifica(List.of(lavoro(1, false, null, "2031-03-10T08:00", 30)), Map.of());
        assertThat(r.nonPianificabili()).containsExactly(1L);
        assertThat(r.pezzi()).isEmpty();
    }

    @Test
    void arrotondamento() {
        assertThat(Pianificatore.arrotonda(LocalTime.of(9, 0))).isEqualTo(LocalTime.of(9, 0));
        assertThat(Pianificatore.arrotonda(LocalTime.of(9, 0, 1))).isEqualTo(LocalTime.of(9, 15));
        assertThat(Pianificatore.arrotonda(LocalTime.of(9, 46))).isEqualTo(LocalTime.of(10, 0));
        assertThat(Pianificatore.arrotonda(LocalTime.of(23, 50))).isNull();
    }
}
