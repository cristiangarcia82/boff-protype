package it.boff.bike.calendario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface AssenzaRepository extends JpaRepository<Assenza, Long> {

    @Query("select a from Assenza a join fetch a.utente where a.al >= :dal and a.dal <= :al order by a.dal")
    List<Assenza> tra(LocalDate dal, LocalDate al);

    @Query("select count(a) > 0 from Assenza a where a.utente.id = :utenteId and a.al >= :dal and a.dal <= :al")
    boolean siSovrappone(Long utenteId, LocalDate dal, LocalDate al);
}
