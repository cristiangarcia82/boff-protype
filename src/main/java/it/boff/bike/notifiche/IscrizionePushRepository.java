package it.boff.bike.notifiche;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IscrizionePushRepository extends JpaRepository<IscrizionePush, Long> {

    List<IscrizionePush> findByUtenteId(Long utenteId);

    Optional<IscrizionePush> findByEndpoint(String endpoint);

    void deleteByEndpoint(String endpoint);
}
