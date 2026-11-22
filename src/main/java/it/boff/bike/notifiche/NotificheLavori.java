package it.boff.bike.notifiche;

import it.boff.bike.lavoro.Lavoro;
import it.boff.bike.lavoro.LavoroCambiato;
import it.boff.bike.lavoro.LavoroService;
import it.boff.bike.lavoro.StatoLavoro;
import it.boff.bike.utente.Utente;
import it.boff.bike.utente.UtenteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Traduce i cambi di stato dei lavori in notifiche. Gira dopo il commit e in un thread
 * separato: un servizio push lento non rallenta mai l'interfaccia.
 * <ul>
 *   <li>al cliente: quando portare la bici (lavoro annunciato fissato dal negozio), bici arrivata in negozio,
 *       data prevista (prima pianificazione), bici pronta;</li>
 *   <li>al personale: nuovo lavoro annunciato da un cliente.</li>
 * </ul>
 */
@Component
public class NotificheLavori {

    private static final Logger log = LoggerFactory.getLogger(NotificheLavori.class);
    private static final DateTimeFormatter GIORNO = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALY);
    private static final DateTimeFormatter ORA = DateTimeFormatter.ofPattern("H:mm", Locale.ITALY);

    private final NotificheService notifiche;
    private final LavoroService lavori;
    private final UtenteService utenti;

    public NotificheLavori(NotificheService notifiche, LavoroService lavori, UtenteService utenti) {
        this.notifiche = notifiche;
        this.lavori = lavori;
        this.utenti = utenti;
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void suCambio(LavoroCambiato evento) {
        try {
            Messaggio m = messaggio(evento);
            if (m == null) return;
            if (m.alPersonale()) {
                for (Utente s : utenti.personale()) {
                    if (s.isAttivo()) notifiche.notifica(s.getId(), m.titolo(), m.testo(), false);
                }
            } else {
                notifiche.notifica(m.destinatario(), m.titolo(), m.testo(), m.emailDiRiserva());
            }
        } catch (RuntimeException e) {
            log.warn("Notifica per il lavoro {} non inviata: {}", evento.lavoroId(), e.getMessage());
        }
    }

    /** Funzionalità "approvazione lavori extra": richiesta al cliente (notifica, o email di riserva). */
    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void suProposta(it.boff.bike.lavoro.InterventoExtraProposto evento) {
        try {
            Lavoro l = lavori.dettaglio(evento.lavoroId());
            l.getRighe().stream().filter(r -> r.getId().equals(evento.rigaId())).findFirst().ifPresent(r -> {
                String testo = "Sulla tua " + l.getBicicletta().getNome() + " servirebbe anche: " + r.getDescrizione()
                        + (r.getNotaApprovazione() == null ? "" : " (" + r.getNotaApprovazione() + ")")
                        + ". Apri l'app per approvare o rifiutare.";
                notifiche.notifica(l.getBicicletta().getProprietario().getId(), "Serve il tuo ok", testo, true);
            });
        } catch (RuntimeException e) {
            log.warn("Richiesta di approvazione per il lavoro {} non inviata: {}", evento.lavoroId(), e.getMessage());
        }
    }

    /** Lavoro annunciato messo in calendario dal negozio: al cliente, quando portare la bici (email di riserva). */
    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void suFissatoPrimaDellArrivo(it.boff.bike.pianificazione.LavoroAnnunciatoFissato evento) {
        try {
            Messaggio m = messaggioArrivo(lavori.dettaglio(evento.lavoroId()));
            if (m != null) notifiche.notifica(m.destinatario(), m.titolo(), m.testo(), m.emailDiRiserva());
        } catch (RuntimeException e) {
            log.warn("Data di consegna della bici per il lavoro {} non notificata: {}", evento.lavoroId(), e.getMessage());
        }
    }

    /** Pacchetto per i test. */
    public static Messaggio messaggioArrivo(Lavoro l) {
        if (l.getStato() != StatoLavoro.ANNUNCIATO || l.getArrivoPrevisto() == null) return null;
        String testo = "Ti aspettiamo con la tua " + l.getBicicletta().getNome() + " " + GIORNO.format(l.getArrivoPrevisto())
                + " entro le " + ORA.format(l.getArrivoPrevisto()) + "."
                + (l.getFinePrevista() == null ? "" : " Sarà pronta indicativamente " + GIORNO.format(l.getFinePrevista()) + ".");
        return new Messaggio(l.getBicicletta().getProprietario().getId(), false, "Quando portarci la bici", testo, true);
    }

    /** Risposta del cliente: avviso al personale. */
    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void suRisposta(it.boff.bike.lavoro.InterventoExtraRisposto evento) {
        try {
            Lavoro l = lavori.dettaglio(evento.lavoroId());
            String descr = l.getRighe().stream().filter(r -> r.getId().equals(evento.rigaId()))
                    .map(it.boff.bike.lavoro.RigaLavoro::getDescrizione).findFirst().orElse("intervento extra");
            String testo = l.getBicicletta().getProprietario().getNomeCompleto() + " ha "
                    + (evento.approvato() ? "approvato" : "rifiutato") + ": " + descr + " (" + l.getBicicletta().getNome() + ")";
            for (Utente s : utenti.personale()) {
                if (s.isAttivo()) notifiche.notifica(s.getId(), evento.approvato() ? "Intervento approvato" : "Intervento rifiutato", testo, false);
            }
        } catch (RuntimeException e) {
            log.warn("Risposta per il lavoro {} non notificata: {}", evento.lavoroId(), e.getMessage());
        }
    }

    public record Messaggio(Long destinatario, boolean alPersonale, String titolo, String testo, boolean emailDiRiserva) {
    }

    /** Quale messaggio inviare per un evento (null = nessuno). Pacchetto per i test. */
    Messaggio messaggio(LavoroCambiato e) {
        if (e.dopo() == e.prima()) return null; // solo cambi di stato veri (non ogni ricalcolo della data)
        Lavoro l = lavori.dettaglio(e.lavoroId());
        String bici = l.getBicicletta().getNome();
        Utente cliente = l.getBicicletta().getProprietario();
        return switch (e.dopo()) {
            case ANNUNCIATO -> new Messaggio(null, true, "Nuovo lavoro annunciato",
                    bici + " di " + cliente.getNomeCompleto() + ": " + l.getRighe().size() + " interventi", false);
            case IN_NEGOZIO -> e.prima() == StatoLavoro.ANNUNCIATO
                    ? new Messaggio(cliente.getId(), false, "Bici ricevuta",
                    "Abbiamo ricevuto la tua " + bici + ". Ti diremo presto quando sarà pronta.", false)
                    : null;
            case PIANIFICATO -> e.prima() == StatoLavoro.IN_NEGOZIO && l.getFinePrevista() != null
                    ? new Messaggio(cliente.getId(), false, "Data prevista",
                    "La tua " + bici + " sarà pronta indicativamente " + GIORNO.format(l.getFinePrevista()) + ".", false)
                    : null;
            case PRONTO -> new Messaggio(cliente.getId(), false, "La tua bici è pronta!",
                    "La tua " + bici + " è pronta: passa a ritirarla quando vuoi.", true);
            default -> null;
        };
    }
}
