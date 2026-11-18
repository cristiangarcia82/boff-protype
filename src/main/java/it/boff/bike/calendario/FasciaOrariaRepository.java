package it.boff.bike.calendario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface FasciaOrariaRepository extends JpaRepository<FasciaOraria, Long> {

    @Query("select f from FasciaOraria f where f.utente.id = :utenteId order by f.giornoSettimana, f.oraInizio")
    List<FasciaOraria> diUtente(Long utenteId);

    @Query("select f from FasciaOraria f join fetch f.utente u where u.attivo = true order by f.giornoSettimana, f.oraInizio")
    List<FasciaOraria> diPersonaleAttivo();

    @Modifying
    @Query("delete from FasciaOraria f where f.utente.id = :utenteId")
    void eliminaDiUtente(Long utenteId);
}
