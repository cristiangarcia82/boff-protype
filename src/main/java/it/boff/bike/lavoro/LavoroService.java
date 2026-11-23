package it.boff.bike.lavoro;

import it.boff.bike.bicicletta.Bicicletta;
import it.boff.bike.bicicletta.BiciclettaNonTrovataException;
import it.boff.bike.bicicletta.BiciclettaRepository;
import it.boff.bike.catalogo.TipoIntervento;
import it.boff.bike.catalogo.TipoInterventoRepository;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.pianificazione.Ripianificazione;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Lavori di officina. I metodi "…DelCliente" verificano che il lavoro appartenga al
 * cliente; gli altri sono per lo staff (l'accesso è limitato dalle viste con @RolesAllowed).
 */
@Service
@Transactional
public class LavoroService {

    public static final int MAX_NOTE = 1000;

    private final LavoroRepository lavori;
    private final RigaLavoroRepository righe;
    private final BiciclettaRepository biciclette;
    private final TipoInterventoRepository catalogo;
    private final ApplicationEventPublisher eventi;
    private final Clock clock;
    private final Ripianificazione ripianificazione;
    private final it.boff.bike.funzionalita.FunzionalitaService funzionalita;
    private final it.boff.bike.utente.UtenteRepository utenti;

    public LavoroService(LavoroRepository lavori, RigaLavoroRepository righe, BiciclettaRepository biciclette,
                         TipoInterventoRepository catalogo, ApplicationEventPublisher eventi, Clock clock,
                         Ripianificazione ripianificazione,
                         it.boff.bike.funzionalita.FunzionalitaService funzionalita,
                         it.boff.bike.utente.UtenteRepository utenti) {
        this.utenti = utenti;
        this.ripianificazione = ripianificazione;
        this.funzionalita = funzionalita;
        this.lavori = lavori;
        this.righe = righe;
        this.biciclette = biciclette;
        this.catalogo = catalogo;
        this.eventi = eventi;
        this.clock = clock;
    }

    /** Id della persona del negozio collegata (per il foglio lavori: chi ha fatto l'intervento); null se non nota. */
    private Long chiStaLavorando() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return utenti.findByEmail(auth.getName()).filter(u -> u.getRuolo() == it.boff.bike.utente.Ruolo.STAFF)
                .map(it.boff.bike.utente.Utente::getId).orElse(null);
    }

    // =====================================================================
    // Cliente
    // =====================================================================

    /** Il cliente annuncia cosa fare sulla sua bici, prima di portarla in negozio. */
    public Lavoro annuncia(Long clienteId, Long biciId, Collection<Long> tipiIntervento, String note) {
        Bicicletta bici = biciclette.findByIdAndProprietarioIdAndArchiviataFalse(biciId, clienteId)
                .orElseThrow(BiciclettaNonTrovataException::new);
        return crea(bici, StatoLavoro.ANNUNCIATO, tipiIntervento, note);
    }

    /** Il cliente può annullare solo finché la bici non è in negozio. */
    public void annullaDelCliente(Long clienteId, Long lavoroId) {
        Lavoro l = delCliente(clienteId, lavoroId);
        if (l.getStato() != StatoLavoro.ANNUNCIATO) {
            throw new ErroreDominio("La bici è già in negozio: per annullare contatta il negozio");
        }
        cambia(l, x -> x.annulla(adesso()));
    }

    /** Il cliente approva o rifiuta un intervento in più proposto dal negozio. */
    public void rispondiExtra(Long clienteId, Long rigaId, boolean approva) {
        RigaLavoro r = riga(rigaId);
        Lavoro l = r.getLavoro();
        if (!l.getBicicletta().getProprietario().getId().equals(clienteId)) {
            throw new ErroreDominio("Intervento non trovato");
        }
        if (!r.isInAttesaDiApprovazione()) {
            throw new ErroreDominio("Hai già risposto a questa richiesta");
        }
        if (!l.getStato().isAperto()) {
            throw new ErroreDominio("Il lavoro è chiuso");
        }
        r.rispondi(approva, adesso());
        eventi.publishEvent(new InterventoExtraRisposto(l.getId(), r.getId(), approva));
        if (approva) ripianifica("intervento extra approvato");
    }

    /** Il negozio registra la risposta data dal cliente di persona o al telefono. */
    public void registraRispostaExtra(Long rigaId, boolean approva) {
        RigaLavoro r = riga(rigaId);
        if (!r.isInAttesaDiApprovazione()) {
            throw new ErroreDominio("Questo intervento non è in attesa di risposta");
        }
        if (!r.getLavoro().getStato().isAperto()) {
            throw new ErroreDominio("Il lavoro è chiuso");
        }
        r.rispondi(approva, adesso());
        if (approva) ripianifica("intervento extra approvato");
    }

    @Transactional(readOnly = true)
    public List<Lavoro> lavoriDelCliente(Long clienteId) {
        return lavori.delCliente(clienteId);
    }

    /** Privacy: le note scritte dal cliente possono contenere dati personali e vengono cancellate. */
    public void cancellaNoteDelCliente(Long clienteId) {
        lavori.delCliente(clienteId).forEach(Lavoro::cancellaNoteCliente);
    }

    @Transactional(readOnly = true)
    public List<Lavoro> storicoBiciDelCliente(Long clienteId, Long biciId) {
        biciclette.findByIdAndProprietarioIdAndArchiviataFalse(biciId, clienteId)
                .orElseThrow(BiciclettaNonTrovataException::new);
        return lavori.dellaBici(biciId);
    }

    /** Foglio lavori visto dal cliente: solo un lavoro di una sua bici. */
    @Transactional(readOnly = true)
    public Lavoro lavoroDelCliente(Long clienteId, Long lavoroId) {
        return delCliente(clienteId, lavoroId);
    }

    private Lavoro delCliente(Long clienteId, Long lavoroId) {
        Lavoro l = dettaglio(lavoroId);
        if (!l.getBicicletta().getProprietario().getId().equals(clienteId)) {
            throw new ErroreDominio("Lavoro non trovato");
        }
        return l;
    }

    // =====================================================================
    // Staff
    // =====================================================================

    /** Bici portata in negozio senza annuncio: il lavoro nasce già "in negozio". */
    public Lavoro creaAlBanco(Long biciId, Collection<Long> tipiIntervento, String note) {
        Bicicletta bici = biciclette.findById(biciId)
                .filter(b -> !b.isArchiviata())
                .orElseThrow(BiciclettaNonTrovataException::new);
        return crea(bici, StatoLavoro.IN_NEGOZIO, tipiIntervento, note);
    }

    public void checkIn(Long lavoroId) {
        cambia(dettaglio(lavoroId), l -> l.checkIn(adesso()));
    }

    public void inizia(Long lavoroId) {
        cambia(dettaglio(lavoroId), Lavoro::inizia);
    }

    public void segnaPronto(Long lavoroId) {
        Long chi = chiStaLavorando();
        cambia(dettaglio(lavoroId), l -> l.segnaPronto(adesso(), chi));
    }

    public void riapri(Long lavoroId) {
        cambia(dettaglio(lavoroId), Lavoro::riapri);
    }

    public void ritira(Long lavoroId) {
        cambia(dettaglio(lavoroId), l -> l.ritira(adesso()));
    }

    public void annulla(Long lavoroId) {
        cambia(dettaglio(lavoroId), l -> l.annulla(adesso()));
    }

    public void impostaDettagli(Long lavoroId, boolean urgente, LocalDate dataPromessa, String noteInterne) {
        Lavoro l = dettaglio(lavoroId);
        if (!l.getStato().isAperto()) {
            throw new ErroreDominio("Il lavoro è chiuso e non può essere modificato");
        }
        l.setUrgente(urgente);
        l.setDataPromessa(dataPromessa);
        l.setNoteInterne(pulisci(noteInterne, 2000));
        ripianifica("dettagli del lavoro " + lavoroId);
    }

    public RigaLavoro aggiungiDaCatalogo(Long lavoroId, Long tipoId) {
        Lavoro l = dettaglio(lavoroId);
        TipoIntervento t = catalogo.findById(tipoId).orElseThrow(() -> new ErroreDominio("Intervento non trovato"));
        ripianifica("nuovo intervento");
        return l.aggiungiRiga(t, t.getNome(), t.getDurataStandardMin());
    }

    /**
     * Funzionalità "approvazione lavori extra": il negozio propone un intervento in più,
     * che entra in calendario solo dopo il sì del cliente.
     */
    public RigaLavoro proponiExtra(Long lavoroId, Long tipoId, String descrizione, Integer durataMin, String nota) {
        if (!funzionalita.attiva(it.boff.bike.funzionalita.Funzionalita.APPROVAZIONE_EXTRA)) {
            throw new ErroreDominio("L'approvazione dei lavori extra non è attiva (Impostazioni → Funzionalità)");
        }
        Lavoro l = dettaglio(lavoroId);
        if (!StatoLavoro.IN_OFFICINA.contains(l.getStato())) {
            throw new ErroreDominio("Si possono proporre interventi extra solo con la bici in officina");
        }
        RigaLavoro r;
        if (tipoId != null) {
            TipoIntervento t = catalogo.findById(tipoId).orElseThrow(() -> new ErroreDominio("Intervento non trovato"));
            r = l.aggiungiRiga(t, t.getNome(), t.getDurataStandardMin());
        } else {
            if (descrizione == null || descrizione.isBlank() || durataMin == null) {
                throw new ErroreDominio("Descrivi l'intervento e indica i minuti previsti");
            }
            r = l.aggiungiRiga(null, pulisci(descrizione, 200), controllaDurata(durataMin));
        }
        r.proponi(pulisci(nota, 300));
        lavori.flush();
        eventi.publishEvent(new InterventoExtraProposto(l.getId(), r.getId()));
        return r;
    }

    /** Funzionalità "tempo effettivo": minuti realmente impiegati per un intervento fatto. */
    public void registraTempoEffettivo(Long rigaId, int minuti) {
        if (!funzionalita.attiva(it.boff.bike.funzionalita.Funzionalita.TEMPO_EFFETTIVO)) {
            throw new ErroreDominio("La registrazione del tempo effettivo non è attiva");
        }
        RigaLavoro r = riga(rigaId);
        if (!r.isCompletata()) {
            throw new ErroreDominio("Segna prima l'intervento come fatto");
        }
        if (minuti < 1 || minuti > 1440) {
            throw new ErroreDominio("Indica un tempo tra 1 minuto e 24 ore");
        }
        r.setMinutiEffettivi(minuti);
    }

    public RigaLavoro aggiungiLibera(Long lavoroId, String descrizione, int durataMin) {
        if (descrizione == null || descrizione.isBlank()) {
            throw new ErroreDominio("Descrivi l'intervento");
        }
        ripianifica("nuovo intervento");
        return dettaglio(lavoroId).aggiungiRiga(null, pulisci(descrizione, 200), controllaDurata(durataMin));
    }

    public void modificaDurata(Long rigaId, int durataMin) {
        RigaLavoro r = riga(rigaId);
        if (!r.getLavoro().getStato().isAperto()) {
            throw new ErroreDominio("Il lavoro è chiuso e non può essere modificato");
        }
        r.setDurataMin(controllaDurata(durataMin));
        ripianifica("durata modificata");
    }

    /** Segna una riga come fatta (o da rifare). Il primo intervento fatto mette il lavoro "in lavorazione". */
    public void segnaRiga(Long rigaId, boolean completata) {
        RigaLavoro r = riga(rigaId);
        Lavoro l = r.getLavoro();
        if (!StatoLavoro.IN_OFFICINA.contains(l.getStato())) {
            throw new ErroreDominio("Gli interventi si segnano come fatti solo mentre la bici è in officina");
        }
        r.segna(completata, adesso(), chiStaLavorando());
        if (completata && l.getStato() != StatoLavoro.IN_LAVORAZIONE) {
            cambia(l, Lavoro::inizia);
        }
        ripianifica("intervento segnato");
    }

    public void rimuoviRiga(Long rigaId) {
        RigaLavoro r = riga(rigaId);
        r.getLavoro().rimuoviRiga(r);
        ripianifica("intervento rimosso");
    }

    @Transactional(readOnly = true)
    public List<Lavoro> cerca(Collection<StatoLavoro> stati, String testo) {
        List<Lavoro> trovati = lavori.cerca(stati, testo == null ? "" : testo.trim());
        trovati.forEach(Lavoro::caricaRighe); // in blocchi da 50 (@BatchSize)
        return trovati;
    }

    @Transactional(readOnly = true)
    public Lavoro dettaglio(Long lavoroId) {
        return lavori.dettaglio(lavoroId).orElseThrow(() -> new ErroreDominio("Lavoro non trovato"));
    }

    @Transactional(readOnly = true)
    public List<Lavoro> storicoBici(Long biciId) {
        return lavori.dellaBici(biciId);
    }

    /** L'ultima volta che su una bici è stato fatto un certo intervento, e quante volte in tutto. */
    public record UltimoIntervento(String intervento, LocalDate quando, int volte, Long lavoroId) {
    }

    /**
     * Per ogni tipo di intervento fatto su una bici: quando è stato fatto l'ultima volta (giorno in cui la bici
     * è stata pronta, o ritirata) e quante volte in tutto; i più recenti prima. Esclusi i lavori annullati.
     * Serve per decidere il prossimo (es. quanto tempo è passato dall'ultimo cambio catena).
     */
    @Transactional(readOnly = true)
    public List<UltimoIntervento> ultimiInterventi(Long biciId) {
        return ultimiInterventi(biciId, null);
    }

    /** Come {@link #ultimiInterventi(Long)}, senza contare un lavoro (es. quello che si sta guardando). */
    @Transactional(readOnly = true)
    public List<UltimoIntervento> ultimiInterventi(Long biciId, Long escluso) {
        Map<String, UltimoIntervento> perTipo = new java.util.LinkedHashMap<>();
        for (Lavoro l : lavori.dellaBici(biciId)) {
            if (l.getStato() == StatoLavoro.ANNULLATO || l.getId().equals(escluso)) continue;
            LocalDate quando = dataLavoroFatto(l);
            if (quando == null) continue;
            for (RigaLavoro r : l.getRighe()) {
                if (!r.isCompletata()) continue;
                String chiave = r.getTipoIntervento() != null ? "t" + r.getTipoIntervento().getId()
                        : "d" + r.getDescrizione().trim().toLowerCase(java.util.Locale.ITALY);
                UltimoIntervento prima = perTipo.get(chiave);
                if (prima == null) {
                    perTipo.put(chiave, new UltimoIntervento(r.getDescrizione(), quando, 1, l.getId()));
                } else {
                    boolean piuRecente = quando.isAfter(prima.quando());
                    perTipo.put(chiave, new UltimoIntervento(piuRecente ? r.getDescrizione() : prima.intervento(),
                            piuRecente ? quando : prima.quando(), prima.volte() + 1, piuRecente ? l.getId() : prima.lavoroId()));
                }
            }
        }
        return perTipo.values().stream()
                .sorted(java.util.Comparator.comparing(UltimoIntervento::quando).reversed()
                        .thenComparing(UltimoIntervento::intervento))
                .toList();
    }

    /** Il giorno in cui il lavoro si può dire fatto: pronto, ritirato o, se ancora in corso, oggi. */
    private LocalDate dataLavoroFatto(Lavoro l) {
        OffsetDateTime t = l.getProntoIl() != null ? l.getProntoIl() : l.getChiusoIl();
        if (t != null) return t.atZoneSameInstant(clock.getZone()).toLocalDate();
        return StatoLavoro.IN_OFFICINA.contains(l.getStato()) ? LocalDate.now(clock) : null;
    }

    /** Lavori (con righe) negli stati indicati: usato dalla pianificazione. */
    @Transactional(readOnly = true)
    public List<Lavoro> conRighe(Collection<StatoLavoro> stati) {
        List<Lavoro> elenco = lavori.conRighe(stati);
        elenco.forEach(Lavoro::caricaRighe);
        return elenco;
    }

    /**
     * Registra l'esito della pianificazione: fine prevista e stato PIANIFICATO
     * (o ritorno a IN_NEGOZIO se il lavoro non è più in calendario).
     */
    public void applicaPianificazione(Collection<Long> lavoriInOfficina, Map<Long, LocalDate> finePerLavoro) {
        for (Long id : lavoriInOfficina) {
            Lavoro l = dettaglio(id);
            LocalDate fine = finePerLavoro.get(id);
            StatoLavoro prima = l.getStato();
            LocalDate finePrima = l.getFinePrevista();
            if (fine != null) l.segnaPianificato(fine); else l.togliPianificazione();
            if (prima != l.getStato() || !java.util.Objects.equals(finePrima, l.getFinePrevista())) {
                eventi.publishEvent(new LavoroCambiato(l.getId(), prima, l.getStato()));
            }
        }
    }

    /**
     * Lavori annunciati (bici non ancora in negozio): data prevista di fine e quando il cliente deve
     * portare la bici, secondo gli interventi fissati a mano. Senza interventi fissati restano senza date.
     */
    public void applicaPrevisioniAnnunciati(Map<Long, LocalDate> finePerLavoro, Map<Long, java.time.LocalDateTime> arrivoPerLavoro) {
        for (Lavoro l : lavori.conRighe(java.util.EnumSet.of(StatoLavoro.ANNUNCIATO))) {
            LocalDate fine = finePerLavoro.get(l.getId());
            java.time.LocalDateTime arrivo = arrivoPerLavoro.get(l.getId());
            if (fine != null) l.segnaPianificato(fine); else l.togliPianificazione();
            l.impostaArrivoPrevisto(fine == null ? null : arrivo);
        }
    }

    @Transactional(readOnly = true)
    public long conta(Collection<StatoLavoro> stati) {
        return lavori.countByStatoIn(stati);
    }

    // =====================================================================

    private Lavoro crea(Bicicletta bici, StatoLavoro stato, Collection<Long> tipiIntervento, String note) {
        String noteOk = pulisci(note, MAX_NOTE);
        if ((tipiIntervento == null || tipiIntervento.isEmpty()) && noteOk == null) {
            throw new ErroreDominio("Indica almeno un intervento o descrivi cosa va fatto");
        }
        if (lavori.apertoDellaBici(bici.getId()).isPresent()) {
            throw new ErroreDominio("Per questa bici c'è già un lavoro aperto");
        }
        Lavoro l = new Lavoro(bici, stato, noteOk, adesso());
        if (tipiIntervento != null) {
            catalogo.findAllById(tipiIntervento).forEach(t -> l.aggiungiRiga(t, t.getNome(), t.getDurataStandardMin()));
        }
        if (l.getRighe().isEmpty()) {
            // Solo descrizione libera: il negozio stimerà il tempo dopo aver visto la bici
            l.aggiungiRiga(null, "Da valutare in negozio", 30);
        }
        Lavoro salvato = lavori.save(l);
        eventi.publishEvent(new LavoroCambiato(salvato.getId(), null, stato));
        ripianifica("nuovo lavoro");
        return salvato;
    }

    private void cambia(Lavoro l, Consumer<Lavoro> azione) {
        StatoLavoro prima = l.getStato();
        azione.accept(l);
        if (prima != l.getStato()) {
            eventi.publishEvent(new LavoroCambiato(l.getId(), prima, l.getStato()));
            ripianifica("stato del lavoro " + l.getId());
        }
    }

    private void ripianifica(String motivo) {
        ripianificazione.richiedi(motivo);
    }

    private RigaLavoro riga(Long rigaId) {
        return righe.findById(rigaId).orElseThrow(() -> new ErroreDominio("Intervento non trovato"));
    }

    private static int controllaDurata(int minuti) {
        if (minuti < 5 || minuti > 1440) {
            throw new ErroreDominio("La durata deve essere tra 5 minuti e 24 ore");
        }
        return minuti;
    }

    private static String pulisci(String testo, int max) {
        if (testo == null || testo.isBlank()) return null;
        String t = testo.trim();
        if (t.length() > max) {
            throw new ErroreDominio("Testo troppo lungo (massimo " + max + " caratteri)");
        }
        return t;
    }

    private OffsetDateTime adesso() {
        return OffsetDateTime.now(clock);
    }
}
