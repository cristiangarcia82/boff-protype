package it.boff.bike.ui.componenti;

import it.boff.bike.config.BoffProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Formattazione di date e orari in italiano, nel fuso orario del negozio. */
@Component
public class Formati {

    private static final Locale IT = Locale.ITALY;
    private static final DateTimeFormatter GIORNO = DateTimeFormatter.ofPattern("EEE d MMM", IT);
    private static final DateTimeFormatter GIORNO_ANNO = DateTimeFormatter.ofPattern("d MMM yyyy", IT);
    private static final DateTimeFormatter GIORNO_ORA = DateTimeFormatter.ofPattern("d MMM, HH:mm", IT);
    private static final DateTimeFormatter GIORNO_ANNO_ORA = DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm", IT);
    private static volatile ZoneId zona = ZoneId.of("Europe/Zurich");

    public Formati(BoffProperties properties) {
        zona = properties.fusoOrario();
    }

    public static String giorno(LocalDate d) {
        return d == null ? "–" : GIORNO.format(d);
    }

    public static String giornoAnno(LocalDate d) {
        return d == null ? "–" : GIORNO_ANNO.format(d);
    }

    public static String quando(OffsetDateTime t) {
        return t == null ? "–" : GIORNO_ORA.format(t.atZoneSameInstant(zona));
    }

    public static String data(OffsetDateTime t) {
        return t == null ? "–" : GIORNO_ANNO.format(t.atZoneSameInstant(zona));
    }

    public static ZoneId zona() {
        return zona;
    }

    /** "oggi", "ieri", "3 settimane fa", "5 mesi fa", "1 anno e 2 mesi fa". */
    public static String tempoFa(java.time.LocalDate quando, java.time.LocalDate oggi) {
        if (quando == null) return "–";
        long giorni = java.time.temporal.ChronoUnit.DAYS.between(quando, oggi);
        if (giorni <= 0) return "oggi";
        if (giorni == 1) return "ieri";
        if (giorni < 14) return giorni + " giorni fa";
        java.time.Period p = java.time.Period.between(quando, oggi);
        int mesi = p.getYears() * 12 + p.getMonths();
        if (mesi < 1) return (giorni / 7) + " settimane fa";
        if (mesi < 12) return mesi == 1 ? "1 mese fa" : mesi + " mesi fa";
        int anni = mesi / 12, resto = mesi % 12;
        return (anni == 1 ? "1 anno" : anni + " anni") + (resto == 0 ? "" : resto == 1 ? " e 1 mese" : " e " + resto + " mesi") + " fa";
    }

    /** "mar 24 nov 2026, 10:30" (fogli lavori, documenti da conservare). */
    public static String dataOra(OffsetDateTime t) {
        return t == null ? "–" : GIORNO_ANNO_ORA.format(t.atZoneSameInstant(zona));
    }
}
