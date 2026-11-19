package it.boff.bike.utente;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Locale;

@Entity
@Table(name = "utente")
public class Utente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String cognome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Ruolo ruolo;

    /** Solo il titolare può modificare la configurazione (utenti, orari, catalogo). */
    @Column(nullable = false)
    private boolean titolare;

    @Column(nullable = false)
    private boolean attivo = true;

    private String telefono;

    @Column(name = "privacy_accettata_il")
    private OffsetDateTime privacyAccettataIl;

    @Column(name = "promemoria_attivi", nullable = false)
    private boolean promemoriaAttivi = true;

    @Column(name = "creato_il", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime creatoIl;

    protected Utente() {
    }

    public Utente(String email, String passwordHash, String nome, String cognome, Ruolo ruolo, boolean titolare) {
        if (titolare && ruolo != Ruolo.STAFF) {
            throw new IllegalArgumentException("Il titolare deve avere ruolo STAFF");
        }
        this.email = normalizzaEmail(email);
        this.passwordHash = passwordHash;
        this.nome = nome;
        this.cognome = cognome;
        this.ruolo = ruolo;
        this.titolare = titolare;
    }

    public static String normalizzaEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getNome() { return nome; }
    public String getCognome() { return cognome; }
    public Ruolo getRuolo() { return ruolo; }
    public boolean isTitolare() { return titolare; }
    public boolean isAttivo() { return attivo; }
    public OffsetDateTime getCreatoIl() { return creatoIl; }

    public String getNomeCompleto() {
        return nome + " " + cognome;
    }

    public String getTelefono() { return telefono; }
    public boolean isPromemoriaAttivi() { return promemoriaAttivi; }
    public void setPromemoriaAttivi(boolean attivi) { this.promemoriaAttivi = attivi; }
    public OffsetDateTime getPrivacyAccettataIl() { return privacyAccettataIl; }

    /**
     * Diritto all'oblio: sostituisce i dati personali con valori anonimi e disattiva l'account.
     * Il record resta per non rompere lo storico dei lavori del negozio.
     */
    public void anonimizza(String passwordHashInutilizzabile) {
        this.email = "eliminato-" + id + "@utente-eliminato.invalid";
        this.nome = "Cliente";
        this.cognome = "eliminato";
        this.telefono = null;
        this.privacyAccettataIl = null;
        this.passwordHash = passwordHashInutilizzabile;
        this.attivo = false;
    }

    public void setAttivo(boolean attivo) { this.attivo = attivo; }
    public void setNome(String nome) { this.nome = nome; }
    public void setCognome(String cognome) { this.cognome = cognome; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public void setPrivacyAccettataIl(OffsetDateTime quando) { this.privacyAccettataIl = quando; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
}
