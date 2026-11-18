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

/** Browser o app installata su cui un utente riceve le notifiche push. */
@Entity
@Table(name = "iscrizione_push")
public class IscrizionePush {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utente_id", nullable = false)
    private Utente utente;

    @Column(nullable = false, unique = true)
    private String endpoint;

    @Column(nullable = false)
    private String p256dh;

    @Column(nullable = false)
    private String auth;

    protected IscrizionePush() {
    }

    IscrizionePush(Utente utente, String endpoint, String p256dh, String auth) {
        this.utente = utente;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
    }

    void aggiorna(Utente utente, String p256dh, String auth) {
        this.utente = utente;
        this.p256dh = p256dh;
        this.auth = auth;
    }

    public Long getId() { return id; }
    public Utente getUtente() { return utente; }
    public String getEndpoint() { return endpoint; }
    public String getP256dh() { return p256dh; }
    public String getAuth() { return auth; }
}
