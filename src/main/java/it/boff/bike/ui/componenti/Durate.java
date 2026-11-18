package it.boff.bike.ui.componenti;

/** Formattazione delle durate in minuti ("1 h 30 min"). */
public final class Durate {

    private Durate() {
    }

    public static String formatta(long minuti) {
        long h = minuti / 60;
        long m = minuti % 60;
        if (h == 0) return m + " min";
        if (m == 0) return h + " h";
        return h + " h " + m + " min";
    }
}
