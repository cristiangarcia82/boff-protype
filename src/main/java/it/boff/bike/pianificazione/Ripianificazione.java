package it.boff.bike.pianificazione;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Chiede di ricalcolare il calendario. Più richieste nella stessa transazione (es. "segna
 * intervento fatto" che fa anche partire il lavoro) producono <b>un solo</b> ricalcolo,
 * eseguito dopo il commit da {@link PianificazioneService}.
 */
@Component
public class Ripianificazione {

    private static final Object GIA_RICHIESTA = new Object();

    private final ApplicationEventPublisher eventi;

    public Ripianificazione(ApplicationEventPublisher eventi) {
        this.eventi = eventi;
    }

    public void richiedi(String motivo) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            if (TransactionSynchronizationManager.hasResource(GIA_RICHIESTA)) {
                return;
            }
            TransactionSynchronizationManager.bindResource(GIA_RICHIESTA, Boolean.TRUE);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(GIA_RICHIESTA);
                }
            });
        }
        eventi.publishEvent(new RichiestaRipianificazione(motivo));
    }
}
