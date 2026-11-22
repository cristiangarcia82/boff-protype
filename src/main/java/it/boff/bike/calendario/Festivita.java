package it.boff.bike.calendario;

import java.time.LocalDate;
import java.time.Month;
import java.time.MonthDay;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

/**
 * Giorni festivi del Canton Grigioni (legge cantonale sui giorni di riposo pubblici): Capodanno, Venerdì Santo,
 * Lunedì di Pasqua, Ascensione, Lunedì di Pentecoste, Festa nazionale, Natale e Santo Stefano.
 * Le date mobili si calcolano dalla Pasqua di ogni anno. Le feste che valgono solo in alcuni comuni
 * (es. Epifania, San Giuseppe, Corpus Domini, Assunta, Ognissanti, Immacolata nei comuni cattolici)
 * si aggiungono a mano come chiusure.
 */
public final class Festivita {

    private static final Map<MonthDay, String> FISSE = Map.of(
            MonthDay.of(Month.JANUARY, 1), "Capodanno",
            MonthDay.of(Month.AUGUST, 1), "Festa nazionale",
            MonthDay.of(Month.DECEMBER, 25), "Natale",
            MonthDay.of(Month.DECEMBER, 26), "Santo Stefano");

    private Festivita() {
    }

    public static Optional<String> nome(LocalDate giorno) {
        String fissa = FISSE.get(MonthDay.from(giorno));
        if (fissa != null) return Optional.of(fissa);
        long dallaPasqua = ChronoUnit.DAYS.between(pasqua(giorno.getYear()), giorno);
        if (dallaPasqua == -2) return Optional.of("Venerdì Santo");
        if (dallaPasqua == 1) return Optional.of("Lunedì di Pasqua");
        if (dallaPasqua == 39) return Optional.of("Ascensione");
        if (dallaPasqua == 50) return Optional.of("Lunedì di Pentecoste");
        return Optional.empty();
    }

    /** Algoritmo di Meeus/Jones/Butcher per il calendario gregoriano. */
    static LocalDate pasqua(int anno) {
        int a = anno % 19, b = anno / 100, c = anno % 100, d = b / 4, e = b % 4;
        int f = (b + 8) / 25, g = (b - f + 1) / 3, h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mese = (h + l - 7 * m + 114) / 31, giorno = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(anno, mese, giorno);
    }
}
