package it.boff.bike.calendario;

import it.boff.bike.common.ErroreDominio;
import it.boff.bike.config.BoffProperties;
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

    public CalendarioService(FasciaOrariaRepository fasce, AssenzaRepository assenze, ChiusuraRepository chiusure,
                             UtenteRepository utenti, BoffProperties properties, Clock clock) {
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
    }

    // ---------------------------------------------------------------- assenze

    public Assenza aggiungiAssenza(Long utenteId, LocalDate dal, LocalDate al, MotivoAssenza motivo, String note) {
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
        return assenze.save(new Assenza(persona, dal, al, motivo,
                note == null || note.isBlank() ? null : note.trim()));
    }

    public void rimuoviAssenza(Long assenzaId) {
        assenze.deleteById(assenzaId);
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
        return chiusure.save(new Chiusura(data, descrizione.trim()));
    }

    public void rimuoviChiusura(Long chiusuraId) {
        chiusure.deleteById(chiusuraId);
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

    /** Giorno chiuso: {@code chiusuraId} è null per le festività nazionali (non eliminabili). */
    public record GiornoChiuso(LocalDate data, String descrizione, Long chiusuraId) {
    }

    // ---------------------------------------------------------------- disponibilità

    @Transactional(readOnly = true)
    public Disponibilita disponibilita(LocalDate dal, LocalDate al) {
        return new Disponibilita(fasce.diPersonaleAttivo(), assenze.tra(dal, al),
                chiusure.findByDataBetweenOrderByData(dal, al), properties.festiviChiusi());
    }

    private static String nome(DayOfWeek giorno) {
        return giorno.getDisplayName(TextStyle.FULL, Locale.ITALY);
    }
}
