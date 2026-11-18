package it.boff.bike.notifiche;

import it.boff.bike.utente.Utente;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "token_password")
public class TokenPassword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utente_id", nullable = false)
    private Utente utente;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "creato_il", nullable = false)
    private OffsetDateTime creatoIl;

    @Column(name = "scade_il", nullable = false)
    private OffsetDateTime scadeIl;

    @Column(name = "usato_il")
    private OffsetDateTime usatoIl;

    protected TokenPassword() {
    }

    TokenPassword(Utente utente, String tokenHash, OffsetDateTime creatoIl, OffsetDateTime scadeIl) {
        this.utente = utente;
        this.tokenHash = tokenHash;
        this.creatoIl = creatoIl;
        this.scadeIl = scadeIl;
    }

    boolean valido(OffsetDateTime adesso) {
        return usatoIl == null && adesso.isBefore(scadeIl);
    }

    void usa(OffsetDateTime adesso) {
        usatoIl = adesso;
    }

    public Utente getUtente() { return utente; }
}
