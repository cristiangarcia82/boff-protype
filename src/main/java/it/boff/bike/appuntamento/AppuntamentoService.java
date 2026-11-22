package it.boff.bike.appuntamento;

import it.boff.bike.bicicletta.Bicicletta;
import it.boff.bike.bicicletta.BiciclettaRepository;
import it.boff.bike.calendario.CalendarioService;
import it.boff.bike.calendario.Disponibilita;
import it.boff.bike.calendario.Intervallo;
import it.boff.bike.calendario.TipoFascia;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.utente.Ruolo;
import it.boff.bike.utente.Utente;
import it.boff.bike.utente.UtenteRepository;
import jakarta.validation.Valid;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Appuntamenti di consulenza. Stanno nell'orario di lavoro di una persona (tolti assenze e turni di vendita)
 * e hanno la precedenza sui lavori di officina, che si ripianificano attorno; non si sovrappongono agli
 * interventi fissati a mano. Il cliente sceglie giorno e ora fra quelli liberi, il negozio conferma.
 * Chi segue l'appuntamento lo sceglie l'app (la persona libera meno impegnata quel giorno) o il negozio.
 */
@Service
@Transactional
public class AppuntamentoService {

    /** Granularità degli orari proposti. */
    static final int PASSO_MIN = 30;
    /** Preavviso minimo per una richiesta dall'app. */
    static final Duration PREAVVISO = Duration.ofHours(2);
    /** Fin dove si può prenotare dall'app. */
    public static final int GIORNI_PRENOTABILI = 30;
    /** Appuntamenti futuri aperti per cliente. */
    static final int MAX_APERTI_PER_CLIENTE = 3;
    public static final int MAX_NOTE = 1000;

    private final AppuntamentoRepository appuntamenti;
    private final TipoAppuntamentoRepository tipi;
    private final UtenteRepository utenti;
    private final BiciclettaRepository biciclette;
    private final CalendarioService calendario;
    private final ApplicationEventPublisher eventi;
    private final Clock clock;
    private final it.boff.bike.pianificazione.AssegnazioneRepository assegnazioni;
    private final it.boff.bike.pianificazione.Ripianificazione ripianificazione;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    /** Chiave del lock condiviso (appuntamenti e turni di vendita) sull'agenda del personale. */
    public static final long LOCK_AGENDA = 7_312_001L;

    public AppuntamentoService(AppuntamentoRepository appuntamenti, TipoAppuntamentoRepository tipi,
                               UtenteRepository utenti, BiciclettaRepository biciclette, CalendarioService calendario,
                               ApplicationEventPublisher eventi, Clock clock,
                               it.boff.bike.pianificazione.AssegnazioneRepository assegnazioni,
                               it.boff.bike.pianificazione.Ripianificazione ripianificazione,
                               org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.assegnazioni = assegnazioni;
        this.ripianificazione = ripianificazione;
        this.appuntamenti = appuntamenti;
        this.tipi = tipi;
        this.utenti = utenti;
        this.biciclette = biciclette;
        this.calendario = calendario;
        this.eventi = eventi;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- catalogo

    @Transactional(readOnly = true)
    public List<TipoAppuntamento> tipiAttivi() {
        return tipi.findByAttivoTrueOrderByOrdineAscNomeAsc();
    }

    @Transactional(readOnly = true)
    public List<TipoAppuntamento> tuttiITipi() {
        return tipi.findAllByOrderByOrdineAscNomeAsc();
    }

    public TipoAppuntamento salvaTipo(@Valid TipoAppuntamento tipo) {
        tipo.setNome(tipo.getNome().trim());
        if (tipo.getId() == null && tipi.existsByNomeIgnoreCase(tipo.getNome())) {
            throw new ErroreDominio("Esiste già un tipo di appuntamento con questo nome");
        }
        return tipi.save(tipo);
    }

    // ---------------------------------------------------------------- disponibilità

    /** Orari d'inizio liberi per un tipo di appuntamento, giorno per giorno (per chiunque sia libero). */
    @Transactional(readOnly = true)
    public Map<LocalDate, List<LocalTime>> orariLiberi(Long tipoId, LocalDate dal, LocalDate al) {
        TipoAppuntamento tipo = tipo(tipoId);
        Disponibilita d = calendario.disponibilita(dal, al);
        List<Occupato> occupati = occupati(dal, al);
        LocalDateTime minimo = LocalDateTime.now(clock).plus(PREAVVISO);
        Map<LocalDate, List<LocalTime>> risultato = new TreeMap<>();
        for (LocalDate g = dal; !g.isAfter(al); g = g.plusDays(1)) {
            TreeSet<LocalTime> orari = new TreeSet<>();
            for (Long p : d.persone()) {
                orari.addAll(inizi(d, occupati, p, g, tipo.getDurataMin(), null, minimo));
            }
            if (!orari.isEmpty()) risultato.put(g, List.copyOf(orari));
        }
        return risultato;
    }

    /** Orari d'inizio liberi per una persona precisa (creazione o spostamento dal negozio). */
    @Transactional(readOnly = true)
    public List<LocalTime> orariLiberiDi(Long utenteId, LocalDate giorno, int durataMin, Long esclusoAppuntamento) {
        Disponibilita d = calendario.disponibilita(giorno, giorno);
        return inizi(d, occupati(giorno, giorno), utenteId, giorno, durataMin, esclusoAppuntamento, LocalDateTime.now(clock));
    }

    /** Tempo già preso: appuntamenti attivi e interventi di officina fissati a mano. */
    record Occupato(Long utenteId, LocalDate data, Intervallo intervallo, Long appuntamentoId) {
    }

    private List<Occupato> occupati(LocalDate dal, LocalDate al) {
        List<Occupato> o = new ArrayList<>();
        appuntamenti.tra(dal, al, StatoAppuntamento.ATTIVI)
                .forEach(a -> o.add(new Occupato(a.getUtente().getId(), a.getData(), a.intervallo(), a.getId())));
        assegnazioni.bloccateTra(dal, al)
                .forEach(a -> o.add(new Occupato(a.getUtente().getId(), a.getData(), a.intervallo(), null)));
        return o;
    }

    /** Orario di lavoro della persona (al netto di assenze e turni di vendita), meno ciò che è già preso. */
    private static List<LocalTime> inizi(Disponibilita d, List<Occupato> occupati, Long persona, LocalDate g,
                                         int durata, Long escluso, LocalDateTime minimo) {
        List<Intervallo> presi = occupati.stream()
                .filter(a -> a.utenteId().equals(persona) && a.data().equals(g)
                        && (a.appuntamentoId() == null || !a.appuntamentoId().equals(escluso)))
                .map(Occupato::intervallo).toList();
        List<LocalTime> risultato = new ArrayList<>();
        for (Intervallo lavoro : d.officina(persona, g)) {
            for (Intervallo libero : lavoro.meno(presi)) {
                LocalTime t = arrotonda(libero.inizio());
                while (t != null && !t.plusMinutes(durata).isAfter(libero.fine()) && t.plusMinutes(durata).isAfter(t)) {
                    if (!LocalDateTime.of(g, t).isBefore(minimo)) risultato.add(t);
                    LocalTime dopo = t.plusMinutes(PASSO_MIN);
                    t = dopo.isAfter(t) ? dopo : null;
                }
            }
        }
        return risultato;
    }

    /** Arrotonda alla mezz'ora successiva (09:10 → 09:30), lasciando invariati gli orari già tondi. */
    static LocalTime arrotonda(LocalTime t) {
        int resto = (t.getHour() * 60 + t.getMinute()) % PASSO_MIN;
        if (resto == 0 && t.getSecond() == 0) return t.withSecond(0).withNano(0);
        LocalTime r = t.withSecond(0).withNano(0).plusMinutes(PASSO_MIN - resto);
        return r.isAfter(t) ? r : null;
    }

    // ---------------------------------------------------------------- richieste e gestione

    /** Il cliente chiede un appuntamento dall'app: lo spazio è tenuto, il negozio conferma. */
    public Appuntamento richiedi(Long clienteId, Long tipoId, LocalDate giorno, LocalTime ora, Long biciId, String note) {
        bloccaAgenda();
        Utente cliente = cliente(clienteId);
        if (!cliente.isAttivo()) throw new ErroreDominio("Account non attivo");
        LocalDate oggi = calendario.oggi();
        if (giorno.isAfter(oggi.plusDays(GIORNI_PRENOTABILI))) {
            throw new ErroreDominio("Si può prenotare al massimo " + GIORNI_PRENOTABILI + " giorni avanti");
        }
        if (appuntamenti.apertiDelCliente(clienteId, oggi, StatoAppuntamento.ATTIVI) >= MAX_APERTI_PER_CLIENTE) {
            throw new ErroreDominio("Hai già " + MAX_APERTI_PER_CLIENTE + " appuntamenti in programma: "
                    + "annullane uno o chiama il negozio");
        }
        TipoAppuntamento tipo = tipo(tipoId);
        if (!tipo.isAttivo()) throw new ErroreDominio("Questo tipo di appuntamento non è più disponibile");
        Bicicletta bici = biciDelCliente(clienteId, biciId);
        Utente persona = personaLibera(giorno, ora, tipo.getDurataMin())
                .orElseThrow(() -> new ErroreDominio("Quest'orario non è più libero: scegline un altro"));
        Appuntamento a = appuntamenti.save(new Appuntamento(cliente, tipo, persona, bici, giorno,
                new Intervallo(ora, ora.plusMinutes(tipo.getDurataMin())), pulisci(note), true, adesso()));
        eventi.publishEvent(new AppuntamentoCambiato(a.getId(), AppuntamentoCambiato.Cosa.RICHIESTO));
        ripianificazione.richiedi("appuntamento richiesto");
        return a;
    }

    /** Il negozio fissa un appuntamento (già confermato). Persona null = la sceglie l'app. */
    public Appuntamento crea(Long clienteId, Long tipoId, Long utenteId, LocalDate giorno, LocalTime ora, String note) {
        bloccaAgenda();
        Utente cliente = cliente(clienteId);
        TipoAppuntamento tipo = tipo(tipoId);
        if (LocalDateTime.of(giorno, ora).isBefore(LocalDateTime.now(clock))) {
            throw new ErroreDominio("Non si può fissare un appuntamento in un orario già passato");
        }
        Utente persona = utenteId == null
                ? personaLiberaNegozio(giorno, ora, tipo.getDurataMin())
                    .orElseThrow(() -> new ErroreDominio("Nessuno è libero a quell'ora"))
                : personaSeLibera(utenteId, giorno, ora, tipo.getDurataMin(), null);
        Appuntamento a = appuntamenti.save(new Appuntamento(cliente, tipo, persona, null, giorno,
                new Intervallo(ora, ora.plusMinutes(tipo.getDurataMin())), null, false, adesso()));
        a.setNoteInterne(pulisci(note));
        eventi.publishEvent(new AppuntamentoCambiato(a.getId(), AppuntamentoCambiato.Cosa.CREATO_DAL_NEGOZIO));
        ripianificazione.richiedi("appuntamento fissato");
        return a;
    }

    public void conferma(Long id) {
        Appuntamento a = appuntamento(id);
        if (iniziato(a)) throw new ErroreDominio("L'orario è già passato: proponi un altro orario o rifiuta la richiesta");
        a.conferma(adesso());
        eventi.publishEvent(new AppuntamentoCambiato(id, AppuntamentoCambiato.Cosa.CONFERMATO));
    }

    /** Il negozio sposta un appuntamento (persona, giorno, ora): resta nell'orario di lavoro, vale come confermato. */
    public void sposta(Long id, Long utenteId, LocalDate giorno, LocalTime ora) {
        bloccaAgenda();
        Appuntamento a = appuntamento(id);
        if (LocalDateTime.of(giorno, ora).isBefore(LocalDateTime.now(clock))) {
            throw new ErroreDominio("Non si può spostare in un orario già passato");
        }
        int durata = (int) a.intervallo().minuti();
        Utente persona = personaSeLibera(utenteId, giorno, ora, durata, id);
        a.sposta(persona, giorno, new Intervallo(ora, ora.plusMinutes(durata)), adesso());
        eventi.publishEvent(new AppuntamentoCambiato(id, AppuntamentoCambiato.Cosa.SPOSTATO));
        ripianificazione.richiedi("appuntamento spostato");
    }

    public void annulla(Long id) {
        appuntamento(id).annulla(adesso());
        eventi.publishEvent(new AppuntamentoCambiato(id, AppuntamentoCambiato.Cosa.ANNULLATO_DAL_NEGOZIO));
        ripianificazione.richiedi("appuntamento annullato");
    }

    public void annullaDelCliente(Long clienteId, Long id) {
        Appuntamento a = appuntamento(id);
        if (!a.getCliente().getId().equals(clienteId)) throw new ErroreDominio("Appuntamento non trovato");
        if (LocalDateTime.of(a.getData(), a.getOraInizio()).isBefore(LocalDateTime.now(clock))) {
            throw new ErroreDominio("L'appuntamento è già passato");
        }
        a.annulla(adesso());
        eventi.publishEvent(new AppuntamentoCambiato(id, AppuntamentoCambiato.Cosa.ANNULLATO_DAL_CLIENTE));
        ripianificazione.richiedi("appuntamento annullato");
    }

    public void segnaSvolto(Long id) {
        Appuntamento a = appuntamento(id);
        if (!iniziato(a)) throw new ErroreDominio("L'appuntamento non è ancora iniziato");
        a.segnaSvolto(adesso());
    }

    /** True se l'appuntamento è già cominciato (o passato), nel fuso del negozio. */
    public boolean iniziato(Appuntamento a) {
        return !LocalDateTime.of(a.getData(), a.getOraInizio()).isAfter(LocalDateTime.now(clock));
    }

    public void impostaNoteInterne(Long id, String note) {
        appuntamento(id).setNoteInterne(pulisci(note));
    }

    // ---------------------------------------------------------------- letture

    @Transactional(readOnly = true)
    public List<Appuntamento> tra(LocalDate dal, LocalDate al) {
        return appuntamenti.tra(dal, al, StatoAppuntamento.ATTIVI);
    }

    /** Per il calendario: attivi e anche svolti (si rivedono a posteriori), non gli annullati. */
    @Transactional(readOnly = true)
    public List<Appuntamento> perCalendario(LocalDate dal, LocalDate al) {
        return appuntamenti.tra(dal, al, java.util.EnumSet.of(StatoAppuntamento.RICHIESTO, StatoAppuntamento.CONFERMATO,
                StatoAppuntamento.SVOLTO));
    }

    @Transactional(readOnly = true)
    public List<Appuntamento> daConfermare() {
        // anche quelle rimaste senza risposta negli ultimi 14 giorni: vanno rifiutate o spostate, non perse
        return appuntamenti.daConfermare(calendario.oggi().minusDays(14));
    }

    /** Confermati degli ultimi 14 giorni già finiti e non ancora segnati come svolti. */
    @Transactional(readOnly = true)
    public List<Appuntamento> daChiudere() {
        LocalDate oggi = calendario.oggi();
        LocalDateTime adesso = LocalDateTime.now(clock);
        return appuntamenti.tra(oggi.minusDays(14), oggi, java.util.EnumSet.of(StatoAppuntamento.CONFERMATO)).stream()
                .filter(a -> !LocalDateTime.of(a.getData(), a.getOraFine()).isAfter(adesso))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Appuntamento> delCliente(Long clienteId) {
        return appuntamenti.delCliente(clienteId);
    }

    @Transactional(readOnly = true)
    public Appuntamento dettaglio(Long id) {
        Appuntamento a = appuntamento(id);
        org.hibernate.Hibernate.initialize(a.getCliente());
        org.hibernate.Hibernate.initialize(a.getTipo());
        org.hibernate.Hibernate.initialize(a.getUtente());
        if (a.getBicicletta() != null) org.hibernate.Hibernate.initialize(a.getBicicletta());
        return a;
    }

    /** Cancellazione dei dati del cliente: annulla quelli futuri e toglie note e bici da tutti. */
    public void perCancellazioneCliente(Long clienteId) {
        OffsetDateTime adesso = adesso();
        boolean liberato = false;
        for (Appuntamento a : appuntamenti.delCliente(clienteId)) {
            if (a.isAttivo()) {
                a.annulla(adesso);
                eventi.publishEvent(new AppuntamentoCambiato(a.getId(), AppuntamentoCambiato.Cosa.ANNULLATO_DAL_CLIENTE));
                liberato = true;
            }
            a.cancellaNoteCliente();
        }
        if (liberato) ripianificazione.richiedi("appuntamenti annullati con la cancellazione dei dati");
    }

    // ---------------------------------------------------------------- interni

    /**
     * Serializza le prenotazioni fino al commit della transazione (lock "advisory" di PostgreSQL):
     * due richieste contemporanee per lo stesso orario non possono passare entrambe il controllo.
     * Un lock in memoria rilasciato prima del commit non basterebbe.
     */
    private void bloccaAgenda() {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?)", LOCK_AGENDA);
    }

    /** Persona libera per una richiesta dall'app (con preavviso): la meno impegnata quel giorno. */
    private Optional<Utente> personaLibera(LocalDate giorno, LocalTime ora, int durata) {
        if (LocalDateTime.of(giorno, ora).isBefore(LocalDateTime.now(clock).plus(PREAVVISO))) return Optional.empty();
        return personaLiberaNegozio(giorno, ora, durata);
    }

    private Optional<Utente> personaLiberaNegozio(LocalDate giorno, LocalTime ora, int durata) {
        Disponibilita d = calendario.disponibilita(giorno, giorno);
        List<Occupato> occupati = occupati(giorno, giorno);
        return d.personale().stream()
                .filter(p -> entra(d, occupati, p.getId(), giorno, ora, durata, null))
                .min(Comparator.comparingLong(p -> occupati.stream()
                        .filter(a -> a.utenteId().equals(p.getId()) && a.appuntamentoId() != null).count()));
    }

    private Utente personaSeLibera(Long utenteId, LocalDate giorno, LocalTime ora, int durata, Long escluso) {
        Utente persona = utenti.findById(utenteId).filter(u -> u.getRuolo() == Ruolo.STAFF && u.isAttivo())
                .orElseThrow(() -> new ErroreDominio("Persona non trovata"));
        Disponibilita d = calendario.disponibilita(giorno, giorno);
        if (!entra(d, occupati(giorno, giorno), utenteId, giorno, ora, durata, escluso)) {
            throw new ErroreDominio(persona.getNome() + " non è libero/a alle " + ora
                    + " per " + durata + " minuti (fuori orario, turno di vendita, altro appuntamento o intervento fissato)");
        }
        return persona;
    }

    /** true se [ora, ora+durata) sta nell'orario di lavoro della persona e non tocca nulla di già preso. */
    private static boolean entra(Disponibilita d, List<Occupato> occupati, Long persona, LocalDate g,
                                 LocalTime ora, int durata, Long escluso) {
        LocalTime fine = ora.plusMinutes(durata);
        if (!fine.isAfter(ora)) return false;
        Intervallo voluto = new Intervallo(ora, fine);
        boolean inOrario = d.officina(persona, g).stream()
                .anyMatch(v -> !ora.isBefore(v.inizio()) && !fine.isAfter(v.fine()));
        boolean libero = occupati.stream().noneMatch(a -> a.utenteId().equals(persona) && a.data().equals(g)
                && (a.appuntamentoId() == null || !a.appuntamentoId().equals(escluso))
                && a.intervallo().siSovrappone(voluto));
        return inOrario && libero;
    }

    private Bicicletta biciDelCliente(Long clienteId, Long biciId) {
        if (biciId == null) return null;
        return biciclette.findById(biciId).filter(b -> b.getProprietario().getId().equals(clienteId))
                .orElseThrow(() -> new ErroreDominio("Bici non trovata"));
    }

    private Utente cliente(Long id) {
        return utenti.findById(id).filter(u -> u.getRuolo() == Ruolo.CLIENTE && !u.isEliminato())
                .orElseThrow(() -> new ErroreDominio("Cliente non trovato"));
    }

    private TipoAppuntamento tipo(Long id) {
        return tipi.findById(id).orElseThrow(() -> new ErroreDominio("Tipo di appuntamento non trovato"));
    }

    private Appuntamento appuntamento(Long id) {
        return appuntamenti.findById(id).orElseThrow(() -> new ErroreDominio("Appuntamento non trovato"));
    }

    private OffsetDateTime adesso() {
        return OffsetDateTime.now(clock);
    }

    private static String pulisci(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        if (t.length() > MAX_NOTE) throw new ErroreDominio("Note troppo lunghe (massimo " + MAX_NOTE + " caratteri)");
        return t;
    }
}
