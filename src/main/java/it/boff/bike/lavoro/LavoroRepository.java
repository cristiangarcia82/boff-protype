package it.boff.bike.lavoro;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LavoroRepository extends JpaRepository<Lavoro, Long> {

    @EntityGraph(attributePaths = {"righe", "bicicletta", "bicicletta.proprietario"})
    @Query("select l from Lavoro l where l.id = :id")
    Optional<Lavoro> dettaglio(@Param("id") Long id);

    @EntityGraph(attributePaths = {"bicicletta", "bicicletta.proprietario"})
    @Query("""
            select l from Lavoro l
            where l.stato in :stati and (:testo = ''
               or lower(concat(l.bicicletta.marca, ' ', l.bicicletta.modello)) like lower(concat('%', :testo, '%'))
               or lower(concat(l.bicicletta.proprietario.nome, ' ', l.bicicletta.proprietario.cognome)) like lower(concat('%', :testo, '%'))
               or lower(concat(l.bicicletta.proprietario.cognome, ' ', l.bicicletta.proprietario.nome)) like lower(concat('%', :testo, '%')))
            order by l.urgente desc, coalesce(l.dataPromessa, l.finePrevista) asc nulls last, l.annunciatoIl asc
            limit 300
            """)
    List<Lavoro> cerca(@Param("stati") Collection<StatoLavoro> stati, @Param("testo") String testo);

    @EntityGraph(attributePaths = {"righe", "bicicletta"})
    @Query("select distinct l from Lavoro l where l.bicicletta.proprietario.id = :clienteId order by l.annunciatoIl desc")
    List<Lavoro> delCliente(@Param("clienteId") Long clienteId);

    @EntityGraph(attributePaths = {"righe"})
    @Query("select distinct l from Lavoro l where l.bicicletta.id = :biciId order by l.annunciatoIl desc")
    List<Lavoro> dellaBici(@Param("biciId") Long biciId);

    @Query("select l from Lavoro l where l.bicicletta.id = :biciId and l.stato not in ('RITIRATO', 'ANNULLATO')")
    Optional<Lavoro> apertoDellaBici(@Param("biciId") Long biciId);

    long countByStatoIn(Collection<StatoLavoro> stati);

    @EntityGraph(attributePaths = {"righe", "bicicletta", "bicicletta.proprietario"})
    @Query("select distinct l from Lavoro l where l.stato in :stati")
    List<Lavoro> conRighe(@Param("stati") Collection<StatoLavoro> stati);
}
