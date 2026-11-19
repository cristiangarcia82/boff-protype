package it.boff.bike.appuntamento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface AppuntamentoRepository extends JpaRepository<Appuntamento, Long> {

    @Query("""
            select a from Appuntamento a join fetch a.cliente join fetch a.tipo join fetch a.utente
              left join fetch a.bicicletta
            where a.data between :dal and :al and a.stato in :stati
            order by a.data, a.oraInizio
            """)
    List<Appuntamento> tra(LocalDate dal, LocalDate al, Collection<StatoAppuntamento> stati);

    @Query("""
            select a from Appuntamento a join fetch a.tipo join fetch a.utente left join fetch a.bicicletta
            where a.cliente.id = :clienteId order by a.data desc, a.oraInizio desc
            """)
    List<Appuntamento> delCliente(Long clienteId);

    @Query("""
            select a from Appuntamento a join fetch a.cliente join fetch a.tipo join fetch a.utente
              left join fetch a.bicicletta
            where a.stato = it.boff.bike.appuntamento.StatoAppuntamento.RICHIESTO and a.data >= :oggi
            order by a.data, a.oraInizio
            """)
    List<Appuntamento> daConfermare(LocalDate oggi);

    @Query("""
            select count(a) from Appuntamento a
            where a.cliente.id = :clienteId and a.data >= :oggi and a.stato in :stati
            """)
    long apertiDelCliente(Long clienteId, LocalDate oggi, Collection<StatoAppuntamento> stati);
}
