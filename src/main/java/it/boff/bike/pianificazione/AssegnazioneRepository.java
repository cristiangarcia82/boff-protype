package it.boff.bike.pianificazione;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface AssegnazioneRepository extends JpaRepository<Assegnazione, Long> {

    @Query("""
            select a from Assegnazione a
              join fetch a.utente
              join fetch a.riga r
              join fetch r.lavoro l
              join fetch l.bicicletta b
              join fetch b.proprietario
            where a.data between :dal and :al
            order by a.data, a.oraInizio
            """)
    List<Assegnazione> tra(LocalDate dal, LocalDate al);

    @Query("""
            select a from Assegnazione a join fetch a.utente join fetch a.riga r join fetch r.lavoro
            where r.lavoro.id = :lavoroId order by a.data, a.oraInizio
            """)
    List<Assegnazione> diLavoro(Long lavoroId);

    @Query("""
            select a from Assegnazione a join fetch a.riga r join fetch r.lavoro l
            where a.bloccata = true and a.data >= :oggi and r.completata = false and l.id in :lavori
            """)
    List<Assegnazione> bloccateDa(LocalDate oggi, Collection<Long> lavori);

    /**
     * Toglie dal calendario tutto ciò che va ricalcolato (mantiene le future bloccate dei lavori in officina).
     * Il passato non si tocca mai: resta com'era, per rivedere a posteriori come è andata la settimana.
     */
    @Modifying
    @Query("""
            delete from Assegnazione a where a.id in (
              select x.id from Assegnazione x join x.riga r join r.lavoro l
              where x.data >= :oggi
                and (l.id not in :lavori or x.bloccata = false or r.completata = true))
            """)
    int eliminaDaRicalcolare(Collection<Long> lavori, LocalDate oggi);

    /** Assegnazioni da oggi in poi dei lavori indicati (ricalcolo che conserva il calendario). */
    @Query("""
            select a from Assegnazione a join fetch a.riga r join fetch r.lavoro l
            where a.data >= :oggi and l.id in :lavori
            """)
    List<Assegnazione> daOggi(LocalDate oggi, Collection<Long> lavori);

    /** Come {@link #eliminaDaRicalcolare}, ma conserva le assegnazioni indicate. */
    @Modifying
    @Query("""
            delete from Assegnazione a where a.id in (
              select x.id from Assegnazione x join x.riga r join r.lavoro l
              where x.data >= :oggi and (l.id not in :lavori or x.id not in :conserva))
            """)
    int eliminaTranne(Collection<Long> lavori, LocalDate oggi, Collection<Long> conserva);

    /** Interventi fissati a mano in un periodo (gli appuntamenti non ci si possono sovrapporre). */
    @Query("select a from Assegnazione a join fetch a.utente where a.bloccata = true and a.data between :dal and :al")
    List<Assegnazione> bloccateTra(LocalDate dal, LocalDate al);

    @Query("select a from Assegnazione a where a.utente.id = :utenteId and a.data = :data and a.bloccata = true")
    List<Assegnazione> bloccateDiPersona(Long utenteId, LocalDate data);

    /** Lavori ancora annunciati (bici non arrivata) con interventi fissati da oggi in poi. */
    @Query("""
            select distinct l.id from Assegnazione a join a.riga r join r.lavoro l
            where l.stato = it.boff.bike.lavoro.StatoLavoro.ANNUNCIATO and a.data >= :oggi
            """)
    List<Long> lavoriAnnunciatiFissati(LocalDate oggi);
}
