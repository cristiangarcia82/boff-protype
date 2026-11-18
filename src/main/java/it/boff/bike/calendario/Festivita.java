package it.boff.bike.calendario;

import java.time.LocalDate;
import java.time.Month;
import java.time.MonthDay;
import java.util.Map;
import java.util.Optional;

/** Festività nazionali italiane (incluse Pasqua e Pasquetta, calcolate per ogni anno). */
public final class Festivita {

    private static final Map<MonthDay, String> FISSE = Map.ofEntries(
            Map.entry(MonthDay.of(Month.JANUARY, 1), "Capodanno"),
            Map.entry(MonthDay.of(Month.JANUARY, 6), "Epifania"),
            Map.entry(MonthDay.of(Month.APRIL, 25), "Festa della Liberazione"),
            Map.entry(MonthDay.of(Month.MAY, 1), "Festa del Lavoro"),
            Map.entry(MonthDay.of(Month.JUNE, 2), "Festa della Repubblica"),
            Map.entry(MonthDay.of(Month.AUGUST, 15), "Ferragosto"),
            Map.entry(MonthDay.of(Month.NOVEMBER, 1), "Ognissanti"),
            Map.entry(MonthDay.of(Month.DECEMBER, 8), "Immacolata"),
            Map.entry(MonthDay.of(Month.DECEMBER, 25), "Natale"),
            Map.entry(MonthDay.of(Month.DECEMBER, 26), "Santo Stefano"));

    private Festivita() {
    }

    public static Optional<String> nome(LocalDate giorno) {
        String fissa = FISSE.get(MonthDay.from(giorno));
        if (fissa != null) return Optional.of(fissa);
        LocalDate pasqua = pasqua(giorno.getYear());
        if (giorno.equals(pasqua)) return Optional.of("Pasqua");
        if (giorno.equals(pasqua.plusDays(1))) return Optional.of("Lunedì dell'Angelo");
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
