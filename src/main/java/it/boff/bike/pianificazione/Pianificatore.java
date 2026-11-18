package it.boff.bike.pianificazione;

import it.boff.bike.calendario.Intervallo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Algoritmo di pianificazione "greedy" (nessun accesso al database: facile da testare).
 * <ol>
 *   <li>I lavori sono ordinati per priorità (vedi {@link LavoroDaPianificare#ORDINE}).</li>
 *   <li>Ogni intervento occupa il primo spazio libero nelle ore di <b>officina</b> di una
 *       qualsiasi persona, a partire da adesso o dall'arrivo della bici.</li>
 *   <li>Gli interventi dello stesso lavoro sono in sequenza (una bici, un banco da lavoro):
 *       il successivo inizia dopo la fine del precedente; a parità di orario si preferisce
 *       la persona che sta già lavorando su quella bici.</li>
 *   <li>Un intervento lungo può essere spezzato su più fasce o giorni; non si creano
 *       pezzi più corti di {@value #PEZZO_MINIMO} minuti (salvo la parte finale).</li>
 * </ol>
 */
public class Pianificatore {

    static final int PEZZO_MINIMO = 15;
    static final int GIORNI_ORIZZONTE = 90;

    /** Un intervento da collocare. */
    public record RigaDaPianificare(Long rigaId, int minuti) {
    }

    /** Un lavoro con i suoi interventi ancora da collocare, nell'ordine di esecuzione. */
    public record LavoroDaPianificare(Long lavoroId, boolean inLavorazione, boolean urgente, LocalDate dataPromessa,
                                      LocalDateTime arrivo, List<RigaDaPianificare> righe) {

        /** In lavorazione prima (si finisce ciò che è iniziato), poi urgenti, data promessa, ordine di arrivo. */
        public static final Comparator<LavoroDaPianificare> ORDINE = Comparator
                .comparing(LavoroDaPianificare::inLavorazione).reversed()
                .thenComparing(Comparator.comparing(LavoroDaPianificare::urgente).reversed())
                .thenComparing(LavoroDaPianificare::dataPromessa, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(LavoroDaPianificare::arrivo)
                .thenComparing(LavoroDaPianificare::lavoroId);
    }

    /** Spazio già occupato (es. assegnazioni bloccate a mano). */
    public record Occupato(Long utenteId, LocalDate data, Intervallo intervallo, Long lavoroId) {
    }

    /** Pezzo di intervento collocato in calendario. */
    public record Pezzo(Long lavoroId, Long rigaId, Long utenteId, LocalDate data, Intervallo intervallo) {
    }

    public record Risultato(List<Pezzo> pezzi, Map<Long, LocalDate> finePerLavoro, List<Long> nonPianificabili) {
    }

    private final OreOfficina ore;
    private final LocalDateTime adesso;
    private final Map<String, List<Intervallo>> occupati = new HashMap<>();

    public Pianificatore(OreOfficina ore, LocalDateTime adesso, List<Occupato> giaOccupati) {
        this.ore = ore;
        this.adesso = adesso;
        giaOccupati.forEach(o -> occupa(o.utenteId(), o.data(), o.intervallo()));
    }

    public Risultato pianifica(List<LavoroDaPianificare> lavori, Map<Long, LocalDateTime> fineBloccatePerLavoro) {
        List<Pezzo> pezzi = new ArrayList<>();
        Map<Long, LocalDate> fine = new HashMap<>();
        List<Long> nonPianificabili = new ArrayList<>();

        for (LavoroDaPianificare l : lavori.stream().sorted(LavoroDaPianificare.ORDINE).toList()) {
            LocalDateTime cursore = max(adesso, l.arrivo());
            Long personaPreferita = null;
            boolean ok = true;
            for (RigaDaPianificare r : l.righe()) {
                int restanti = r.minuti();
                while (restanti > 0) {
                    Optional<Pezzo> p = primoSpazio(l.lavoroId(), r.rigaId(), cursore, restanti, personaPreferita);
                    if (p.isEmpty()) {
                        ok = false;
                        break;
                    }
                    Pezzo pezzo = p.get();
                    pezzi.add(pezzo);
                    occupa(pezzo.utenteId(), pezzo.data(), pezzo.intervallo());
                    restanti -= (int) pezzo.intervallo().minuti();
                    cursore = LocalDateTime.of(pezzo.data(), pezzo.intervallo().fine());
                    personaPreferita = pezzo.utenteId();
                }
                if (!ok) break;
            }
            if (!ok) {
                nonPianificabili.add(l.lavoroId());
                continue;
            }
            LocalDateTime ultimo = cursore;
            LocalDateTime bloccate = fineBloccatePerLavoro.get(l.lavoroId());
            if (bloccate != null && bloccate.isAfter(ultimo)) ultimo = bloccate;
            if (!l.righe().isEmpty() || bloccate != null) {
                fine.put(l.lavoroId(), ultimo.toLocalDate());
            }
        }
        return new Risultato(pezzi, fine, nonPianificabili);
    }

    private Optional<Pezzo> primoSpazio(Long lavoroId, Long rigaId, LocalDateTime da, int minuti, Long preferita) {
        for (int i = 0; i < GIORNI_ORIZZONTE; i++) {
            LocalDate giorno = da.toLocalDate().plusDays(i);
            LocalTime minimo = i == 0 ? arrotonda(da.toLocalTime()) : LocalTime.MIN;
            if (i == 0 && minimo == null) continue; // oltre la mezzanotte
            Candidato migliore = null;
            for (Long persona : ore.persone()) {
                for (Intervallo fascia : ore.officina(persona, giorno)) {
                    if (!fascia.fine().isAfter(minimo)) continue;
                    Intervallo utile = fascia.inizio().isBefore(minimo) ? new Intervallo(minimo, fascia.fine()) : fascia;
                    for (Intervallo libero : utile.meno(occupati(persona, giorno))) {
                        long durata = Math.min(libero.minuti(), minuti);
                        if (durata < Math.min(PEZZO_MINIMO, minuti)) continue;
                        Candidato c = new Candidato(persona, libero.inizio(), (int) durata);
                        if (migliore == null || c.inizio.isBefore(migliore.inizio)
                                || (c.inizio.equals(migliore.inizio) && c.utenteId.equals(preferita))) {
                            migliore = c;
                        }
                        break; // il primo libero della fascia è il più presto
                    }
                }
            }
            if (migliore != null) {
                return Optional.of(new Pezzo(lavoroId, rigaId, migliore.utenteId, giorno,
                        new Intervallo(migliore.inizio, migliore.inizio.plusMinutes(migliore.minuti))));
            }
        }
        return Optional.empty();
    }

    private record Candidato(Long utenteId, LocalTime inizio, int minuti) {
    }

    private List<Intervallo> occupati(Long utenteId, LocalDate giorno) {
        return occupati.getOrDefault(utenteId + "|" + giorno, List.of());
    }

    private void occupa(Long utenteId, LocalDate giorno, Intervallo i) {
        occupati.computeIfAbsent(utenteId + "|" + giorno, k -> new ArrayList<>()).add(i);
    }

    /** Arrotonda al quarto d'ora successivo; null se si va al giorno dopo. */
    static LocalTime arrotonda(LocalTime t) {
        int minuti = t.getHour() * 60 + t.getMinute() + (t.getSecond() > 0 || t.getNano() > 0 ? 1 : 0);
        int arrotondati = ((minuti + 14) / 15) * 15;
        return arrotondati >= 24 * 60 ? null : LocalTime.of(arrotondati / 60, arrotondati % 60);
    }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return b == null || a.isAfter(b) ? a : b;
    }
}
