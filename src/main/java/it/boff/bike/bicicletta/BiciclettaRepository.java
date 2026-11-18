package it.boff.bike.bicicletta;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BiciclettaRepository extends JpaRepository<Bicicletta, Long> {

    List<Bicicletta> findByProprietarioIdAndArchiviataFalseOrderByMarcaAscModelloAsc(Long proprietarioId);

    Optional<Bicicletta> findByIdAndProprietarioIdAndArchiviataFalse(Long id, Long proprietarioId);
}
