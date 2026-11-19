package it.boff.bike.utente;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UtenteRepository extends JpaRepository<Utente, Long> {

    Optional<Utente> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByTitolareTrue();

    List<Utente> findByRuoloOrderByTitolareDescNomeAsc(Ruolo ruolo);

    List<Utente> findByRuoloAndAttivoTrue(Ruolo ruolo);

    /** Ricerca per nome, cognome, "nome cognome", email o telefono (max 200 risultati). */
    @Query("""
            select u from Utente u
            where u.ruolo = :ruolo and u.eliminatoIl is null and (:testo = ''
               or lower(concat(u.nome, ' ', u.cognome)) like lower(concat('%', :testo, '%'))
               or lower(concat(u.cognome, ' ', u.nome)) like lower(concat('%', :testo, '%'))
               or u.email like lower(concat('%', :testo, '%'))
               or u.telefono like concat('%', :testo, '%'))
            order by u.cognome, u.nome
            limit 200
            """)
    List<Utente> cerca(@Param("ruolo") Ruolo ruolo, @Param("testo") String testo);
}
