package it.boff.bike.appuntamento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TipoAppuntamentoRepository extends JpaRepository<TipoAppuntamento, Long> {
    List<TipoAppuntamento> findAllByOrderByOrdineAscNomeAsc();
    List<TipoAppuntamento> findByAttivoTrueOrderByOrdineAscNomeAsc();
    boolean existsByNomeIgnoreCase(String nome);
}
