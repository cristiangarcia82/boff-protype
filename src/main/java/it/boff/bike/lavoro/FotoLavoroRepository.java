package it.boff.bike.lavoro;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface FotoLavoroRepository extends JpaRepository<FotoLavoro, Long> {

    /** Solo i dati descrittivi (non i byte), per gli elenchi. */
    @Query("select new it.boff.bike.lavoro.FotoInfo(f.id, f.nomeFile, f.tipo, f.dimensione, f.dalCliente) "
            + "from FotoLavoro f where f.lavoro.id = :lavoroId order by f.id")
    List<FotoInfo> elenco(Long lavoroId);

    long countByLavoroId(Long lavoroId);

    @Modifying
    @Query("delete from FotoLavoro f where f.lavoro.id in (select l.id from Lavoro l where l.bicicletta.proprietario.id = :clienteId)")
    int eliminaDelCliente(Long clienteId);
}
