package it.boff.bike.calendario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ChiusuraRepository extends JpaRepository<Chiusura, Long> {

    List<Chiusura> findByDataBetweenOrderByData(LocalDate dal, LocalDate al);

    boolean existsByData(LocalDate data);
}
