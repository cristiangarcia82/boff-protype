package it.boff.bike.statistiche;

import it.boff.bike.config.BoffProperties;
import it.boff.bike.lavoro.StatoLavoro;
import it.boff.bike.utente.Ruolo;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Numeri di sintesi sull'attività dell'officina in un periodo (solo per il titolare). */
@Service
@Transactional(readOnly = true)
public class StatisticheService {

    public record Intervento(String descrizione, long volte, long minutiTotali) {
    }

    /**
     * @param giorniMediInNegozio  dal check-in alla bici pronta (null se nessun dato)
     * @param puntualitaPercento   bici pronte entro la data promessa, sul totale con data promessa (null se nessuna)
     */
    public record Riepilogo(int giorni, long nuoviLavori, long ritirati, long annullati, long nuoviClienti,
                            Double giorniMediInNegozio, Integer puntualitaPercento, long conDataPromessa,
                            List<Intervento> interventiFrequenti) {
    }

    private final EntityManager em;
    private final Clock clock;
    private final BoffProperties properties;

    public StatisticheService(EntityManager em, Clock clock, BoffProperties properties) {
        this.em = em;
        this.clock = clock;
        this.properties = properties;
    }

    public Riepilogo ultimiGiorni(int giorni) {
        OffsetDateTime dal = OffsetDateTime.now(clock).minusDays(giorni);

        long nuovi = conta("select count(l) from Lavoro l where l.annunciatoIl >= :dal", dal);
        long ritirati = em.createQuery("select count(l) from Lavoro l where l.stato = :s and l.chiusoIl >= :dal", Long.class)
                .setParameter("s", StatoLavoro.RITIRATO).setParameter("dal", dal).getSingleResult();
        long annullati = em.createQuery("select count(l) from Lavoro l where l.stato = :s and l.chiusoIl >= :dal", Long.class)
                .setParameter("s", StatoLavoro.ANNULLATO).setParameter("dal", dal).getSingleResult();
        long clienti = em.createQuery("select count(u) from Utente u where u.ruolo = :r and u.creatoIl >= :dal", Long.class)
                .setParameter("r", Ruolo.CLIENTE).setParameter("dal", dal).getSingleResult();

        List<Object[]> pronti = em.createQuery("""
                select l.checkinIl, l.prontoIl, l.dataPromessa from Lavoro l
                where l.prontoIl is not null and l.prontoIl >= :dal
                """, Object[].class).setParameter("dal", dal).getResultList();
        Double media = pronti.stream().filter(r -> r[0] != null)
                .mapToDouble(r -> Duration.between((OffsetDateTime) r[0], (OffsetDateTime) r[1]).toMinutes() / 1440.0)
                .average().stream().boxed().findFirst().orElse(null);
        List<Object[]> promessi = pronti.stream().filter(r -> r[2] != null).toList();
        long puntuali = promessi.stream().filter(r -> !((OffsetDateTime) r[1]).atZoneSameInstant(properties.fusoOrario())
                .toLocalDate().isAfter((LocalDate) r[2])).count();
        Integer puntualita = promessi.isEmpty() ? null : (int) Math.round(100.0 * puntuali / promessi.size());

        List<Intervento> frequenti = em.createQuery("""
                select new it.boff.bike.statistiche.StatisticheService$Intervento(r.descrizione, count(r), sum(r.durataMin))
                from RigaLavoro r where r.lavoro.annunciatoIl >= :dal and r.lavoro.stato <> :annullato
                  and (r.approvazione is null or r.approvazione = it.boff.bike.lavoro.Approvazione.APPROVATA)
                group by r.descrizione order by count(r) desc, r.descrizione
                """, Intervento.class)
                .setParameter("dal", dal).setParameter("annullato", StatoLavoro.ANNULLATO)
                .setMaxResults(10).getResultList();

        return new Riepilogo(giorni, nuovi, ritirati, annullati, clienti, media, puntualita, promessi.size(), frequenti);
    }

    /** Confronto tra tempo standard e tempo effettivo per intervento del catalogo. */
    public record Confronto(String intervento, long casi, double stimatoMedio, double effettivoMedio) {
        /** Scarto percentuale dell'effettivo rispetto allo stimato (positivo = ci si mette di più). */
        public int scartoPercento() {
            return stimatoMedio == 0 ? 0 : (int) Math.round(100.0 * (effettivoMedio - stimatoMedio) / stimatoMedio);
        }
    }

    public List<Confronto> tempiEffettivi(int giorni) {
        OffsetDateTime dal = OffsetDateTime.now(clock).minusDays(giorni);
        return em.createQuery("""
                select new it.boff.bike.statistiche.StatisticheService$Confronto(
                    r.tipoIntervento.nome, count(r), avg(r.durataMin), avg(r.minutiEffettivi))
                from RigaLavoro r
                where r.minutiEffettivi is not null and r.tipoIntervento is not null and r.lavoro.prontoIl >= :dal
                group by r.tipoIntervento.nome
                order by count(r) desc, r.tipoIntervento.nome
                """, Confronto.class).setParameter("dal", dal).setMaxResults(15).getResultList();
    }

    private long conta(String jpql, OffsetDateTime dal) {
        return em.createQuery(jpql, Long.class).setParameter("dal", dal).getSingleResult();
    }
}
