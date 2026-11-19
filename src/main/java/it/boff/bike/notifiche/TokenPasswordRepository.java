package it.boff.bike.notifiche;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface TokenPasswordRepository extends JpaRepository<TokenPassword, Long> {

    Optional<TokenPassword> findByTokenHash(String tokenHash);

    void deleteByUtenteId(Long utenteId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            "update TokenPassword t set t.usatoIl = :quando where t.utente.id = :utenteId and t.usatoIl is null")
    int invalidaTutti(Long utenteId, OffsetDateTime quando);

    long countByUtenteIdAndCreatoIlAfter(Long utenteId, OffsetDateTime dopo);
}
