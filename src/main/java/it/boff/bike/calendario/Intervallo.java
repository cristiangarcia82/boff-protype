package it.boff.bike.calendario;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Intervallo orario [inizio, fine) all'interno di una giornata. */
public record Intervallo(LocalTime inizio, LocalTime fine) {

    public Intervallo {
        if (!fine.isAfter(inizio)) {
            throw new IllegalArgumentException("L'orario di fine deve essere dopo quello di inizio");
        }
    }

    public long minuti() {
        return Duration.between(inizio, fine).toMinutes();
    }

    public boolean siSovrappone(Intervallo altro) {
        return inizio.isBefore(altro.fine) && altro.inizio.isBefore(fine);
    }

    /** Questo intervallo meno quelli occupati, in ordine di orario. */
    public List<Intervallo> meno(List<Intervallo> occupati) {
        List<Intervallo> liberi = new ArrayList<>(List.of(this));
        for (Intervallo o : occupati.stream().sorted(Comparator.comparing(Intervallo::inizio)).toList()) {
            List<Intervallo> nuovi = new ArrayList<>();
            for (Intervallo l : liberi) {
                if (!l.siSovrappone(o)) {
                    nuovi.add(l);
                    continue;
                }
                if (l.inizio.isBefore(o.inizio)) nuovi.add(new Intervallo(l.inizio, o.inizio));
                if (o.fine.isBefore(l.fine)) nuovi.add(new Intervallo(o.fine, l.fine));
            }
            liberi = nuovi;
        }
        return liberi;
    }

    @Override
    public String toString() {
        return inizio + "–" + fine;
    }
}
