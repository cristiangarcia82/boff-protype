package it.boff.bike.catalogo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TipoInterventoRepository extends JpaRepository<TipoIntervento, Long> {

    List<TipoIntervento> findAllByOrderByOrdineAscNomeAsc();

    List<TipoIntervento> findByAttivoTrueOrderByOrdineAscNomeAsc();

    boolean existsByNomeIgnoreCase(String nome);

    boolean existsByNomeIgnoreCaseAndIdNot(String nome, Long id);
}
