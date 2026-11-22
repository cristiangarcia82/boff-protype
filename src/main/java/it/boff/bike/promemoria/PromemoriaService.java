package it.boff.bike.promemoria;

import it.boff.bike.bicicletta.Bicicletta;
import it.boff.bike.funzionalita.Funzionalita;
import it.boff.bike.funzionalita.FunzionalitaService;
import it.boff.bike.lavoro.StatoLavoro;
import it.boff.bike.notifiche.NotificheService;
import it.boff.bike.utente.Utente;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Funzionalità "promemoria di manutenzione": N mesi dopo l'ultimo ritiro di una bici, il
 * cliente riceve un invito al tagliando (notifica, o email se non ha le notifiche attive).
 * Un solo promemoria per ogni ritiro; niente promemoria se c'è già un lavoro aperto o se il
 * cliente li ha disattivati dal profilo.
 */
@Service
public class PromemoriaService {

    private static final Logger log = LoggerFactory.getLogger(PromemoriaService.class);

    private final EntityManager em;
    private final FunzionalitaService funzionalita;
    private final NotificheService notifiche;
    private final Clock clock;

    public PromemoriaService(EntityManager em, FunzionalitaService funzionalita, NotificheService notifiche, Clock clock) {
        this.em = em;
        this.funzionalita = funzionalita;
        this.notifiche = notifiche;
        this.clock = clock;
    }

    /** Ogni giorno alle 10:12 (ora del negozio), un orario adatto a ricevere un messaggio. */
    @Scheduled(cron = "0 12 10 * * *", zone = "${boff.fuso-orario:Europe/Zurich}")
    public void giornaliero() {
        if (funzionalita.attiva(Funzionalita.PROMEMORIA_MANUTENZIONE)) {
            int inviati = invia();
            if (inviati > 0) log.info("Promemoria di manutenzione inviati: {}", inviati);
        }
    }

    /** Invia i promemoria dovuti; restituisce quanti. */
    @Transactional
    public int invia() {
        OffsetDateTime adesso = OffsetDateTime.now(clock);
        OffsetDateTime limite = adesso.minusMonths(funzionalita.mesiPromemoria());
        List<Object[]> candidate = em.createQuery("""
                select b, max(l.chiusoIl) from Lavoro l join l.bicicletta b join b.proprietario u
                where l.stato = :ritirato and b.archiviata = false and u.attivo = true and u.promemoriaAttivi = true
                group by b
                having max(l.chiusoIl) < :limite
                """, Object[].class)
                .setParameter("ritirato", StatoLavoro.RITIRATO).setParameter("limite", limite).getResultList();
        int inviati = 0;
        for (Object[] riga : candidate) {
            Bicicletta b = (Bicicletta) riga[0];
            OffsetDateTime ultimoRitiro = (OffsetDateTime) riga[1];
            if (b.getPromemoriaInviatoIl() != null && b.getPromemoriaInviatoIl().isAfter(ultimoRitiro)) {
                continue; // già avvisato per questo ritiro
            }
            Long aperti = em.createQuery("select count(l) from Lavoro l where l.bicicletta = :b and l.stato in :aperti", Long.class)
                    .setParameter("b", b).setParameter("aperti", StatoLavoro.APERTI).getSingleResult();
            if (aperti > 0) continue;
            Utente u = b.getProprietario();
            notifiche.notifica(u.getId(), "È ora del tagliando",
                    "Sono passati più di " + funzionalita.mesiPromemoria() + " mesi dall'ultimo intervento sulla tua "
                            + b.getNome() + ". Annunciaci un tagliando dall'app quando vuoi.", true);
            b.segnaPromemoriaInviato(adesso);
            inviati++;
        }
        return inviati;
    }
}
