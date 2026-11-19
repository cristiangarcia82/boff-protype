package it.boff.bike.calendario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface TurnoVenditaRepository extends JpaRepository<TurnoVendita, Long> {
    @Query("select t from TurnoVendita t join fetch t.utente where t.data between :dal and :al order by t.data, t.oraInizio")
    List<TurnoVendita> tra(LocalDate dal, LocalDate al);
}
