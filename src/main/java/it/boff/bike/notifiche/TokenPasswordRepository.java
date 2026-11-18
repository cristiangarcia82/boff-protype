package it.boff.bike.notifiche;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface TokenPasswordRepository extends JpaRepository<TokenPassword, Long> {

    Optional<TokenPassword> findByTokenHash(String tokenHash);

    long countByUtenteIdAndCreatoIlAfter(Long utenteId, OffsetDateTime dopo);
}
