package it.boff.bike.pianificazione;

import it.boff.bike.calendario.CalendarioService;
import it.boff.bike.calendario.Disponibilita;
import it.boff.bike.calendario.Intervallo;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.config.BoffProperties;
import it.boff.bike.lavoro.Lavoro;
import it.boff.bike.lavoro.LavoroService;
import it.boff.bike.lavoro.RigaLavoro;
import it.boff.bike.lavoro.StatoLavoro;
import it.boff.bike.pianificazione.Pianificatore.LavoroDaPianificare;
import it.boff.bike.pianificazione.Pianificatore.Occupato;
import it.boff.bike.pianificazione.Pianificatore.RigaDaPianificare;
import it.boff.bike.utente.Utente;
import it.boff.bike.utente.UtenteRepository;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Tiene aggiornato il calendario dell'officina.
 * <ul>
 *   <li>Si ricalcola da solo dopo ogni modifica rilevante ({@link RichiestaRipianificazione}).</li>
 *   <li>Le assegnazioni spostate a mano sono "bloccate": il ricalcolo le lascia dove sono
 *       e pianifica il resto attorno.</li>
 * </ul>
 */
@Service
@Transactional
public class PianificazioneService {

    private static final Logger log = LoggerFactory.getLogger(PianificazioneService.class);

    private final AssegnazioneRepository assegnazioni;
    private final LavoroService lavori;
    private final CalendarioService calendario;
    private final UtenteRepository utenti;
    private final EntityManager em;
    private final BoffProperties properties;
    private final Clock clock;
    private final java.util.concurrent.atomic.AtomicLong ricalcoli = new java.util.concurrent.atomic.AtomicLong();
    /** Un ricalcolo alla volta (l'app gira su una sola istanza). */
    private final ReentrantLock lock = new ReentrantLock();

    public PianificazioneService(AssegnazioneRepository assegnazioni, LavoroService lavori,
                                 CalendarioService calendario, UtenteRepository utenti, EntityManager em,
                                 BoffProperties properties, Clock clock) {
        this.assegnazioni = assegnazioni;
        this.lavori = lavori;
        this.calendario = calendario;
        this.utenti = utenti;
        this.em = em;
        this.properties = properties;
        this.clock = clock;
    }

    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void suRichiesta(RichiestaRipianificazione richiesta) {
        log.debug("Ripianificazione: {}", richiesta.motivo());
        ripianifica();
    }

    /**
     * Ogni ora: il tempo passa, e ciò che non è stato fatto va spostato in avanti.
     * (Il primo minuto dell'ora è arbitrario: serve solo che non sia troppo frequente.)
     */
    @Scheduled(cron = "0 5 * * * *")
    public void ricalcoloPeriodico() {
        ripianifica();
    }

    /** Ricalcola il calendario di tutti i lavori in officina. */
    public EsitoPianificazione ripianifica() {
        lock.lock();
        try {
            return calcola();
        } finally {
            lock.unlock();
        }
    }

    /** Numero di ricalcoli eseguiti dall'avvio (diagnostica e test). */
    public long ricalcoliEseguiti() {
        return ricalcoli.get();
    }

    private EsitoPianificazione calcola() {
        ricalcoli.incrementAndGet();
        LocalDateTime adesso = LocalDateTime.now(clock);
        LocalDate oggi = adesso.toLocalDate();
        List<Lavoro> inOfficina = lavori.conRighe(StatoLavoro.IN_OFFICINA);
        List<Long> ids = inOfficina.stream().map(Lavoro::getId).toList();
        List<Long> idsQuery = ids.isEmpty() ? List.of(-1L) : ids;

        assegnazioni.eliminaDaRicalcolare(idsQuery, oggi);
        em.flush();
        em.clear();
        inOfficina = lavori.conRighe(StatoLavoro.IN_OFFICINA);

        List<Assegnazione> bloccate = assegnazioni.bloccateDa(oggi, idsQuery);
        Map<Long, Integer> minutiBloccatiPerRiga = new HashMap<>();
        Map<Long, LocalDateTime> fineBloccatePerLavoro = new HashMap<>();
        List<Occupato> occupati = new ArrayList<>();
        for (Assegnazione a : bloccate) {
            Long lavoroId = a.getRiga().getLavoro().getId();
            occupati.add(new Occupato(a.getUtente().getId(), a.getData(), a.intervallo(), lavoroId));
            minutiBloccatiPerRiga.merge(a.getRiga().getId(), (int) a.intervallo().minuti(), Integer::sum);
            fineBloccatePerLavoro.merge(lavoroId, LocalDateTime.of(a.getData(), a.getOraFine()),
                    (x, y) -> x.isAfter(y) ? x : y);
        }

        List<LavoroDaPianificare> daPianificare = new ArrayList<>();
        Map<Long, RigaLavoro> righePerId = new HashMap<>();
        for (Lavoro l : inOfficina) {
            List<RigaDaPianificare> righe = new ArrayList<>();
            for (RigaLavoro r : l.getRighe()) {
                righePerId.put(r.getId(), r);
                if (r.isCompletata()) continue;
                int restanti = r.getDurataMin() - minutiBloccatiPerRiga.getOrDefault(r.getId(), 0);
                if (restanti > 0) righe.add(new RigaDaPianificare(r.getId(), restanti));
            }
            LocalDateTime arrivo = l.getCheckinIl() == null ? adesso
                    : l.getCheckinIl().atZoneSameInstant(properties.fusoOrario()).toLocalDateTime();
            daPianificare.add(new LavoroDaPianificare(l.getId(), l.getStato() == StatoLavoro.IN_LAVORAZIONE,
                    l.isUrgente(), l.getDataPromessa(), arrivo, righe));
        }

        Disponibilita disponibilita = calendario.disponibilita(oggi, oggi.plusDays(Pianificatore.GIORNI_ORIZZONTE));
        Pianificatore.Risultato r = new Pianificatore(disponibilita, adesso, occupati)
                .pianifica(daPianificare, fineBloccatePerLavoro);

        Map<Long, Utente> persone = new HashMap<>();
        for (Pianificatore.Pezzo p : r.pezzi()) {
            Utente u = persone.computeIfAbsent(p.utenteId(), id -> em.getReference(Utente.class, id));
            assegnazioni.save(new Assegnazione(righePerId.get(p.rigaId()), u, p.data(), p.intervallo()));
        }

        Map<Long, LocalDate> fine = new HashMap<>(r.finePerLavoro());
        lavori.applicaPianificazione(ids, fine);

        List<Long> inRitardo = inOfficina.stream()
                .filter(l -> l.getDataPromessa() != null && fine.containsKey(l.getId())
                        && fine.get(l.getId()).isAfter(l.getDataPromessa()))
                .map(Lavoro::getId).toList();
        return new EsitoPianificazione(r.nonPianificabili(), inRitardo);
    }

    /**
     * Sposta a mano un'assegnazione su una persona e un giorno (es. trascinandola nel calendario):
     * va nel primo spazio libero di officina abbastanza lungo e diventa "bloccata".
     */
    public void sposta(Long assegnazioneId, Long utenteId, LocalDate giorno) {
        sposta(assegnazioneId, utenteId, giorno, null);
    }

    /**
     * Come {@link #sposta(Long, Long, LocalDate)}, ma a un orario preciso: l'intervento deve
     * stare tutto in uno spazio libero di officina che inizia a quell'ora.
     */
    public void sposta(Long assegnazioneId, Long utenteId, LocalDate giorno, LocalTime ora) {
        lock.lock();
        try {
            Assegnazione a = assegnazioni.findById(assegnazioneId)
                    .orElseThrow(() -> new ErroreDominio("Assegnazione non trovata: ricarica la pagina"));
            LocalDateTime adesso = LocalDateTime.now(clock);
            if (giorno.isBefore(adesso.toLocalDate())) {
                throw new ErroreDominio("Non si può pianificare nel passato");
            }
            long durata = a.intervallo().minuti();
            Disponibilita d = calendario.disponibilita(giorno, giorno);
            List<Intervallo> occupati = assegnazioni.bloccateDiPersona(utenteId, giorno).stream()
                    .filter(x -> !x.getId().equals(assegnazioneId))
                    .map(Assegnazione::intervallo).toList();
            LocalTime minimo = giorno.equals(adesso.toLocalDate()) ? Pianificatore.arrotonda(adesso.toLocalTime()) : LocalTime.MIN;
            Intervallo destinazione = null;
            for (Intervallo fascia : d.officina(utenteId, giorno)) {
                if (minimo == null || !fascia.fine().isAfter(minimo)) continue;
                Intervallo utile = fascia.inizio().isBefore(minimo) ? new Intervallo(minimo, fascia.fine()) : fascia;
                for (Intervallo libero : utile.meno(occupati)) {
                    if (ora == null && libero.minuti() >= durata) {
                        destinazione = new Intervallo(libero.inizio(), libero.inizio().plusMinutes(durata));
                    } else if (ora != null && !ora.isBefore(libero.inizio())
                            && !ora.plusMinutes(durata).isAfter(libero.fine()) && ora.plusMinutes(durata).isAfter(ora)) {
                        destinazione = new Intervallo(ora, ora.plusMinutes(durata));
                    }
                    if (destinazione != null) break;
                }
                if (destinazione != null) break;
            }
            if (destinazione == null) {
                throw new ErroreDominio(ora == null
                        ? "Non c'è uno spazio libero di officina abbastanza lungo quel giorno"
                        : "Alle " + ora + " non ci sono " + durata + " minuti liberi di officina per quella persona");
            }
            Utente persona = utenti.findById(utenteId).orElseThrow();
            a.sposta(persona, giorno, destinazione);
            a.setBloccata(true);
            em.flush();
            calcola();
        } finally {
            lock.unlock();
        }
    }

    /** Toglie il blocco: alla prossima ripianificazione l'intervento può essere spostato. */
    public void sblocca(Long assegnazioneId) {
        assegnazioni.findById(assegnazioneId).ifPresent(a -> a.setBloccata(false));
        em.flush();
        ripianifica();
    }

    /** Fissa un'assegnazione dove si trova. */
    public void blocca(Long assegnazioneId) {
        assegnazioni.findById(assegnazioneId).ifPresent(a -> a.setBloccata(true));
    }

    @Transactional(readOnly = true)
    public List<Assegnazione> tra(LocalDate dal, LocalDate al) {
        return assegnazioni.tra(dal, al);
    }

    @Transactional(readOnly = true)
    public List<Assegnazione> diLavoro(Long lavoroId) {
        return assegnazioni.diLavoro(lavoroId);
    }

    /** Minuti già assegnati per persona e giorno nel periodo. */
    @Transactional(readOnly = true)
    public Map<String, Long> minutiAssegnati(LocalDate dal, LocalDate al) {
        return assegnazioni.tra(dal, al).stream().collect(Collectors.groupingBy(
                a -> a.getUtente().getId() + "|" + a.getData(), Collectors.summingLong(a -> a.intervallo().minuti())));
    }
}
