package it.boff.bike.notifiche;

import it.boff.bike.appuntamento.Appuntamento;
import it.boff.bike.appuntamento.AppuntamentoCambiato;
import it.boff.bike.appuntamento.AppuntamentoService;
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
 * Avvisi sugli appuntamenti: al personale una nuova richiesta o un annullamento del cliente,
 * al cliente conferma, spostamento o annullamento (con email di riserva).
 * Un cliente senza app non riceve nulla: lo si avvisa per telefono.
 */
@Component
public class NotificheAppuntamenti {

    private static final Logger log = LoggerFactory.getLogger(NotificheAppuntamenti.class);
    private static final DateTimeFormatter QUANDO = DateTimeFormatter.ofPattern("EEEE d MMMM 'alle' HH:mm", Locale.ITALY);

    private final NotificheService notifiche;
    private final AppuntamentoService appuntamenti;
    private final UtenteService utenti;

    public NotificheAppuntamenti(NotificheService notifiche, AppuntamentoService appuntamenti, UtenteService utenti) {
        this.notifiche = notifiche;
        this.appuntamenti = appuntamenti;
        this.utenti = utenti;
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void suCambio(AppuntamentoCambiato evento) {
        try {
            Appuntamento a = appuntamenti.dettaglio(evento.appuntamentoId());
            String quando = QUANDO.format(a.getData().atTime(a.getOraInizio()));
            String cosa = a.getTipo().getNome();
            Utente cliente = a.getCliente();
            switch (evento.cosa()) {
                case RICHIESTO -> alPersonale("Nuova richiesta di appuntamento",
                        cliente.getNomeCompleto() + ": " + cosa + ", " + quando + ". Da confermare.");
                case ANNULLATO_DAL_CLIENTE -> alPersonale("Appuntamento annullato",
                        cliente.getNomeCompleto() + " ha annullato " + cosa + " di " + quando + ".");
                case CONFERMATO -> notifiche.notifica(cliente.getId(), "Appuntamento confermato",
                        cosa + ": ti aspettiamo " + quando + ".", true);
                case CREATO_DAL_NEGOZIO -> notifiche.notifica(cliente.getId(), "Nuovo appuntamento",
                        cosa + ": ti aspettiamo " + quando + ".", true);
                case SPOSTATO -> notifiche.notifica(cliente.getId(), "Appuntamento spostato",
                        cosa + ": il nuovo orario è " + quando + ".", true);
                case ANNULLATO_DAL_NEGOZIO -> notifiche.notifica(cliente.getId(), "Appuntamento annullato",
                        cosa + " di " + quando + " è stato annullato. Scrivici o chiamaci per fissarne un altro.", true);
            }
        } catch (RuntimeException e) {
            log.warn("Notifica per l'appuntamento {} non inviata: {}", evento.appuntamentoId(), e.getMessage());
        }
    }

    private void alPersonale(String titolo, String testo) {
        for (Utente s : utenti.personale()) {
            if (s.isAttivo()) notifiche.notifica(s.getId(), titolo, testo, false);
        }
    }
}
