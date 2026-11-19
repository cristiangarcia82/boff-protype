package it.boff.bike.calendario;

import it.boff.bike.common.ErroreDominio;
import it.boff.bike.config.BoffProperties;
import it.boff.bike.pianificazione.Ripianificazione;
import it.boff.bike.utente.Ruolo;
import it.boff.bike.utente.Utente;
import it.boff.bike.utente.UtenteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Orari settimanali del personale, assenze, chiusure e calcolo della disponibilità. */
@Service
@Transactional
public class CalendarioService {

    private final FasciaOrariaRepository fasce;
    private final AssenzaRepository assenze;
    private final ChiusuraRepository chiusure;
    private final UtenteRepository utenti;
    private final BoffProperties properties;
    private final Clock clock;
    private final Ripianificazione ripianificazione;
    private final TurnoVenditaRepository turni;

    public CalendarioService(FasciaOrariaRepository fasce, AssenzaRepository assenze, ChiusuraRepository chiusure,
                             UtenteRepository utenti, BoffProperties properties, Clock clock,
                             Ripianificazione ripianificazione, TurnoVenditaRepository turni) {
        this.turni = turni;
        this.ripianificazione = ripianificazione;
        this.fasce = fasce;
        this.assenze = assenze;
        this.chiusure = chiusure;
        this.utenti = utenti;
        this.properties = properties;
        this.clock = clock;
    }

    public LocalDate oggi() {
        return LocalDate.now(clock);
    }

    // ---------------------------------------------------------------- orari

    @Transactional(readOnly = true)
    public List<FasciaOraria> orarioDi(Long utenteId) {
        return fasce.diUtente(utenteId);
    }

    /** Sostituisce l'orario settimanale di una persona. Le fasce dello stesso giorno non si devono sovrapporre. */
    public void salvaOrario(Long utenteId, List<NuovaFascia> nuove) {
        Utente persona = utenti.findById(utenteId)
                .filter(u -> u.getRuolo() == Ruolo.STAFF)
                .orElseThrow(() -> new ErroreDominio("Gli orari si impostano solo per il personale"));
        Map<DayOfWeek, List<NuovaFascia>> perGiorno = nuove.stream().collect(Collectors.groupingBy(NuovaFascia::giorno));
        perGiorno.forEach((giorno, elenco) -> {
            List<NuovaFascia> ordinate = elenco.stream().sorted(Comparator.comparing(NuovaFascia::inizio)).toList();
            for (int i = 0; i < ordinate.size(); i++) {
                NuovaFascia f = ordinate.get(i);
                if (f.inizio() == null || f.fine() == null || f.tipo() == null) {
                    throw new ErroreDominio("Completa tutte le fasce di " + nome(giorno));
                }
                if (!f.fine().isAfter(f.inizio())) {
                    throw new ErroreDominio("Una fascia di " + nome(giorno) + " finisce prima di iniziare");
                }
                if (i > 0 && ordinate.get(i - 1).fine().isAfter(f.inizio())) {
                    throw new ErroreDominio("Le fasce di " + nome(giorno) + " si sovrappongono");
                }
            }
        });
        fasce.eliminaDiUtente(utenteId);
        fasce.flush();
        nuove.forEach(f -> fasce.save(new FasciaOraria(persona, f.giorno(), f.inizio(), f.fine(), f.tipo())));
        ripianificazione.richiedi("orario modificato");
    }

    // ---------------------------------------------------------------- assenze

    public Assenza aggiungiAssenza(Long utenteId, LocalDate dal, LocalDate al, MotivoAssenza motivo, String note) {
        return aggiungiAssenza(utenteId, dal, al, null, null, motivo, note);
    }

    /**
     * Assenza di una o più giornate; con {@code dalle}/{@code alle} solo per parte di un giorno
     * (in quel caso {@code dal} e {@code al} devono coincidere).
     */
    public Assenza aggiungiAssenza(Long utenteId, LocalDate dal, LocalDate al, java.time.LocalTime dalle,
                                   java.time.LocalTime alle, MotivoAssenza motivo, String note) {
        boolean parziale = dalle != null || alle != null;
        if (parziale) {
            if (dalle == null || alle == null || !alle.isAfter(dalle)) {
                throw new ErroreDominio("Indica un orario di assenza valido (dalle… alle…)");
            }
            if (dal == null || !dal.equals(al)) {
                throw new ErroreDominio("L'assenza di parte della giornata riguarda un solo giorno");
            }
        }
        if (dal == null || al == null || motivo == null) {
            throw new ErroreDominio("Indica persona, date e motivo");
        }
        if (al.isBefore(dal)) {
            throw new ErroreDominio("La data di fine è precedente a quella di inizio");
        }
        Utente persona = utenti.findById(utenteId)
                .filter(u -> u.getRuolo() == Ruolo.STAFF)
                .orElseThrow(() -> new ErroreDominio("Le assenze si registrano solo per il personale"));
        if (assenze.siSovrappone(utenteId, dal, al)) {
            throw new ErroreDominio("C'è già un'assenza registrata in quei giorni");
        }
        ripianificazione.richiedi("nuova assenza");
        String n = note == null || note.isBlank() ? null : note.trim();
        return assenze.save(parziale ? new Assenza(persona, dal, new Intervallo(dalle, alle), motivo, n)
                : new Assenza(persona, dal, al, motivo, n));
    }

    public void rimuoviAssenza(Long assenzaId) {
        assenze.deleteById(assenzaId);
        ripianificazione.richiedi("assenza eliminata");
    }

    @Transactional(readOnly = true)
    public List<Assenza> assenzeDa(LocalDate dal) {
        return assenze.tra(dal, dal.plusYears(2));
    }

    // ---------------------------------------------------------------- chiusure

    public Chiusura aggiungiChiusura(LocalDate data, String descrizione) {
        if (data == null || descrizione == null || descrizione.isBlank()) {
            throw new ErroreDominio("Indica data e motivo della chiusura");
        }
        if (chiusure.existsByData(data)) {
            throw new ErroreDominio("Quel giorno è già segnato come chiusura");
        }
        ripianificazione.richiedi("nuova chiusura");
        return chiusure.save(new Chiusura(data, descrizione.trim()));
    }

    public void rimuoviChiusura(Long chiusuraId) {
        chiusure.deleteById(chiusuraId);
        ripianificazione.richiedi("chiusura eliminata");
    }

    /** Chiusure inserite a mano e festività nazionali tra due date, in ordine. */
    @Transactional(readOnly = true)
    public List<GiornoChiuso> giorniChiusi(LocalDate dal, LocalDate al) {
        List<GiornoChiuso> risultato = new ArrayList<>();
        chiusure.findByDataBetweenOrderByData(dal, al)
                .forEach(c -> risultato.add(new GiornoChiuso(c.getData(), c.getDescrizione(), c.getId())));
        if (properties.festiviChiusi()) {
            for (LocalDate d = dal; !d.isAfter(al); d = d.plusDays(1)) {
                LocalDate giorno = d;
                Festivita.nome(d).filter(n -> risultato.stream().noneMatch(r -> r.data().equals(giorno)))
                        .ifPresent(n -> risultato.add(new GiornoChiuso(giorno, n, null)));
            }
        }
        risultato.sort(Comparator.comparing(GiornoChiuso::data));
        return risultato;
    }

    // ---------------------------------------------------------------- turni di vendita

    /**
     * Il titolare mette una persona al banco in un giorno preciso: dentro il suo orario di lavoro,
     * senza sovrapporsi ad altri turni. Lavori e appuntamenti lasciano libero quel tempo.
     */
    public TurnoVendita aggiungiTurnoVendita(Long utenteId, LocalDate giorno, java.time.LocalTime dalle,
                                             java.time.LocalTime alle, String note) {
        if (dalle == null || alle == null || !alle.isAfter(dalle)) {
            throw new ErroreDominio("L'ora di fine deve essere dopo l'ora di inizio");
        }
        if (giorno.isBefore(oggi())) throw new ErroreDominio("Non si può inserire nel passato");
        Utente persona = utenti.findById(utenteId).filter(u -> u.getRuolo() == Ruolo.STAFF)
                .orElseThrow(() -> new ErroreDominio("Persona non trovata"));
        Intervallo turno = new Intervallo(dalle, alle);
        Disponibilita d = disponibilita(giorno, giorno);
        boolean inOrario = d.fasce(utenteId, giorno).stream()
                .anyMatch(f -> !dalle.isBefore(f.getOraInizio()) && !alle.isAfter(f.getOraFine()));
        if (!inOrario) {
            throw new ErroreDominio(persona.getNome() + " non lavora in quell'orario: il turno deve stare nel suo orario");
        }
        if (d.turniVendita(utenteId, giorno).stream().anyMatch(t -> t.intervallo().siSovrappone(turno))) {
            throw new ErroreDominio("Si sovrappone a un altro turno di vendita");
        }
        String n = note == null || note.isBlank() ? null : note.trim();
        if (n != null && n.length() > 200) throw new ErroreDominio("Nota troppo lunga (massimo 200 caratteri)");
        TurnoVendita t = turni.save(new TurnoVendita(persona, giorno, turno, n));
        ripianificazione.richiedi("turno di vendita");
        return t;
    }

    public void rimuoviTurnoVendita(Long turnoId) {
        turni.deleteById(turnoId);
        ripianificazione.richiedi("turno di vendita eliminato");
    }

    /** Giorno chiuso: {@code chiusuraId} è null per le festività nazionali (non eliminabili). */
    public record GiornoChiuso(LocalDate data, String descrizione, Long chiusuraId) {
    }

    // ---------------------------------------------------------------- disponibilità

    @Transactional(readOnly = true)
    public Disponibilita disponibilita(LocalDate dal, LocalDate al) {
        return new Disponibilita(fasce.diPersonaleAttivo(), assenze.tra(dal, al),
                chiusure.findByDataBetweenOrderByData(dal, al), properties.festiviChiusi(), turni.tra(dal, al));
    }

    private static String nome(DayOfWeek giorno) {
        return giorno.getDisplayName(TextStyle.FULL, Locale.ITALY);
    }
}
