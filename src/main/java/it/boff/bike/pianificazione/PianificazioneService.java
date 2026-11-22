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
    private final it.boff.bike.funzionalita.FunzionalitaService funzionalita;
    private final it.boff.bike.appuntamento.AppuntamentoRepository appuntamenti;

    public PianificazioneService(AssegnazioneRepository assegnazioni, LavoroService lavori,
                                 CalendarioService calendario, UtenteRepository utenti, EntityManager em,
                                 BoffProperties properties, Clock clock,
                                 it.boff.bike.funzionalita.FunzionalitaService funzionalita,
                                 it.boff.bike.appuntamento.AppuntamentoRepository appuntamenti) {
        this.appuntamenti = appuntamenti;
        this.funzionalita = funzionalita;
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
        aggiorna();
    }

    /** true se il titolare ha attivato il ricalcolo completo automatico. */
    public boolean automatica() {
        return funzionalita.attiva(it.boff.bike.funzionalita.Funzionalita.RIPIANIFICAZIONE_AUTOMATICA);
    }

    /**
     * Aggiorna il calendario dopo una modifica: ricalcolo completo se è attiva la ripianificazione
     * automatica, altrimenti conserva ciò che c'è e mette solo ciò che manca nei primi spazi liberi.
     */
    public EsitoPianificazione aggiorna() {
        lock.lock();
        try {
            return calcola(automatica());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Ogni ora: il tempo passa, e ciò che non è stato fatto va spostato in avanti.
     * (Il primo minuto dell'ora è arbitrario: serve solo che non sia troppo frequente.)
     */
    @Scheduled(cron = "0 5 * * * *")
    public void ricalcoloPeriodico() {
        if (automatica()) ripianifica();
    }

    /** Ricalcola da capo il calendario di tutti i lavori in officina (pulsante "Ripianifica"). */
    public EsitoPianificazione ripianifica() {
        lock.lock();
        try {
            return calcola(true);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Ricalcolo che conserva il calendario: si tengono le assegnazioni di interventi ancora da fare;
     * se un intervento ne ha più del necessario (durata ridotta) si ripianifica solo quello.
     */
    private static List<Long> daConservare(List<Assegnazione> daOggi, Disponibilita d, List<Occupato> appuntamenti) {
        Map<Long, List<Assegnazione>> perRiga = new HashMap<>();
        for (Assegnazione a : daOggi) {
            RigaLavoro r = a.getRiga();
            if (r.isCompletata() || !r.isValida()) continue;
            // un intervento non fissato finito sopra un appuntamento, un turno di vendita o un'assenza si ricolloca
            if (!a.isBloccata() && !ancoraValida(a, d, appuntamenti)) continue;
            perRiga.computeIfAbsent(r.getId(), k -> new ArrayList<>()).add(a);
        }
        List<Long> conserva = new ArrayList<>();
        for (List<Assegnazione> gruppo : perRiga.values()) {
            long minuti = gruppo.stream().mapToLong(a -> a.intervallo().minuti()).sum();
            if (minuti <= gruppo.get(0).getRiga().getDurataMin()) {
                gruppo.forEach(a -> conserva.add(a.getId()));
            }
        }
        return conserva;
    }

    private static boolean ancoraValida(Assegnazione a, Disponibilita d, List<Occupato> appuntamenti) {
        Intervallo i = a.intervallo();
        Long p = a.getUtente().getId();
        boolean inOrario = d.officina(p, a.getData()).stream()
                .anyMatch(o -> !i.inizio().isBefore(o.inizio()) && !i.fine().isAfter(o.fine()));
        boolean libera = appuntamenti.stream().noneMatch(o -> o.utenteId().equals(p) && o.data().equals(a.getData())
                && o.intervallo().siSovrappone(i));
        return inOrario && libera;
    }

    /** Numero di ricalcoli eseguiti dall'avvio (diagnostica e test). */
    public long ricalcoliEseguiti() {
        return ricalcoli.get();
    }

    /**
     * @param completo true: si tengono solo le assegnazioni fissate a mano e si ripianifica il resto;
     *                 false: si tengono tutte quelle da oggi in poi ancora valide e si pianifica solo ciò che manca
     */
    private EsitoPianificazione calcola(boolean completo) {
        ricalcoli.incrementAndGet();
        LocalDateTime adesso = LocalDateTime.now(clock);
        LocalDate oggi = adesso.toLocalDate();
        List<Lavoro> inOfficina = lavori.conRighe(StatoLavoro.IN_OFFICINA);
        List<Long> ids = inOfficina.stream().map(Lavoro::getId).toList();
        List<Long> idsQuery = ids.isEmpty() ? List.of(-1L) : ids;

        Disponibilita disponibilita = calendario.disponibilita(oggi, oggi.plusDays(Pianificatore.GIORNI_ORIZZONTE));
        // gli appuntamenti di consulenza hanno la precedenza: il loro tempo non va all'officina
        List<Occupato> appuntamentiAttivi = appuntamenti.tra(oggi, oggi.plusDays(Pianificatore.GIORNI_ORIZZONTE),
                        it.boff.bike.appuntamento.StatoAppuntamento.ATTIVI).stream()
                .map(a -> new Occupato(a.getUtente().getId(), a.getData(), a.intervallo(), null)).toList();
        if (completo) {
            assegnazioni.eliminaDaRicalcolare(idsQuery, oggi);
        } else {
            List<Long> conserva = daConservare(assegnazioni.daOggi(oggi, idsQuery), disponibilita, appuntamentiAttivi);
            assegnazioni.eliminaTranne(idsQuery, oggi, conserva.isEmpty() ? List.of(-1L) : conserva);
        }
        em.flush();
        em.clear();
        inOfficina = lavori.conRighe(StatoLavoro.IN_OFFICINA);

        // restano al loro posto: con il ricalcolo completo le fissate a mano, altrimenti tutto ciò che è valido
        List<Assegnazione> bloccate = completo ? assegnazioni.bloccateDa(oggi, idsQuery) : assegnazioni.daOggi(oggi, idsQuery);
        Map<Long, Integer> minutiBloccatiPerRiga = new HashMap<>();
        Map<Long, LocalDateTime> fineBloccatePerLavoro = new HashMap<>();
        List<Occupato> occupati = new ArrayList<>(appuntamentiAttivi);
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
                if (r.isCompletata() || !r.isValida()) continue; // extra in attesa o rifiutati: fuori calendario
                int restanti = r.getDurataMin() - minutiBloccatiPerRiga.getOrDefault(r.getId(), 0);
                if (restanti > 0) righe.add(new RigaDaPianificare(r.getId(), restanti));
            }
            LocalDateTime arrivo = l.getCheckinIl() == null ? adesso
                    : l.getCheckinIl().atZoneSameInstant(properties.fusoOrario()).toLocalDateTime();
            daPianificare.add(new LavoroDaPianificare(l.getId(), l.getStato() == StatoLavoro.IN_LAVORAZIONE,
                    l.isUrgente(), l.getDataPromessa(), arrivo, righe));
        }

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
        spostaTutti(List.of(assegnazioneId), utenteId, giorno, ora);
    }

    /**
     * Sposta insieme più interventi (es. il blocco di una bici nel calendario), nell'ordine dato:
     * con un orario uno dopo l'altro da quell'ora, senza ciascuno nel primo spazio libero.
     * Tutto o niente (una sola transazione) e un solo ricalcolo alla fine: durante il ricalcolo
     * le assegnazioni non fissate cambiano id, per questo lo spostamento non si fa una alla volta.
     */
    public void spostaTutti(List<Long> assegnazioniIds, Long utenteId, LocalDate giorno, LocalTime ora) {
        lock.lock();
        try {
            LocalDateTime adesso = LocalDateTime.now(clock);
            if (giorno.isBefore(adesso.toLocalDate())) {
                throw new ErroreDominio("Non si può pianificare nel passato");
            }
            List<Assegnazione> daSpostare = assegnazioniIds.stream()
                    .map(id -> assegnazioni.findById(id)
                            .orElseThrow(() -> new ErroreDominio("Assegnazione non trovata: ricarica la pagina")))
                    .toList();
            java.util.Set<Long> ids = new java.util.HashSet<>(assegnazioniIds);
            Disponibilita d = calendario.disponibilita(giorno, giorno);
            Utente persona = utenti.findById(utenteId).orElseThrow();
            var appuntamentiDelGiorno = appuntamenti.tra(giorno, giorno, it.boff.bike.appuntamento.StatoAppuntamento.ATTIVI);
            LocalTime prossima = ora;
            for (Assegnazione a : daSpostare) {
                long durata = a.intervallo().minuti();
                // occupano spazio gli interventi fissati, tranne quelli del gruppo non ancora collocati
                int posizione = assegnazioniIds.indexOf(a.getId());
                List<Intervallo> occupati = new ArrayList<>(assegnazioni.bloccateDiPersona(utenteId, giorno).stream()
                        .filter(x -> !ids.contains(x.getId()) || assegnazioniIds.indexOf(x.getId()) < posizione)
                        .map(Assegnazione::intervallo).toList());
                // nemmeno sopra un appuntamento di consulenza
                appuntamentiDelGiorno.stream().filter(x -> x.getUtente().getId().equals(utenteId))
                        .forEach(x -> occupati.add(x.intervallo()));
                LocalTime minimo = giorno.equals(adesso.toLocalDate()) ? Pianificatore.arrotonda(adesso.toLocalTime()) : LocalTime.MIN;
                Intervallo destinazione = null;
                for (Intervallo fascia : d.officina(utenteId, giorno)) {
                    if (minimo == null || !fascia.fine().isAfter(minimo)) continue;
                    Intervallo utile = fascia.inizio().isBefore(minimo) ? new Intervallo(minimo, fascia.fine()) : fascia;
                    for (Intervallo libero : utile.meno(occupati)) {
                        if (prossima == null && libero.minuti() >= durata) {
                            destinazione = new Intervallo(libero.inizio(), libero.inizio().plusMinutes(durata));
                        } else if (prossima != null && !prossima.isBefore(libero.inizio())
                                && !prossima.plusMinutes(durata).isAfter(libero.fine())
                                && prossima.plusMinutes(durata).isAfter(prossima)) {
                            destinazione = new Intervallo(prossima, prossima.plusMinutes(durata));
                        }
                        if (destinazione != null) break;
                    }
                    if (destinazione != null) break;
                }
                if (destinazione == null) {
                    throw new ErroreDominio(prossima == null
                            ? "Quel giorno non c'è uno spazio libero abbastanza lungo nelle ore di lavoro"
                            : "Alle " + prossima + " non ci sono " + durata + " minuti liberi nelle ore di lavoro di quella persona");
                }
                a.sposta(persona, giorno, destinazione);
                a.setBloccata(true);
                em.flush();
                if (prossima != null) prossima = destinazione.fine();
            }
            calcola(automatica());
        } finally {
            lock.unlock();
        }
    }

    /** Toglie il blocco: alla prossima ripianificazione l'intervento può essere spostato. */
    public void sblocca(Long assegnazioneId) {
        assegnazioni.findById(assegnazioneId).ifPresent(a -> a.setBloccata(false));
        em.flush();
        aggiorna();
    }

    /** Fissa un'assegnazione dove si trova. */
    public void blocca(Long assegnazioneId) {
        assegnazioni.findById(assegnazioneId).ifPresent(a -> a.setBloccata(true));
    }

    @Transactional(readOnly = true)
    public List<Assegnazione> tra(LocalDate dal, LocalDate al) {
        return assegnazioni.tra(dal, al);
    }

    /** Tempo libero di una persona in un giorno e gli orari da cui un lavoro intero ci sta di seguito. */
    public record GiornoLibero(LocalDate giorno, long minutiLiberi, List<LocalTime> inizi) {
    }

    /**
     * Per scegliere a mano quando fare un lavoro: giorno per giorno, nelle ore di lavoro della persona,
     * il tempo non occupato da altri interventi (tolti quelli da spostare) né da appuntamenti, e gli orari (ogni 15 minuti)
     * da cui {@code durataMin} minuti stanno tutti di seguito. Oggi conta solo da adesso in poi.
     */
    @Transactional(readOnly = true)
    public List<GiornoLibero> spaziLiberi(java.util.Collection<Long> daSpostare, Long utenteId, LocalDate dal, int giorni, long durataMin) {
        LocalDate al = dal.plusDays(giorni - 1L);
        Disponibilita d = calendario.disponibilita(dal, al);
        LocalDateTime adesso = LocalDateTime.now(clock);
        Map<LocalDate, List<Intervallo>> occupati = new HashMap<>();
        assegnazioni.tra(dal, al).stream()
                .filter(a -> a.getUtente().getId().equals(utenteId) && !daSpostare.contains(a.getId()))
                .forEach(a -> occupati.computeIfAbsent(a.getData(), k -> new ArrayList<>()).add(a.intervallo()));
        appuntamenti.tra(dal, al, it.boff.bike.appuntamento.StatoAppuntamento.ATTIVI).stream()
                .filter(a -> a.getUtente().getId().equals(utenteId))
                .forEach(a -> occupati.computeIfAbsent(a.getData(), k -> new ArrayList<>()).add(a.intervallo()));
        List<GiornoLibero> risultato = new ArrayList<>();
        for (LocalDate g = dal; !g.isAfter(al); g = g.plusDays(1)) {
            if (g.isBefore(adesso.toLocalDate())) continue;
            LocalTime minimo = g.equals(adesso.toLocalDate()) ? Pianificatore.arrotonda(adesso.toLocalTime()) : LocalTime.MIN;
            if (minimo == null) continue;
            long liberi = 0;
            List<LocalTime> inizi = new ArrayList<>();
            for (Intervallo fascia : d.officina(utenteId, g)) {
                if (!fascia.fine().isAfter(minimo)) continue;
                Intervallo utile = fascia.inizio().isBefore(minimo) ? new Intervallo(minimo, fascia.fine()) : fascia;
                for (Intervallo libero : utile.meno(occupati.getOrDefault(g, List.of()))) {
                    liberi += libero.minuti();
                    LocalTime t = Pianificatore.arrotonda(libero.inizio());
                    while (t != null && !t.plusMinutes(durataMin).isAfter(libero.fine()) && t.plusMinutes(durataMin).isAfter(t)) {
                        inizi.add(t);
                        LocalTime dopo = t.plusMinutes(15);
                        t = dopo.isAfter(t) ? dopo : null;
                    }
                }
            }
            risultato.add(new GiornoLibero(g, liberi, inizi));
        }
        return risultato;
    }

    @Transactional(readOnly = true)
    public List<Assegnazione> diLavoro(Long lavoroId) {
        return assegnazioni.diLavoro(lavoroId);
    }

    /** Occupazione dell'officina: minuti assegnati e disponibili da adesso ai prossimi giorni. */
    public record Carico(long assegnati, long capacita) {
        public int percento() {
            return capacita == 0 ? 100 : (int) Math.round(100.0 * assegnati / capacita);
        }
    }

    @Transactional(readOnly = true)
    public Carico carico(int giorni) {
        LocalDateTime adesso = LocalDateTime.now(clock);
        LocalDate oggi = adesso.toLocalDate(), fine = oggi.plusDays(giorni - 1);
        Disponibilita d = calendario.disponibilita(oggi, fine);
        long capacita = 0;
        for (Long p : d.persone()) {
            for (LocalDate g = oggi; !g.isAfter(fine); g = g.plusDays(1)) {
                for (Intervallo i : d.officina(p, g)) {
                    if (g.equals(oggi)) {
                        if (!i.fine().isAfter(adesso.toLocalTime())) continue;
                        capacita += i.inizio().isBefore(adesso.toLocalTime())
                                ? java.time.Duration.between(adesso.toLocalTime(), i.fine()).toMinutes() : i.minuti();
                    } else {
                        capacita += i.minuti();
                    }
                }
            }
        }
        long assegnati = assegnazioni.tra(oggi, fine).stream()
                .filter(a -> a.getData().isAfter(oggi) || a.getOraFine().isAfter(adesso.toLocalTime()))
                .mapToLong(a -> a.intervallo().minuti()).sum();
        return new Carico(assegnati, capacita);
    }

    /** Minuti già assegnati per persona e giorno nel periodo. */
    @Transactional(readOnly = true)
    public Map<String, Long> minutiAssegnati(LocalDate dal, LocalDate al) {
        return assegnazioni.tra(dal, al).stream().collect(Collectors.groupingBy(
                a -> a.getUtente().getId() + "|" + a.getData(), Collectors.summingLong(a -> a.intervallo().minuti())));
    }
}
