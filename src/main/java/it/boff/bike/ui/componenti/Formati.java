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
    private static volatile ZoneId zona = ZoneId.of("Europe/Rome");

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
}
