package it.boff.bike.calendario;

import it.boff.bike.pianificazione.OreOfficina;
import it.boff.bike.utente.Utente;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Fotografia in memoria di chi lavora, quando e come, in un periodo: orari settimanali
 * del personale attivo, assenze e chiusure. Usata dal calendario e dalla pianificazione
 * (si carica una volta sola invece di interrogare il database giorno per giorno).
 */
public class Disponibilita implements OreOfficina {

    private final List<Utente> persone;
    private final Map<Long, List<FasciaOraria>> fascePerPersona;
    private final List<Assenza> assenze;
    private final Map<LocalDate, String> chiusure;
    private final boolean festiviChiusi;

    Disponibilita(List<FasciaOraria> fasce, List<Assenza> assenze, List<Chiusura> chiusure, boolean festiviChiusi) {
        this.fascePerPersona = fasce.stream().collect(Collectors.groupingBy(f -> f.getUtente().getId()));
        this.persone = fasce.stream().map(FasciaOraria::getUtente)
                .collect(Collectors.toMap(Utente::getId, Function.identity(), (a, b) -> a))
                .values().stream()
                .sorted(Comparator.comparing(Utente::isTitolare).reversed().thenComparing(Utente::getNome))
                .toList();
        this.assenze = assenze;
        this.chiusure = chiusure.stream().collect(Collectors.toMap(Chiusura::getData, Chiusura::getDescrizione));
        this.festiviChiusi = festiviChiusi;
    }

    /** Personale attivo che ha un orario settimanale (le "unità lavorative"). */
    public List<Utente> personale() {
        return persone;
    }

    /** Motivo della chiusura del negozio in quel giorno, se chiuso. */
    public Optional<String> chiusura(LocalDate giorno) {
        String c = chiusure.get(giorno);
        if (c != null) return Optional.of(c);
        return festiviChiusi ? Festivita.nome(giorno) : Optional.empty();
    }

    /** Assenza per l'intera giornata, se c'è. */
    public Optional<Assenza> assenza(Long utenteId, LocalDate giorno) {
        return assenze.stream().filter(a -> a.getUtente().getId().equals(utenteId) && a.comprende(giorno)
                && a.isGiornataIntera()).findFirst();
    }

    /** Assenze di parte della giornata (es. pomeriggio libero). */
    public List<Assenza> assenzeParziali(Long utenteId, LocalDate giorno) {
        return assenze.stream().filter(a -> a.getUtente().getId().equals(utenteId) && a.comprende(giorno)
                && !a.isGiornataIntera()).toList();
    }

    /**
     * Fasce dell'orario tipo valide in un giorno: vuote se il negozio è chiuso o la persona è
     * assente tutto il giorno. Le assenze parziali sono tolte in {@link #intervalli}.
     */
    public List<FasciaOraria> fasce(Long utenteId, LocalDate giorno) {
        if (chiusura(giorno).isPresent() || assenza(utenteId, giorno).isPresent()) {
            return List.of();
        }
        return fascePerPersona.getOrDefault(utenteId, List.of()).stream()
                .filter(f -> f.getGiorno() == giorno.getDayOfWeek())
                .sorted(Comparator.comparing(FasciaOraria::getOraInizio))
                .toList();
    }

    /** Intervalli effettivi di un tipo, al netto delle assenze parziali. */
    public List<Intervallo> intervalli(Long utenteId, LocalDate giorno, TipoFascia tipo) {
        List<Intervallo> assente = assenzeParziali(utenteId, giorno).stream()
                .map(a -> a.orario().orElseThrow()).toList();
        return fasce(utenteId, giorno).stream().filter(f -> f.getTipo() == tipo)
                .flatMap(f -> f.intervallo().meno(assente).stream()).toList();
    }

    @Override
    public List<Long> persone() {
        return persone.stream().map(Utente::getId).toList();
    }

    @Override
    public List<Intervallo> officina(Long utenteId, LocalDate giorno) {
        return intervalli(utenteId, giorno, TipoFascia.MANUTENZIONE);
    }

    public long minuti(Long utenteId, LocalDate giorno, TipoFascia tipo) {
        return intervalli(utenteId, giorno, tipo).stream().mapToLong(Intervallo::minuti).sum();
    }
}
