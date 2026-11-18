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
 *   <li>al cliente: bici arrivata in negozio, data prevista (prima pianificazione), bici pronta;</li>
 *   <li>al personale: nuovo lavoro annunciato da un cliente.</li>
 * </ul>
 */
@Component
public class NotificheLavori {

    private static final Logger log = LoggerFactory.getLogger(NotificheLavori.class);
    private static final DateTimeFormatter GIORNO = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALY);

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

    record Messaggio(Long destinatario, boolean alPersonale, String titolo, String testo, boolean emailDiRiserva) {
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
